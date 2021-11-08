package com.advancedtelematic.ota.deviceregistry.daemon

import java.time.Instant
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.deviceUpdateCompletedCodec
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateAssigned, DeviceUpdateCanceled, DeviceUpdateCompleted, DeviceUpdateEvent, DeviceUpdateInFlight}
import com.advancedtelematic.ota.deviceregistry.common.Errors
import com.advancedtelematic.ota.deviceregistry.daemon.DeviceUpdateStatus._
import com.advancedtelematic.ota.deviceregistry.data.DeviceStatus
import com.advancedtelematic.ota.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.ota.deviceregistry.db.{DeviceRepository, InstallationReportRepository}
import io.circe.syntax._
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}


  final case class DeviceUpdateStatus(namespace: Namespace,
                                      device: DeviceId,
                                      status: DeviceStatus,
                                      timestamp: Instant = Instant.now())

  object DeviceUpdateStatus {
    import cats.syntax.show._
    import com.advancedtelematic.libats.codecs.CirceCodecs._
    implicit val MessageLikeInstance = MessageLike.derive[DeviceUpdateStatus](_.device.show)
  }

class DeviceUpdateEventListener(messageBus: MessageBusPublisher)
                               (implicit val db: Database, ec: ExecutionContext) extends MsgOperation[DeviceUpdateEvent] {

  private val _log = LoggerFactory.getLogger(this.getClass)
  case class Unhandleable(name: String, deviceUuid: DeviceId, correlationId: CorrelationId) extends Exception()

  override def apply(event: DeviceUpdateEvent): Future[Unit] = {
    wasCompleted(event.deviceUuid, event.correlationId).flatMap {
      case true =>
        _log.warn(s"Received $event but a DeviceUpdateComplete event for ${event.correlationId} was already received, ignoring event")
        FastFuture.successful(())
      case false =>
        handleEvent(event)
          .flatMap {
            setDeviceStatus(event.deviceUuid, _)
          }
          .recoverWith {
            case Errors.MissingDevice =>
              _log.warn(s"Device ${event.deviceUuid} does not exist ($event)")
              FastFuture.successful(())
            case Unhandleable(name, deviceUuid, correlationId) =>
              _log.error(s"Got message '$name' from device $deviceUuid, correlationId $correlationId and don't know how to handle it")
              FastFuture.successful(())
          }
    }
  }

  private def wasCompleted(deviceId: DeviceId, correlationId: CorrelationId): Future[Boolean] = {
    val existingReport = db.run(InstallationReportRepository.fetchDeviceInstallationResultFor(deviceId, correlationId))
    existingReport.map(_.exists(_.success)) // if we handle success event - other should be ignored
  }

  private def handleEvent(event: DeviceUpdateEvent): Future[DeviceStatus] = event match {
    case   _: DeviceUpdateAssigned  => FastFuture.successful(DeviceStatus.Outdated)
    case   _: DeviceUpdateCanceled  => FastFuture.successful(DeviceStatus.UpToDate)
    case   _: DeviceUpdateInFlight => FastFuture.successful(DeviceStatus.UpdatePending)
    case msg: DeviceUpdateCompleted =>
      db.run {
        InstallationReportRepository
        .saveInstallationResults(
          msg.correlationId, msg.deviceUuid, msg.result.code, msg.result.success, msg.ecuReports,
          msg.eventTime, msg.asJson)
        .map(_ => if (msg.result.success) DeviceStatus.UpToDate else DeviceStatus.Error)
    }
    case msg: DeviceUpdateEvent =>
      FastFuture.failed(Unhandleable(msg.getClass.getSimpleName, msg.deviceUuid, msg.correlationId))
  }

  def setDeviceStatus(deviceUuid: DeviceId, deviceStatus: DeviceStatus) = {
    val f = for {
      device <- DeviceRepository.findByUuid(deviceUuid)
      _ <- DeviceRepository.setDeviceStatus(
        deviceUuid,
        if(device.lastSeen.isEmpty) DeviceStatus.NotSeen else deviceStatus)
    } yield (device, deviceStatus)

    db.run(f)
      .flatMap {
      case (device, status) =>
        messageBus
          .publish(DeviceUpdateStatus(device.namespace, device.uuid, status, Instant.now()))
    }.map(_ => ())
  }

}
