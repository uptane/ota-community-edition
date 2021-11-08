package com.advancedtelematic.campaigner.daemon

import com.advancedtelematic.campaigner.client.DirectorClient
import com.advancedtelematic.campaigner.data.DataType.{CampaignId, DeviceStatus}
import com.advancedtelematic.campaigner.db.Campaigns
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class DeleteDeviceRequestListener(directorClient: DirectorClient, campaigns: Campaigns)(implicit val db: Database, val ec: ExecutionContext)
  extends MsgOperation[DeleteDeviceRequest] {

  private val log = LoggerFactory.getLogger(this.getClass)

  override def apply(message: DeleteDeviceRequest): Future[Unit] = {
    log.info(s"Received delete device request: $message")
    for {
      campaigns <- findScheduledCampaigns(message.uuid)
      _ <- Future.traverse(campaigns)(cancelScheduledDeviceUpdate(message.uuid, _))
      _ <- cancelAcceptedDeviceUpdate(message.namespace, message.uuid)
    } yield ()
  }

  private def findScheduledCampaigns(deviceId: DeviceId): Future[Seq[CampaignId]] = {
    campaigns.repositories.deviceUpdateRepo.findDeviceCampaigns(deviceId, DeviceStatus.requested, DeviceStatus.scheduled)
      .map(_.map { case (campaign, _) => campaign.id })
      .recover { case e: Exception =>
        log.error(s"Cannot load campaigns for device $deviceId", e)
        Seq.empty
      }
  }

  private def cancelScheduledDeviceUpdate(deviceId: DeviceId, campaignId: CampaignId): Future[Unit] =
    campaigns
      .cancelDevices(campaignId, Seq(deviceId))
      .map(_ => log.info(s"Canceled update for device $deviceId and campaign $campaignId"))
      .recover { case e: Exception =>
        log.error(s"Cannot cancel device update for $deviceId and $campaignId", e)
      }

  /**
   * Director sends `DeviceUpdateCanceled` event to Kafka after assignment canceling,
   * and then DeviceUpdateEventListener process this event and cancels the device update.
   */
  private def cancelAcceptedDeviceUpdate(namespace: Namespace, deviceId: DeviceId) =
    directorClient
      .cancelUpdate(namespace, Seq(deviceId))
      .map {
        case affected if affected.nonEmpty => log.info(s"Cleaned up assignment for device $deviceId and namespace $namespace")
        case _ => log.info(s"Assignment for device $deviceId and namespace $namespace not found or is already in flight")
      }
      .recover { case e: Exception =>
        log.error(s"Error on cancel assignments request", e)
      }
}
