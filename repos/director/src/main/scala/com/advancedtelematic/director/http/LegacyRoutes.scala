package com.advancedtelematic.director.http

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.advancedtelematic.director.db.{DeviceRepositorySupport, EcuRepositorySupport}
import com.advancedtelematic.director.http.PaginationParametersDirectives._
import com.advancedtelematic.libats.data.DataType.{MultiTargetUpdateId, Namespace}
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateAssigned, DeviceUpdateEvent}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

// Implements routes provided by old director that ota-web-app still uses
class LegacyRoutes(extractNamespace: Directive1[Namespace])(implicit val db: Database, val ec: ExecutionContext, messageBusPublisher: MessageBusPublisher)
  extends EcuRepositorySupport with DeviceRepositorySupport {

  private val deviceAssignments = new DeviceAssignments()

  private def createDeviceAssignment(ns: Namespace, deviceId: DeviceId, mtuId: UpdateId): Future[Unit] = {
    val correlationId = MultiTargetUpdateId(mtuId.uuid)
    val assignment = deviceAssignments.createForDevice(ns, correlationId, deviceId, mtuId)

    assignment.map { a =>
      val msg: DeviceUpdateEvent = DeviceUpdateAssigned(ns, Instant.now(), correlationId, a.deviceId)
      messageBusPublisher.publishSafe(msg)
    }
  }

  val route: Route =
    extractNamespace { ns =>
      concat(
        path("admin" / "devices" / DeviceId.Path / "multi_target_update" / UpdateId.Path) { (deviceId, updateId) =>
          put {
            val f = createDeviceAssignment(ns, deviceId, updateId).map(_ => StatusCodes.OK)
            complete(f)
          }
        },
        path("assignments" / DeviceId.Path) { deviceId =>
          delete {
            val a = deviceAssignments.cancel(ns, List(deviceId))
            complete(a.map(_.map(_.deviceId)))
          }
        },
        (path("admin" / "devices") & PaginationParameters) { (limit, offset) =>
          get {
            complete(deviceRepository.findAllDeviceIds(ns, offset, limit))
          }
        }
      )
    }
}
