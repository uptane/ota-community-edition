package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.syntax.option._
import com.advancedtelematic.director.data.AdminDataType.{FindImageCount, RegisterDevice}
import com.advancedtelematic.director.data.ClientDataType.DevicePaginationOps
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.db.{AutoUpdateDefinitionRepositorySupport, DeviceRegistration, DeviceRepository, DeviceRepositorySupport, EcuRepositorySupport, RepoNamespaceRepositorySupport}
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.http.RefinedMarshallingSupport._
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, SignedPayload, TargetName, ValidKeyId}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import slick.jdbc.MySQLProfile.api._
import PaginationParametersDirectives._
import com.advancedtelematic.director.repo.DeviceRoleGeneration
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole

import scala.concurrent.ExecutionContext

class AdminResource(extractNamespace: Directive1[Namespace], val keyserverClient: KeyserverClient)
                   (implicit val db: Database, val ec: ExecutionContext, messageBusPublisher: MessageBusPublisher)
  extends NamespaceRepoId
    with RepoNamespaceRepositorySupport
    with RootFetching
    with EcuRepositorySupport
    with DeviceRepositorySupport
    with AutoUpdateDefinitionRepositorySupport {

  private val EcuIdPath = Segment.flatMap(EcuIdentifier(_).toOption)
  private val KeyIdPath = Segment.flatMap(_.refineTry[ValidKeyId].toOption)
  private val TargetNamePath: PathMatcher1[TargetName] = Segment.map(TargetName.apply)

  val deviceRegistration = new DeviceRegistration(keyserverClient)
  val repositoryCreation = new RepositoryCreation(keyserverClient)
  val deviceRoleGeneration = new DeviceRoleGeneration(keyserverClient)

  def repoRoute(ns: Namespace): Route =
    pathPrefix("repo") {
      (post & pathEnd) {
        val f = repositoryCreation.create(ns).map(_ => StatusCodes.Created)
        complete(f)
      } ~
      (pathPrefix("root") & pathEnd & entity(as[SignedPayload[RootRole]]) & UserRepoId(ns)) { (signedPayload, repoId) =>
        complete {
          keyserverClient.updateRoot(repoId, signedPayload)
        }
      } ~
      get {
        path("root.json") {
          complete(fetchRoot(ns, version = None))
        } ~
        path(IntNumber ~ ".root.json") { version ⇒
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
      }
    }

  def devicePath(ns: Namespace): Route =
    pathPrefix(DeviceId.Path) { device =>
      pathPrefix("ecus") {
        pathPrefix(EcuIdPath) { ecuId =>
          pathPrefix("auto_update") {
            (pathEnd & get) {
              complete(autoUpdateDefinitionRepository.findOnDevice(ns, device, ecuId).map(_.map(_.targetName)))
            } ~
              path(TargetNamePath) { targetName =>
                put {
                  complete(autoUpdateDefinitionRepository.persist(ns, device, ecuId, targetName).map(_ => StatusCodes.NoContent))
                } ~
                delete {
                  complete(autoUpdateDefinitionRepository.remove(ns, device, ecuId, targetName).map(_ => StatusCodes.NoContent))
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
        complete(deviceRoleGeneration.forceTargetsRefresh(ns, device).map(_ => StatusCodes.Accepted))
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
        pathPrefix("devices") {
          UserRepoId(ns) { repoId =>
            concat(
              pathEnd {
                (post & entity(as[RegisterDevice])) { regDev =>
                  if (regDev.deviceId.isEmpty)
                    reject(ValidationRejection("deviceId is required to register a device"))
                  else {
                    complete {
                      deviceRegistration.registerAndPublish(ns, repoId, regDev.deviceId.get, regDev.primary_ecu_serial, regDev.ecus)
                        .map {
                          case DeviceRepository.Created => StatusCodes.Created
                          case _: DeviceRepository.Updated => StatusCodes.OK
                        }
                    }
                  }
                }
              },
              get {
                concat(
                  pathEnd {
                    /** if you leave this parameter (or misspell it) out you'll land in [[LegacyRoutes.route]] */
                    parameter('primaryHardwareId.as[HardwareIdentifier]) { hardwareId =>
                      PaginationParameters { (limit, offset) =>
                        val f = deviceRepository.findDevices(ns, hardwareId, offset, limit).map(_.toClient)
                        complete(f)
                      }
                    }
                  },
                  path("hardware_identifiers") {
                    PaginationParameters { (limit, offset) =>
                      val f = ecuRepository.findAllHardwareIdentifiers(ns, offset, limit)
                      complete(f)
                    }
                  }
                )
              },
              devicePath(ns)
            )
          }
        })
    }
  }
}
