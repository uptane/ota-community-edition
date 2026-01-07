package com.advancedtelematic.director.http

import java.time.Instant
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directive, Directive0, Directive1, Route}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.*
import com.advancedtelematic.director.data.AdminDataType.RegisterDevice
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.AdminRoleName.AdminRoleNamePathMatcher
import com.advancedtelematic.director.data.DbDataType.Assignment
import com.advancedtelematic.director.data.Messages.{DeviceManifestReported, *}
import com.advancedtelematic.director.db.*
import com.advancedtelematic.director.manifest.{DeviceManifestProcess, ManifestCompiler}
import com.advancedtelematic.director.repo.{DeviceRoleGeneration, OfflineUpdates, RemoteSessions}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorRepresentation.*
import com.advancedtelematic.libats.http.UUIDKeyPekko.*
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeviceUpdateEvent,
  DeviceUpdateInFlight
}
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{SnapshotRole, TimestampRole}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.{RoleType, SignedPayload}
import com.advancedtelematic.libtuf_server.data.Marshalling.JsonRoleTypeMetaPath
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.Json
import slick.jdbc.MySQLProfile.api.*

import scala.async.Async.*
import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libtuf_server.data.Marshalling.jsonSignedPayloadMarshaller
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient.RootRoleNotFound

import scala.util.{Failure, Success}

object DeviceResource {}

class DeviceResource(extractNamespace: Directive1[Namespace],
                     val keyserverClient: KeyserverClient,
                     val ecuReplacementAllowed: Boolean)(
  implicit val db: Database,
  val ec: ExecutionContext,
  messageBusPublisher: MessageBusPublisher)
    extends ProvisionedDeviceRepositorySupport
    with EcuRepositorySupport
    with RepoNamespaceRepositorySupport
    with DbDeviceRoleRepositorySupport
    with NamespaceRepoId
    with AdminRolesRepositorySupport
    with RootFetching {

  import org.apache.pekko.http.scaladsl.server.Directives.*

  val deviceRegistration = new DeviceRegistration(keyserverClient)
  val deviceManifestProcess = new DeviceManifestProcess()
  val deviceRoleGeneration = new DeviceRoleGeneration(keyserverClient)
  val offlineUpdates = new OfflineUpdates(keyserverClient)
  val remoteSessions = new RemoteSessions(keyserverClient)

  def deviceRegisterAllowed(deviceId: DeviceId): Directive0 =
    if (ecuReplacementAllowed)
      pass
    else {
      Directive.Empty.tflatMap { _ =>
        onSuccess(provisionedDeviceRepository.exists(deviceId)).flatMap {
          case true  => failWith(Errors.EcuReplacementDisabled(deviceId))
          case false => pass
        }
      }
    }

  private def publishInFlight(assignments: Seq[Assignment]): Future[Unit] = {
    val now = Instant.now
    val msgs = assignments.map { a =>
      DeviceUpdateInFlight(a.ns, now, a.correlationId, a.deviceId).asInstanceOf[DeviceUpdateEvent]
    }
    Future.traverse(msgs)(msg => messageBusPublisher.publishSafe(msg)).map(_ => ())
  }

  private def logDevice(ns: Namespace, deviceId: DeviceId): Directive0 = {
    val f = LogDeviceSeen.logDevice(ns, deviceId)

    (onComplete(f) & extractLog).tflatMap {
      case (Failure(ex), log) =>
        log.warning(ex, "could not update device seen status")
        pass
      case _ =>
        pass
    }
  }

  val route = extractNamespaceRepoId(extractNamespace) { (repoId, ns) =>
    pathPrefix("device" / DeviceId.Path) { device =>
      post {
        (path("ecus") & entity(as[RegisterDevice]) & deviceRegisterAllowed(device)) { regDev =>
          complete {
            deviceRegistration
              .register(ns, repoId, device, regDev.primary_ecu_serial, regDev.ecus)
              .map {
                case ProvisionedDeviceRepository.Created    => StatusCodes.Created
                case _: ProvisionedDeviceRepository.Updated => StatusCodes.OK
              }
          }
        }
      } ~
        put {
          path("manifest") {
            entity(as[SignedPayload[Json]]) { jsonDevMan =>
              val msgF = messageBusPublisher.publishSafe(
                DeviceManifestReported(ns, device, jsonDevMan, Instant.now())
              )

              onComplete(msgF) { _ =>
                handleDeviceManifest(ns, device, jsonDevMan)
              }
            }
          }
        } ~
        get {
          path(IntNumber ~ ".root.json") { version =>
            logDevice(ns, device) {
              onComplete(fetchRoot(ns, version.some)) {
                case Success(root)             => complete(root)
                case Failure(RootRoleNotFound) =>
                  complete(RootRoleNotFound.responseCode -> RootRoleNotFound.toErrorRepr)
                case Failure(ex) => failWith(ex)
              }
            }
          } ~
            path("offline-updates" / AdminRoleNamePathMatcher ~ ".json") { offlineTargetName =>
              get {
                val f = offlineUpdates.findLatestUpdates(repoId, offlineTargetName)
                complete(f)
              }
            } ~
            path("offline-snapshot.json") {
              get {
                val f = offlineUpdates.findLatestSnapshot(repoId)
                complete(f)
              }
            } ~
            path("remote-sessions.json") {
              get {
                val f = remoteSessions.find(repoId)
                complete(f)
              }
            } ~
            path(JsonRoleTypeMetaPath) {
              case RoleType.ROOT =>
                logDevice(ns, device) {
                  complete(fetchRoot(ns, version = None))
                }
              case RoleType.TARGETS =>
                val f = for {
                  (json, assignments) <- deviceRoleGeneration.findFreshTargets(ns, repoId, device)
                  _ <- publishInFlight(assignments)
                } yield json

                complete(f)

              case RoleType.SNAPSHOT =>
                val f = deviceRoleGeneration.findFreshDeviceRole[SnapshotRole](ns, repoId, device)
                complete(f)
              case RoleType.TIMESTAMP =>
                val f = deviceRoleGeneration.findFreshDeviceRole[TimestampRole](ns, repoId, device)
                complete(f)
            }
        }
    }
  }

  private def handleDeviceManifest(ns: Namespace,
                                   device: DeviceId,
                                   jsonDevMan: SignedPayload[Json]): Route =
    onSuccess(deviceManifestProcess.validateManifestSignatures(ns, device, jsonDevMan)) {
      case Valid(deviceManifest) =>
        val validatedManifest = ManifestCompiler(ns, deviceManifest)

        val executor = new CompiledManifestExecutor()

        val f = async {
          val execResult = await(executor.process(device, validatedManifest))

          await(
            Future.traverse(execResult.messages)(messageBusPublisher.publishSafe[DeviceUpdateEvent])
          )

          StatusCodes.OK
        }

        complete(f)
      case Invalid(e) =>
        failWith(Errors.Manifest.SignatureNotValid(e.toList.mkString(", ")))
    }

}
