package com.advancedtelematic.campaigner.daemon

import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.campaigner.data.DataType.{DeviceStatus, DeviceUpdate}
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{CampaignerSpec, DatabaseUpdateSpecUtil, FakeDirectorClient}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import com.advancedtelematic.libats.test.DatabaseSpec
import org.scalacheck.Arbitrary._

import java.time.Instant
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteDeviceRequestListenerSpec extends CampaignerSpec
  with DatabaseSpec
  with DatabaseUpdateSpecUtil {

  import repositories.deviceUpdateRepo

  private val director = new FakeDirectorClient() {
    override def cancelUpdate(ns: Namespace, devices: Seq[DeviceId]): Future[Seq[DeviceId]] = {
      cancelled.addAll(devices.asJava)
      FastFuture.successful(devices)
    }
  }

  val listener = new DeleteDeviceRequestListener(director, campaigns)

  "listener" should "cancel device update with `requested` status" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val device = arbitrary[DeviceId].generate

    deviceUpdateRepo.persistMany(Seq(DeviceUpdate(campaign.id, campaign.updateId, device, DeviceStatus.requested))).futureValue
    val msg = DeleteDeviceRequest(campaign.namespace, device, Instant.now())

    listener.apply(msg).futureValue

    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.cancelled).futureValue shouldBe Set(device)
  }

  "listener" should "cancel device update with `scheduled` status" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val device = arbitrary[DeviceId].generate

    deviceUpdateRepo.persistMany(Seq(DeviceUpdate(campaign.id, campaign.updateId, device, DeviceStatus.scheduled))).futureValue
    val msg = DeleteDeviceRequest(campaign.namespace, device, Instant.now())

    listener.apply(msg).futureValue

    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.cancelled).futureValue shouldBe Set(device)
  }

  "listener" should "call director for check and cancel assigment for device" in {
    val campaign = createDbCampaignWithUpdate().futureValue
    val device = arbitrary[DeviceId].generate

    deviceUpdateRepo.persistMany(Seq(DeviceUpdate(campaign.id, campaign.updateId, device, DeviceStatus.accepted))).futureValue
    val msg = DeleteDeviceRequest(campaign.namespace, device, Instant.now())

    listener.apply(msg).futureValue

    director.cancelled should contain(device)
  }

}
