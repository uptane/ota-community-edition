package com.advancedtelematic.campaigner.db

import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.campaigner.client.DirectorClient
import com.advancedtelematic.campaigner.data.DataType.{Campaign, CampaignId, UpdateType}
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode, ResultDescription, CampaignId => CampaignCorrelationId}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import org.slf4j.LoggerFactory

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

object DeviceUpdateProcess {
  sealed trait StartUpdateResult
  case class Started(acceptedDevices: Set[DeviceId], scheduledDevices: Set[DeviceId], rejectedDevices: Set[DeviceId]) extends StartUpdateResult
  case object CampaignCancelled extends StartUpdateResult
}

class DeviceUpdateProcess(director: DirectorClient, campaigns: Campaigns)(implicit ec: ExecutionContext) {

  import DeviceUpdateProcess._
  import campaigns.repositories._

  private val _logger = LoggerFactory.getLogger(this.getClass)

  def startUpdateFor(devices: Set[DeviceId], campaign: Campaign): Future[StartUpdateResult] = {
    updateRepo.findById(campaign.updateId).zip(cancelTaskRepo.isCancelled(campaign.id)).flatMap { case (update, cancelled) =>
      if (cancelled)
        FastFuture.successful(CampaignCancelled)
      else {
        val acceptDevices: Future[Set[DeviceId]] = {
          if (campaign.autoAccept) {
            director
              .setMultiUpdateTarget(campaign.namespace, update.source.id,
                devices.toSeq, CampaignCorrelationId(campaign.id.uuid))
              .map(_.toSet)
          } else {
            FastFuture.successful(Set.empty)
          }
        }

        val scheduleDevices: Future[Set[DeviceId]] = {
          if (campaign.autoAccept) {
            FastFuture.successful(Set.empty)
          } else if (update.source.sourceType == UpdateType.external) {
            FastFuture.successful(devices)
          } else {
            director
              .findAffected(campaign.namespace, update.source.id, devices.toSeq)
              .map(_.toSet)
          }
        }

        for {
          accepted <- acceptDevices
          scheduled <- scheduleDevices
          rejected = devices -- accepted -- scheduled
        } yield Started(accepted, scheduled, rejected)
      }
    }
  }

  def processDeviceAcceptedUpdate(ns: Namespace, campaignId: CampaignId, deviceId: DeviceId): Future[Unit] = async {
    if (await(cancelTaskRepo.isCancelled(campaignId))) {
      _logger.warn(s"Ignoring acceptance of cancelled campaign $campaignId by device $deviceId")
    } else {
      val campaign = await(campaigns.findClientCampaign(campaignId))
      val update = await(updateRepo.findById(campaign.update))
      val affected = await(director.setMultiUpdateTarget(ns, update.source.id, Seq(deviceId), CampaignCorrelationId(campaignId.uuid)))

      if (affected.contains(deviceId)) {
        await(campaigns.markDevicesAccepted(campaignId, Seq(deviceId)))
      } else {
        _logger.warn(s"Could not start mtu update for device $deviceId after device accepted, device is no longer affected")

        await(campaigns.scheduleDevices(campaignId, Seq(deviceId)))
        await(campaigns.failDevices(campaignId, Seq(deviceId), ResultCode("DEVICE_UPDATE_PROCESS_FAILED"),
                                    ResultDescription("DeviceUpdateProcess#processDeviceAcceptedUpdate failed")))
      }
    }
  }
}
