package com.advancedtelematic.campaigner.actor

import akka.actor.Terminated
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import com.advancedtelematic.campaigner.client.DirectorClient
import com.advancedtelematic.campaigner.data.DataType.DeviceStatus
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{ActorSpec, CampaignerSpec, DatabaseUpdateSpecUtil, FakeDirectorClient}
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import org.scalacheck.Gen
import org.scalatest.Inspectors

import scala.concurrent.Future
import scala.concurrent.duration._

class CampaignCancelerSpec extends ActorSpec[CampaignCancelerSpec] with CampaignerSpec
    with DatabaseUpdateSpecUtil with Inspectors {
  import repositories.deviceUpdateRepo

  lazy val directorWithoutAffectedDevicesOnCancel: DirectorClient = new FakeDirectorClient() {
    override def cancelUpdate(ns: DataType.Namespace, devices: Seq[DeviceId]): Future[Seq[DeviceId]] =
      Future.successful(Seq.empty)
  }

  "campaign canceler" should "cancel devices which were not scheduled yet" in {
    val campaign = buildCampaignWithUpdate
    val parent   = TestProbe()
    val devices = Gen.listOfN(1, genDeviceId).generate.toSet

    campaigns.create(campaign, Set.empty, devices, Seq.empty).futureValue

    campaigns.cancel(campaign.id).futureValue

    val canceler = parent.childActorOf(CampaignCanceler.props(director, campaigns, campaign.id, campaign.namespace, 10))

    parent.watch(canceler)

    parent.expectMsgPF(5.seconds) ({
      case Terminated(_) => true
    })

    val processed = deviceUpdateRepo.findByCampaignStream(campaign.id, DeviceStatus.cancelled).map(_._1).runWith(Sink.seq).futureValue

    processed should contain allElementsOf(devices)
  }

  "campaign canceler" should "cancel scheduled devices which need approve" in {
    val campaign = buildCampaignWithUpdate.copy(autoAccept = false)
    val parent = TestProbe()
    val devices = Gen.listOfN(1, genDeviceId).generate.toSet

    campaigns.create(campaign, Set.empty, devices, Seq.empty).futureValue
    campaigns.scheduleDevices(campaign.id, devices.toSeq).futureValue

    campaigns.cancel(campaign.id).futureValue

    val canceler = parent.childActorOf(CampaignCanceler.props(directorWithoutAffectedDevicesOnCancel, campaigns, campaign.id, campaign.namespace, 10))

    parent.watch(canceler)

    parent.expectMsgPF(5.seconds)({
      case Terminated(_) => true
    })

    val processed = deviceUpdateRepo.findByCampaignStream(campaign.id, DeviceStatus.cancelled).map(_._1).runWith(Sink.seq).futureValue

    processed should contain allElementsOf (devices)
  }

  "campaign canceler" should "not cancel scheduled devices which don't need approve" in {
    val campaign = buildCampaignWithUpdate.copy(autoAccept = true)
    val parent = TestProbe()
    val devices = Gen.listOfN(1, genDeviceId).generate.toSet

    campaigns.create(campaign, Set.empty, devices, Seq.empty).futureValue
    campaigns.scheduleDevices(campaign.id, devices.toSeq).futureValue

    campaigns.cancel(campaign.id).futureValue

    val canceler = parent.childActorOf(CampaignCanceler.props(directorWithoutAffectedDevicesOnCancel, campaigns, campaign.id, campaign.namespace, 10))

    parent.watch(canceler)

    parent.expectMsgPF(5.seconds)({
      case Terminated(_) => true
    })

    val processed = deviceUpdateRepo.findByCampaignStream(campaign.id, DeviceStatus.cancelled).map(_._1).runWith(Sink.seq).futureValue

    processed should not contain allElementsOf (devices)
  }

}
