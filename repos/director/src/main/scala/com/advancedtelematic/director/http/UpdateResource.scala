package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directive1, Route}
import cats.implicits.*
import com.advancedtelematic.director.data.AdminDataType.{TargetUpdateRequest, TargetUpdateSpec}
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.{Update, UpdateId}
import com.advancedtelematic.director.db.UpdatesDBIO
import com.advancedtelematic.director.http.PaginationParametersDirectives.PaginationParameters
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode}
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.http.UUIDKeyPekko.UUIDKeyPathOp
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier, EventType}
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeviceUpdateAssigned,
  DeviceUpdateCanceled,
  DeviceUpdateEvent
}
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, TargetFilename}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import scala.concurrent.ExecutionContext

case class CreateDeviceUpdateRequest(targets: Map[HardwareIdentifier, TargetUpdateRequest],
                                     scheduledFor: Option[Instant] = None)

case class CreateUpdateRequest(targets: Map[HardwareIdentifier, TargetUpdateRequest],
                               devices: Seq[DeviceId])

case class CreateUpdateResult(affected: Seq[DeviceId],
                              notAffected: Map[DeviceId, Map[EcuIdentifier, ErrorRepresentation]])

case class UpdateReportedResult(resultCode: ResultCode, success: Boolean, description: Option[String])

case class UpdateResultResponse(hardwareId: HardwareIdentifier,
                                id: TargetFilename,
                                success: Boolean,
                                description: String,
                                result: Option[UpdateReportedResult])

case class UpdateDetailResponse(updateId: UpdateId,
                                status: Update.Status,
                                createdAt: Instant,
                                scheduledFor: Option[Instant],
                                completedAt: Option[Instant],
                                packages: Map[HardwareIdentifier, TargetFilename],
                                deviceResult: Option[UpdateReportedResult],
                                ecuResults: Map[EcuIdentifier, UpdateResultResponse])

case class UpdateResponse(updateId: UpdateId,
                          status: Update.Status,
                          createdAt: Instant,
                          scheduledFor: Option[Instant],
                          completedAt: Option[Instant],
                          deviceResult: Option[UpdateReportedResult],
                          packages: Map[HardwareIdentifier, TargetFilename])

case class UpdateEventResponse(deviceId: DeviceId,
                               eventType: EventType,
                               deviceTime: Instant,
                               receivedAt: Instant,
                               success: Option[Boolean],
                               ecu: Option[EcuIdentifier])

class UpdateResource(extractNamespace: Directive1[Namespace])(
  implicit val db: Database,
  val ec: ExecutionContext,
  val messageBusPublisher: MessageBusPublisher) {

  import org.apache.pekko.http.scaladsl.server.Directives.*

  private val updates = new UpdatesDBIO()

  private def createDeviceUpdate(ns: Namespace,
                                 targets: Map[HardwareIdentifier, TargetUpdateRequest],
                                 deviceId: DeviceId,
                                 scheduledFor: Option[Instant]): Route = complete(for {
    updateId <- updates.createFor(ns, deviceId, TargetUpdateSpec(targets), scheduledFor)
    _ <- messageBusPublisher.publishSafe(
      DeviceUpdateAssigned(
        ns,
        Instant.now(),
        updateId.toCorrelationId,
        deviceId,
        scheduledFor
      ): DeviceUpdateEvent
    )
  } yield updateId)

  val route = extractNamespace { ns =>
    concat(
      path("updates") {
        (get & pathEnd & PaginationParameters) { case (offset, limit) =>
          complete(updates.findAll(ns, offset, limit))
        }
      },
      path("updates" / UpdateId.Path) { updateId =>
        concat(
          patch {
            val f = updates
              .cancelAll(ns, updateId)
              .flatMap { ids =>
                ids.map { case (id, deviceId) =>
                  messageBusPublisher.publishSafe(
                    DeviceUpdateCanceled(ns, Instant.now(), id, deviceId): DeviceUpdateEvent
                  )
                }.sequence_
              }
            complete(f.map(_ => StatusCodes.NoContent))
          },
          path("devices") {
            (get & PaginationParameters) { case (offset, limit) =>
              complete(updates.findUpdateDevices(ns, updateId, offset, limit))
            }
          }
        )
      },
      pathPrefix("updates" / "devices") {
        path(DeviceId.Path) { deviceId =>
          concat(
            (get & pathEnd & PaginationParameters) { (offset, limit) =>
              val f =
                updates.findFor(ns, deviceId, offset, limit)
              complete(f)
            },
            (post & pathEnd) {
              entity(as[CreateDeviceUpdateRequest]) { req =>
                createDeviceUpdate(ns, req.targets, deviceId, req.scheduledFor)
              }
            }
          )
        } ~
          pathPrefix(DeviceId.Path / UpdateId.Path) { (deviceId, updateId) =>
            pathEnd {
              concat(
                (patch & optionalHeaderValueByType(ForceHeader)) { force =>
                  val f = updates
                    .cancel(ns, updateId, deviceId, force.exists(_.asBoolean))
                    .flatMap { id =>
                      messageBusPublisher.publishSafe(
                        DeviceUpdateCanceled(ns, Instant.now(), id, deviceId): DeviceUpdateEvent
                      )
                    }
                  complete(f.map(_ => StatusCodes.NoContent))
                },
                get {
                  val f = updates.find(ns, updateId, deviceId)
                  complete(f)
                }
              )
            } ~
              path("events") {
                val f = updates.findEvents(ns, deviceId, updateId)
                complete(f)
              }
          } ~
          (post & pathEnd) {
            entity(as[CreateUpdateRequest]) { req =>
              val f =
                updates
                  .createMany(ns, TargetUpdateSpec(req.targets), req.devices)
                  .flatMap { case (updateId, createResult) =>
                    createResult.affected.toList
                      .traverse_ { deviceId =>
                        messageBusPublisher.publishSafe(
                          DeviceUpdateAssigned(
                            ns,
                            Instant.now(),
                            updateId.toCorrelationId,
                            deviceId
                          ): DeviceUpdateEvent
                        )
                      }
                      .map(_ => createResult)
                  }

              complete(f)
            }
          }
      }
    )
  }

}
