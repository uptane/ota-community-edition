package com.advancedtelematic.campaigner.daemon

import akka.http.scaladsl.util.FastFuture
import cats.syntax.show._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.Campaigns
import com.advancedtelematic.campaigner.http.Errors
import com.advancedtelematic.libats.data.DataType.{CampaignId => CampaignCorrelationId}
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateCanceled, DeviceUpdateCompleted, DeviceUpdateEvent}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class DeviceUpdateEventListener(campaigns: Campaigns)(implicit ec: ExecutionContext)
  extends MsgOperation[DeviceUpdateEvent] {

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def apply(event: DeviceUpdateEvent): Future[Unit] = event.correlationId match {
    case CampaignCorrelationId(uuid) => dispatch(CampaignId(uuid), event)
    case _ => Future.successful(())
  }

  private def dispatch(campaignId: CampaignId, event: DeviceUpdateEvent): Future[Unit] =
    event match {
      case msg: DeviceUpdateCanceled  => handleUpdateCanceled(campaignId, msg)
      case msg: DeviceUpdateCompleted => handleUpdateCompleted(campaignId, msg)
      case _ => Future.successful(())
    }

  private def handleUpdateCanceled(campaignId: CampaignId, msg: DeviceUpdateCanceled): Future[Unit] =
    campaigns.cancelDevices(campaignId, Seq(msg.deviceUuid))


  private def completedEventReceived(campaignId: CampaignId, deviceId: DeviceId): Future[Boolean] = {
    campaigns.repositories.deviceUpdateRepo.findUpdateStatusByDeviceCampaign(campaignId, deviceId).map {
      case Some(status) if status == DeviceStatus.successful || status == DeviceStatus.cancelled => true
      case _ => false
    }
  }

  private def handleUpdateCompleted(campaignId: CampaignId, msg: DeviceUpdateCompleted): Future[Unit] = {
    val resultCode = msg.result.code
    val resultDescription = msg.result.description

    completedEventReceived(campaignId, msg.deviceUuid).flatMap {
      case true =>
        _log.warn(s"Already received a DeviceUpdateCompleted for $msg, ignoring event")
        FastFuture.successful(())
      case false =>
        val f = if (msg.result.success) {
          campaigns.succeedDevices(campaignId, Seq(msg.deviceUuid), resultCode, resultDescription)
        } else {
          campaigns.failDevices(campaignId, Seq(msg.deviceUuid), resultCode, resultDescription)
        }

        f.recover {
          case Errors.DeviceNotScheduled =>
            _log.info(s"Got DeviceUpdateEvent for device ${msg.deviceUuid.show} which is not scheduled by campaigner, ignoring this message.")
        }
    }
  }
}
