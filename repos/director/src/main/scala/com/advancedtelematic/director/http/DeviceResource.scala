package com.advancedtelematic.director.http

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive, Directive0, Directive1, Route}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.advancedtelematic.director.data.AdminDataType.RegisterDevice
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.Messages.{DeviceManifestReported, _}
import com.advancedtelematic.director.db._
import com.advancedtelematic.director.manifest.{DeviceManifestProcess, ManifestCompiler}
import com.advancedtelematic.director.repo.DeviceRoleGeneration
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceSeen, DeviceUpdateEvent}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{SnapshotRole, TimestampRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{RoleType, SignedPayload}
import com.advancedtelematic.libtuf_server.data.Marshalling.JsonRoleTypeMetaPath
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import slick.jdbc.MySQLProfile.api._

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}

class DeviceResource(extractNamespace: Directive1[Namespace], val keyserverClient: KeyserverClient, val ecuReplacementAllowed: Boolean)
                    (implicit val db: Database, val ec: ExecutionContext, messageBusPublisher: MessageBusPublisher)
  extends DeviceRepositorySupport
    with RepoNamespaceRepositorySupport
    with DbSignedRoleRepositorySupport
    with NamespaceRepoId
    with RootFetching {

  import akka.http.scaladsl.server.Directives._

  val deviceRegistration = new DeviceRegistration(keyserverClient)
  val deviceManifestProcess = new DeviceManifestProcess()
  val deviceRoleGeneration = new DeviceRoleGeneration(keyserverClient)

  def deviceRegisterAllowed(deviceId: DeviceId): Directive0 = {
    if (ecuReplacementAllowed) {
      pass
    } else {
      Directive.Empty.tflatMap { _ =>
        onSuccess(deviceRepository.exists(deviceId)).flatMap {
          case true => failWith(Errors.EcuReplacementDisabled(deviceId))
          case false => pass
        }
      }
    }
  }

  private def logDevice(namespace: Namespace, device: DeviceId): Directive0 = {
    val f = messageBusPublisher.publishSafe(DeviceSeen(namespace, device))
    onComplete(f).flatMap(_ => pass)
  }

  val route = extractNamespaceRepoId(extractNamespace){ (repoId, ns) =>
    pathPrefix("device" / DeviceId.Path) { device =>
      post {
        (path("ecus") & entity(as[RegisterDevice]) & deviceRegisterAllowed(device)) { regDev =>
          complete {
            deviceRegistration.registerAndPublish(ns, repoId, device, regDev.primary_ecu_serial, regDev.ecus)
              .map {
                case DeviceRepository.Created => StatusCodes.Created
                case _: DeviceRepository.Updated => StatusCodes.OK
              }
          }
        }
      } ~
      put {
        path("manifest") {
          entity(as[SignedPayload[Json]]) { jsonDevMan =>
            val msgF = messageBusPublisher.publishSafe(DeviceManifestReported(ns, device, jsonDevMan, Instant.now()))

            onComplete(msgF) { _ =>
              handleDeviceManifest(ns, device, jsonDevMan)
            }
          }
        }
       } ~
      get {
        path(IntNumber ~ ".root.json") { version =>
          logDevice(ns, device) {
            complete(fetchRoot(ns, version.some))
          }
        } ~
          path(JsonRoleTypeMetaPath) {
            case RoleType.ROOT =>
              logDevice(ns, device) {
                complete(fetchRoot(ns, version = None))
              }
            case RoleType.TARGETS =>
              val f = deviceRoleGeneration.findFreshTargets(ns, repoId, device)
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

  private def handleDeviceManifest(ns: Namespace, device: DeviceId, jsonDevMan: SignedPayload[Json]): Route = {
    onSuccess(deviceManifestProcess.validateManifestSignatures(ns, device, jsonDevMan)) {
      case Valid(deviceManifest) =>
        val validatedManifest = ManifestCompiler(ns, deviceManifest)

        val executor = new CompiledManifestExecutor()

        val f = async {
          val execResult = await(executor.process(device, validatedManifest))

          await(Future.traverse(execResult.messages)(messageBusPublisher.publishSafe[DeviceUpdateEvent]))

          StatusCodes.OK
        }

        complete(f)
      case Invalid(e) =>
        failWith(Errors.Manifest.SignatureNotValid(e.toList.mkString(", ")))
    }
  }
}
