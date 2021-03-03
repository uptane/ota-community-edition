package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.util.FastFuture
import cats.data.NonEmptyList
import com.advancedtelematic.campaigner.client.{ExternalUpdate, ResolverClient, UserProfileClient}
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.{
  CampaignMetadataSupport,
  CampaignSupport,
  DeviceUpdateSupport,
  UpdateSupport
}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

private[http] case class GetDeviceCampaigns(deviceId: DeviceId, campaigns: Seq[DeviceCampaign])

private[http] object GetDeviceCampaigns {

  implicit val EncoderInstance: Encoder[GetDeviceCampaigns] = deriveEncoder
  implicit val DecoderInstance: Decoder[GetDeviceCampaigns] = deriveDecoder

}

private[http] case class DeviceCampaign(id: CampaignId,
                                        name: String,
                                        size: Option[Long],
                                        metadata: Seq[CreateCampaignMetadata])

private[http] object DeviceCampaign {
  import com.advancedtelematic.campaigner.data.Codecs._

  implicit val EncoderInstance: Encoder[DeviceCampaign] = deriveEncoder
  implicit val DecoderInstance: Decoder[DeviceCampaign] = deriveDecoder
}

class DeviceCampaigns(userProfileClient: UserProfileClient, resolverClient: ResolverClient)(implicit val db: Database,
                                                                                            val ec: ExecutionContext)
    extends DeviceUpdateSupport
    with UpdateSupport
    with CampaignSupport
    with CampaignMetadataSupport {

  private[this] def fetchSizesForUpdates(ns: Namespace,
                                         deviceId: DeviceId)(updates: List[Update]): Future[Map[UpdateId, Long]] = {
    val updateUuidToSize: List[Update] => ExternalUpdate => Option[(UpdateId, Long)] =
      xs =>
        x => xs.find(y => y.source.sourceType == UpdateType.external && x.updateId == y.source.id).map(_.uuid -> x.size)

    userProfileClient.externalResolverUri(ns).flatMap {
      case Some(x) =>
        resolverClient
          .updatesForDevice(x, ns, deviceId)
          .map(_.map(updateUuidToSize(updates)).foldLeft(Map.empty[UpdateId, Long]) { (xs, x) =>
            x.map((xs.updated _).tupled).getOrElse(xs)
          })

      case None =>
        FastFuture.successful(Map.empty)
    }
  }

  private[this] def getUpdateSizes(ns: Namespace,
                                   deviceId: DeviceId,
                                   campaigns: List[Campaign]): Future[Map[UpdateId, Long]] =
    NonEmptyList.fromList(campaigns) match {
      case Some(xs) =>
        updateRepo.findByIds(ns, xs.map(_.updateId)).flatMap(fetchSizesForUpdates(ns, deviceId))

      case None =>
        FastFuture.successful(Map.empty)
    }

  def findScheduledCampaigns(ns: Namespace, deviceId: DeviceId): Future[GetDeviceCampaigns] =
    for {
      campaignsWithMeta <- deviceUpdateRepo.findDeviceCampaigns(deviceId, DeviceStatus.scheduled)
      updateSizes       <- getUpdateSizes(ns, deviceId, campaignsWithMeta.map(_._1).toList)
    } yield {
      val campaignToMetadata = campaignsWithMeta.groupBy(_._1).mapValues(_.flatMap(_._2))

      val clientCampaigns = campaignToMetadata.map {
        case (campaign, meta) =>
          DeviceCampaign(campaign.id,
                         campaign.name,
                         updateSizes.get(campaign.updateId),
                         meta.map(m => CreateCampaignMetadata(m.`type`, m.value)))
      }.toSeq

      GetDeviceCampaigns(deviceId, clientCampaigns)
    }
}
