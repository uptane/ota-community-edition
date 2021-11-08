package com.advancedtelematic.campaigner.daemon

import java.time.Instant

import akka.Done
import com.advancedtelematic.campaigner.daemon.DeviceEventListener.AcceptedCampaign
import com.advancedtelematic.campaigner.data.DataType.{Campaign, DeviceStatus, DeviceUpdate}
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{CampaignerSpec, DatabaseUpdateSpecUtil, FakeDirectorClient}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, Event, EventType}
import com.advancedtelematic.libats.messaging_datatype.Messages
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceEventMessage
import com.advancedtelematic.libats.test.DatabaseSpec
import io.circe.Json
import io.circe.syntax._
import org.scalacheck.Arbitrary._

import scala.concurrent.ExecutionContext.Implicits.global

class DeviceEventListenerSpec extends CampaignerSpec with DatabaseSpec with DatabaseUpdateSpecUtil {
  import repositories.{updateRepo, deviceUpdateRepo}

  lazy val director = new FakeDirectorClient()

  val listener = new DeviceEventListener(director, campaigns)

  private def mkDeviceEvent(campaign: Campaign, deviceId: DeviceId): DeviceEventMessage = {
    val payload = AcceptedCampaign(campaign.id)
    val event = Event(deviceId, "", DeviceEventListener.CampaignAcceptedEventType, Instant.now, Instant.now, payload.asJson)
    Messages.DeviceEventMessage(campaign.namespace, event)
  }

  private def mkEventOfType(eventType: EventType): DeviceEventMessage = {
    val deviceId = arbitrary[DeviceId].generate
    val event = Event(deviceId, "", eventType, Instant.now, Instant.now, Json.Null)
    Messages.DeviceEventMessage(Namespace("whatever"), event)
  }

  "listener" should "schedule update in director" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val update = updateRepo.findById(campaign.updateId).futureValue
    val device = arbitrary[DeviceId].generate
    val msg = mkDeviceEvent(campaign, device)

    listener.apply(msg).futureValue shouldBe Done

    director.updates.get(update.source.id) shouldBe Set(device)
  }

  it should "set device update status to accepted" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val device = arbitrary[DeviceId].generate

    deviceUpdateRepo.persistMany(Seq(DeviceUpdate(campaign.id, campaign.updateId, device, DeviceStatus.scheduled))).futureValue

    val msg = mkDeviceEvent(campaign, device)

    listener.apply(msg).futureValue shouldBe Done

    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.accepted).futureValue shouldBe Set(device)
  }

  it should "set device to failed if device is no longer affected" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val device = arbitrary[DeviceId].generate
    val msg = mkDeviceEvent(campaign, device)

    director.cancelled.add(device)

    listener.apply(msg).futureValue shouldBe Done

    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.failed).futureValue shouldBe Set(device)
  }

  it should "raise IllegalArgumentException if the message is of non-acceptable type" in {
    val msg = mkEventOfType(EventType("campaign_accepted", 42))
    listener.apply(msg).failed.futureValue shouldBe a[IllegalArgumentException]
  }

  it should "ignore events with unknown type" in {
    val msg = mkEventOfType(EventType("whatever", 42))
    listener.apply(msg).futureValue shouldBe Done
  }

  it should "not launch director update after acceptance of cancelled campaign" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val device = arbitrary[DeviceId].generate

    deviceUpdateRepo.persistMany(Seq(DeviceUpdate(campaign.id, campaign.updateId, device, DeviceStatus.scheduled))).futureValue

    campaigns.cancel(campaign.id).futureValue

    val msg = mkDeviceEvent(campaign, device)
    listener.apply(msg).futureValue shouldBe Done

    val update = updateRepo.findById(campaign.updateId).futureValue
    Option(director.updates.get(update.source.id)) shouldBe None
  }
}
