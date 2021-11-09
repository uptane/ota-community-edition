package com.advancedtelematic.campaigner.daemon

import akka.Done
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.campaigner.client.DirectorClient
import com.advancedtelematic.campaigner.data.DataType.CampaignId
import com.advancedtelematic.campaigner.db.{Campaigns, DeviceUpdateProcess}
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.DataType.EventType
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceEventMessage
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object DeviceEventListener {
  import io.circe.generic.semiauto._
  import io.circe.{Decoder, Encoder}

  val CampaignAcceptedEventType = EventType("campaign_accepted", 0)

  case class AcceptedCampaign(campaignId: CampaignId)

  implicit val acceptedCampaignEncoder: Encoder[AcceptedCampaign] = deriveEncoder
  implicit val acceptedCampaignDecoder: Decoder[AcceptedCampaign] = deriveDecoder
}

class DeviceEventListener(directorClient: DirectorClient, campaigns: Campaigns)(implicit ec: ExecutionContext)
  extends MsgOperation[DeviceEventMessage] {

  import DeviceEventListener._

  private val _logger = LoggerFactory.getLogger(this.getClass)

  def deviceUpdateProcess = new DeviceUpdateProcess(directorClient, campaigns)

  def apply(msg: DeviceEventMessage): Future[Done] =
    msg.event.eventType match {
      case CampaignAcceptedEventType =>
        for {
          acceptedCampaign <- Future.fromTry(msg.event.payload.as[AcceptedCampaign].toTry)
          _ <- deviceUpdateProcess.processDeviceAcceptedUpdate(msg.namespace, acceptedCampaign.campaignId, msg.event.deviceUuid)
        } yield Done

      case e @ EventType(CampaignAcceptedEventType.id, version) =>
        FastFuture.failed(new IllegalArgumentException(s"Could not process version $version of ${e.id} event: ${msg.event}"))

      case event =>
        _logger.debug(s"Ignoring unknown event $event from device ${msg.event.deviceUuid}")
        FastFuture.successful(Done)
    }
}
