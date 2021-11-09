package com.advancedtelematic.campaigner.actor

import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import com.advancedtelematic.campaigner.client._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{ActorSpec, CampaignerSpec, DatabaseUpdateSpecUtil}
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import org.scalacheck.Gen
import org.scalatest.Inspectors

import scala.concurrent.Future

class CampaignSchedulerSpec extends ActorSpec[CampaignScheduler] with CampaignerSpec
  with DatabaseUpdateSpecUtil with Inspectors {
  import CampaignScheduler._

  import scala.concurrent.duration._

  "campaign scheduler" should "trigger updates for each device" in {
    val campaign = buildCampaignWithUpdate
    val parent   = TestProbe()
    val n = Gen.choose(batch, batch * 2).generate
    val devices = Gen.listOfN(n, genDeviceId).generate.toSet

    var actualDevices = Set.empty[DeviceId]
    val director = new DirectorClient {
      override def setMultiUpdateTarget(
        ns: Namespace,
        update: ExternalUpdateId,
        devices: Seq[DeviceId],
        correlationId: CorrelationId
      ): Future[Seq[DeviceId]] = {
        actualDevices = actualDevices ++ devices.toSet
        FastFuture.successful(devices)
      }

      override def cancelUpdate(
        ns: Namespace,
        devs: Seq[DeviceId]
      ): Future[Seq[DeviceId]] = FastFuture.successful(Seq.empty)

      override def cancelUpdate(
        ns: Namespace,
        device: DeviceId): Future[Unit] = FastFuture.successful(())

      override def findAffected(ns: Namespace, updateId: ExternalUpdateId, devices: Seq[DeviceId]): Future[Seq[DeviceId]] =
        Future.successful(Seq.empty)
    }

    campaigns.create(campaign, Set.empty, devices, Seq.empty).futureValue

    parent.childActorOf(CampaignScheduler.props(
      director,
      campaigns,
      campaign,
      schedulerDelay,
      schedulerBatchSize
    ))
    parent.expectMsg(1.minute, CampaignComplete(campaign.id))

    actualDevices shouldBe devices
  }

  "campaign scheduler" should "not schedule devices if campaign was canceled" in {
    val campaign = buildCampaignWithUpdate
    val parent   = TestProbe()
    val devices = Gen.listOfN(batch, genDeviceId).generate.toSet

    campaigns.create(campaign, Set.empty, devices, Seq.empty).futureValue

    campaigns.cancel(campaign.id).futureValue

    parent.childActorOf(CampaignScheduler.props(
      director,
      campaigns,
      campaign,
      schedulerDelay,
      schedulerBatchSize
    ))

    parent.expectMsg(5.seconds, CampaignComplete(campaign.id))

    val processed = repositories.deviceUpdateRepo.findByCampaignStream(campaign.id, DeviceStatus.requested)
      .map(d => List(d._1)).runWith(Sink.fold(List.empty[DeviceId])(_ ++ _)).futureValue

    processed should contain allElementsOf(devices.toList)
  }

  "PRO-3672: campaign with 0 affected devices" should "yield a `finished` status" in {
    val campaign = buildCampaignWithUpdate
    val parent   = TestProbe()
    val n = Gen.choose(batch, batch * 2).generate
    val devices = Gen.listOfN(n, genDeviceId).generate.toSet

    val director = new DirectorClient {
      override def setMultiUpdateTarget(
        ns: Namespace,
        update: ExternalUpdateId,
        devices: Seq[DeviceId],
        correlationId: CorrelationId
      ): Future[Seq[DeviceId]] = FastFuture.successful(Seq.empty)

      override def cancelUpdate(
        ns: Namespace,
        devs: Seq[DeviceId]
      ): Future[Seq[DeviceId]] = FastFuture.successful(Seq.empty)

      override def cancelUpdate(
        ns: Namespace,
        device: DeviceId): Future[Unit] = FastFuture.successful(())

      override def findAffected(ns: Namespace, updateId: ExternalUpdateId, devices: Seq[DeviceId]): Future[Seq[DeviceId]] =
        Future.successful(Seq.empty)
    }

    campaigns.create(campaign, Set.empty, devices, Seq.empty).futureValue

    parent.childActorOf(CampaignScheduler.props(
      director,
      campaigns,
      campaign,
      schedulerDelay,
      schedulerBatchSize
    ))
    parent.expectMsg(20.seconds, CampaignComplete(campaign.id))

    campaigns.campaignStats(campaign.id).futureValue.status shouldBe CampaignStatus.finished
  }

  "OTA-3153: campaign with 0 requested devices" should "yield a `finished` status" in {
    val campaign = buildCampaignWithUpdate
    val parent   = TestProbe()
    campaigns.create(campaign, Set.empty, Set.empty, Seq.empty).futureValue

    parent.childActorOf(CampaignScheduler.props(
      director,
      campaigns,
      campaign,
      schedulerDelay,
      schedulerBatchSize
    ))
    parent.expectMsg(20.seconds, CampaignComplete(campaign.id))

    campaigns.campaignStats(campaign.id).futureValue.status shouldBe CampaignStatus.finished
  }
}
