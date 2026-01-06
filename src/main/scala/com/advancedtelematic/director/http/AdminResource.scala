package com.advancedtelematic.director.http

import com.advancedtelematic.libats.data.PaginationResult.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import cats.syntax.option.*
import com.advancedtelematic.director.data.AdminDataType.{FindImageCount, RegisterDevice}
import com.advancedtelematic.director.data.ClientDataType.*
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.AdminRoleName.AdminRoleNamePathMatcher
import com.advancedtelematic.director.db.*
import com.advancedtelematic.director.http.PaginationParametersDirectives.*
import com.advancedtelematic.director.repo.{DeviceRoleGeneration, OfflineUpdates, RemoteSessions}
import com.advancedtelematic.libats.codecs.CirceCodecs.*
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libats.http.RefinedMarshallingSupport.*
import com.advancedtelematic.libats.http.UUIDKeyPekko.*
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, ValidEcuIdentifier}
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  RemoteCommandsPayload,
  RemoteSessionsPayload,
  RootRole
}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  JsonSignedPayload,
  SignedPayload,
  TargetFilename,
  TargetName,
  ValidKeyId
}
import com.advancedtelematic.libtuf_server.data.Marshalling.jsonSignedPayloadMarshaller
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

case class OfflineUpdateRequest(values: Map[TargetFilename, ClientTargetItem],
                                expiresAt: Option[Instant])

case class RemoteSessionRequest(remoteSessions: RemoteSessionsPayload)

case class RemoteCommandRequest(remoteCommands: RemoteCommandsPayload)

class AdminResource(extractNamespace: Directive1[Namespace], val keyserverClient: KeyserverClient)(
  implicit val db: Database,
  msgBus: MessageBusPublisher,
  val ec: ExecutionContext)
    extends NamespaceRepoId
    with RepoNamespaceRepositorySupport
    with AdminRolesRepositorySupport
    with RootFetching
    with EcuRepositorySupport
    with ProvisionedDeviceRepositorySupport
    with AutoUpdateDefinitionRepositorySupport
    with UpdatesRepositorySupport {

  private val EcuIdPath = Segment.flatMap(_.refineTry[ValidEcuIdentifier].toOption)
  private val KeyIdPath = Segment.flatMap(_.refineTry[ValidKeyId].toOption)
  private val TargetNamePath: PathMatcher1[TargetName] = Segment.map(TargetName.apply)

  val deviceRegistration = new DeviceRegistration(keyserverClient)
  val repositoryCreation = new RepositoryCreation(keyserverClient)
  val deviceRoleGeneration = new DeviceRoleGeneration(keyserverClient)
  val offlineUpdates = new OfflineUpdates(keyserverClient)
  val remoteSessions = new RemoteSessions(keyserverClient)

  private def findDevicesCurrentTarget(ns: Namespace,
                                       devices: Seq[DeviceId]): Future[DevicesCurrentTarget] = {
    val values =
      for {
        d <- ecuRepository.deviceEcuInstallInfo(ns, devices.toSet)
      } yield d
        .map { case (deviceId, ecu, isPrimary, installedTarget) =>
          installedTarget match {
            case Some(t) =>
              deviceId -> Seq[EcuTarget](
                EcuTarget(ecu.ecuSerial, t.checksum, t.filename, ecu.hardwareId, isPrimary)
              )
            case None => deviceId -> Seq.empty[EcuTarget]
          }
        }
        .groupMap(_._1)(_._2)
        .map { case (deviceId, ecuTargets) => deviceId -> ecuTargets.flatten }
    values.map { v =>
      DevicesCurrentTarget {
        // Only necessary if lengths differ. This can happen when db has no activeEcus for a specified deviceId
        if (v.size != devices.length) {
          devices.map(d => d -> v.getOrElse(d, Seq.empty[EcuTarget])).toMap
        } else v
      }
    }
  }

  def repoRoute(ns: Namespace): Route =
    pathPrefix("repo") {
      (post & pathEnd) {
        val f = repositoryCreation.create(ns).map(_ => StatusCodes.Created)
        complete(f)
      } ~
        (pathPrefix("root") & pathEnd & entity(as[SignedPayload[RootRole]]) & UserRepoId(ns)) {
          (signedPayload, repoId) =>
            complete {
              keyserverClient.updateRoot(repoId, signedPayload)
            }
        } ~
        get {
          path("root.json") {
            complete(fetchRoot(ns, version = None))
          } ~
            path(IntNumber ~ ".root.json") { version =>
              complete(fetchRoot(ns, version.some))
            }
        } ~
        path("private_keys" / KeyIdPath) { keyId =>
          UserRepoId(ns) { repoId =>
            delete {
              complete {
                keyserverClient.deletePrivateKey(repoId, keyId)
              }
            } ~
              get {
                complete {
                  keyserverClient.fetchKeyPair(repoId, keyId)
                }
              }
          }
        } ~
        (path("offline-updates" / AdminRoleNamePathMatcher) & UserRepoId(ns)) {
          (offlineTargetName, repoId) =>
            (post & entity(as[OfflineUpdateRequest])) { req =>
              val f = offlineUpdates.set(repoId, offlineTargetName, req.values, req.expiresAt)
              complete(f.map(_.content))
            } ~
              delete {
                val f = offlineUpdates.delete(repoId, offlineTargetName)
                complete(f)
              }
        } ~
        (path("offline-updates" / AdminRoleNamePathMatcher ~ ".json") & UserRepoId(ns)) {
          (offlineTargetName, repoId) =>
            get {
              val f = offlineUpdates.findLatestUpdates(repoId, offlineTargetName)
              complete(f)
            }
        } ~
        (path(
          "offline-updates" / IntNumber ~ "." ~ AdminRoleNamePathMatcher ~ ".json"
        ) & UserRepoId(ns)) { (version, offlineTargetName, repoId) =>
          get {
            val f = offlineUpdates.findUpdates(repoId, offlineTargetName, version)
            complete(f)
          }
        } ~
        (path("offline-snapshot.json") & UserRepoId(ns)) { repoId =>
          get {
            val f = offlineUpdates.findLatestSnapshot(repoId)
            complete(f)
          }
        } ~
        (path(IntNumber ~ ".offline-snapshot.json") & UserRepoId(ns)) { (version, repoId) =>
          get {
            val f = offlineUpdates.findSnapshot(repoId, version)
            complete(f)
          }
        }
    }

  def devicePath(ns: Namespace): Route =
    pathPrefix(DeviceId.Path) { device =>
      pathPrefix("ecus") {
        pathPrefix(EcuIdPath) { ecuId =>
          pathPrefix("auto_update") {
            (pathEnd & get) {
              complete(
                autoUpdateDefinitionRepository
                  .findOnDevice(ns, device, ecuId)
                  .map(_.map(_.targetName))
              )
            } ~
              path(TargetNamePath) { targetName =>
                put {
                  complete(
                    autoUpdateDefinitionRepository
                      .persist(ns, device, ecuId, targetName)
                      .map(_ => StatusCodes.NoContent)
                  )
                } ~
                  delete {
                    complete(
                      autoUpdateDefinitionRepository
                        .remove(ns, device, ecuId, targetName)
                        .map(_ => StatusCodes.NoContent)
                    )
                  }
              }
          } ~
            (path("public_key") & get) {
              val key = ecuRepository.findBySerial(ns, device, ecuId).map(_.publicKey)
              complete(key)
            }
        }
      } ~
        get {
          val f = deviceRegistration.findDeviceEcuInfo(ns, device)
          complete(f)
        } ~
        (path("targets.json") & put) {
          complete(deviceRoleGeneration.forceTargetsRefresh(device).map(_ => StatusCodes.Accepted))
        }
    }

  val route: Route = extractNamespace { ns =>
    pathPrefix("admin") {
      concat(
        repoRoute(ns),
        pathPrefix("images") {
          (post & path("installed_count")) { // this is post because front-end can't send
            entity(as[FindImageCount]) { findImageReq =>
              val f = ecuRepository.countEcusWithImages(ns, findImageReq.filepaths.toSet)
              complete(f)
            }
          }
        },
        (path("remote-commands") & UserRepoId(ns)) { repoId =>
          (post & entity(as[RemoteCommandRequest])) { req =>
            val f = remoteSessions
              .setRemoteCommands(repoId, req.remoteCommands)
              .map(_ => StatusCodes.Accepted)
            complete(f)
          }
        } ~
          (path("remote-sessions") & UserRepoId(ns)) { repoId =>
            (post & entity(as[RemoteSessionRequest])) { req =>
              val f = remoteSessions.setRemoteSessions(repoId, req.remoteSessions)
              complete(f.map(_.content))
            }
          },
        (path("remote-sessions.json") & UserRepoId(ns)) { repoId =>
          get {
            val f = remoteSessions.find(repoId)
            complete(f)
          } ~
            post {
              entity(as[JsonSignedPayload]) { req =>
                complete(remoteSessions.updateFullRole(repoId, req).map(_ => StatusCodes.OK))
              }
            }
        },
        pathPrefix("devices") {
          UserRepoId(ns) { repoId =>
            concat(
              pathEnd {
                (post & entity(as[RegisterDevice])) { regDev =>
                  if (regDev.deviceId.isEmpty)
                    reject(ValidationRejection("deviceId is required to register a device"))
                  else {
                    complete {
                      deviceRegistration
                        .register(
                          ns,
                          repoId,
                          regDev.deviceId.get,
                          regDev.primary_ecu_serial,
                          regDev.ecus
                        )
                        .map {
                          case ProvisionedDeviceRepository.Created    => StatusCodes.Created
                          case _: ProvisionedDeviceRepository.Updated => StatusCodes.OK
                        }
                    }
                  }
                }
              },
              (post & path("list-installed-targets")) {
                entity(as[Seq[DeviceId]]) { devices =>
                  complete(findDevicesCurrentTarget(ns, devices))
                }
              },
              get {
                concat(
                  pathEnd {

                    /** if you leave this parameter (or misspell it) out you'll land in
                      * [[LegacyRoutes.route]]
                      */
                    parameter(Symbol("primaryHardwareId").as[HardwareIdentifier]) { hardwareId =>
                      PaginationParameters { (offset, limit) =>
                        val f = provisionedDeviceRepository
                          .findDevices(ns, hardwareId, offset, limit)
                          .map(_.toClient)
                        complete(f)
                      }
                    }
                  },
                  path("hardware_identifiers") {
                    PaginationParameters { (offset, limit) =>
                      val f = ecuRepository. findAllHardwareIdentifiers(ns, offset, limit)
                      complete(f)
                    }
                  },
                  path("ecus") {
                    PaginationParameters { (offset, limit) =>
                      val pagedEcus = ecuRepository.findAll(ns).map { ecuTuples =>
                        val uniqueDeviceIds = ecuTuples.map(_._1.deviceId).toSet
                        val s = uniqueDeviceIds.map { deviceId =>
                          // group ecus which belong to the same device
                          val deviceEcus = ecuTuples
                            .filter(_._1.deviceId == deviceId)
                            .map { case (ecu, primary) => ecu.toClient(primary) }
                          Map(deviceId -> deviceEcus)
                        }
                        val pageLimit = if (s.size < (offset * limit + limit).toInt) {
                          s.size
                        } else {
                          (offset * limit + limit).toInt
                        }
                        val page = s.slice((offset * limit).toInt, (offset * limit + limit).toInt)
                        PaginationResult(page.toSeq, pageLimit, offset, limit)
                      }
                      complete(pagedEcus)
                    }
                  }
                )
              },
              devicePath(ns)
            )
          }
        }
      )
    }
  }

}
