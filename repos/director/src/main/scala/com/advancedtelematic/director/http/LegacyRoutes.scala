package com.advancedtelematic.director.http

import java.time.Instant
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{Directive1, Route}
import com.advancedtelematic.director.data.DataType.TargetSpecId
import com.advancedtelematic.director.db.{EcuRepositorySupport, ProvisionedDeviceRepositorySupport}
import com.advancedtelematic.director.http.PaginationParametersDirectives.*
import com.advancedtelematic.libats.data.DataType.{MultiTargetUpdateCorrelationId, Namespace}
import com.advancedtelematic.libats.http.UUIDKeyPekko.*
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateAssigned, DeviceUpdateEvent}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

// Implements routes provided by old director that ota-web-app still uses
class LegacyRoutes(extractNamespace: Directive1[Namespace])(
  implicit val db: Database,
  val ec: ExecutionContext,
  messageBusPublisher: MessageBusPublisher)
    extends EcuRepositorySupport
    with ProvisionedDeviceRepositorySupport {

  private val deviceAssignments = new DeviceAssignments()

  // TODO: Remove this, and its endpoint, no longer used
  private def createDeviceAssignment(ns: Namespace,
                                     deviceId: DeviceId,
                                     targetSpecId: TargetSpecId): Future[Unit] = {
    val correlationId = MultiTargetUpdateCorrelationId(targetSpecId.uuid)
    val assignment = deviceAssignments.createForDevice(ns, correlationId, deviceId, targetSpecId)

    assignment.map { d =>
      val msg: DeviceUpdateEvent = DeviceUpdateAssigned(ns, Instant.now(), correlationId, d)
      messageBusPublisher.publishSafe(msg)
    }
  }

  val route: Route =
    extractNamespace { ns =>
      concat(
        path("admin" / "devices" / DeviceId.Path / "multi_target_update" / TargetSpecId.Path) {
          (deviceId, TargetSpecId) =>
            put {
              val f = createDeviceAssignment(ns, deviceId, TargetSpecId).map(_ => StatusCodes.OK)
              complete(f)
            }
        },
        path("assignments" / DeviceId.Path) { deviceId =>
          delete {
            val a = deviceAssignments.cancel(ns, List(deviceId))
            complete(a.map(_.map(_.deviceId)))
          }
        },
        (path("admin" / "devices") & PaginationParameters) { (offset, limit) =>
          get {
            complete(provisionedDeviceRepository.findAllDeviceIds(ns, offset, limit))
          }
        }
      )
    }

}
