package com.advancedtelematic.campaigner.daemon

import java.time.Instant
import java.util.UUID

import org.scalatest.OptionValues._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{CampaignerSpec, DatabaseUpdateSpecUtil}
import com.advancedtelematic.libats.data.DataType.{CorrelationId, ResultCode, ResultDescription, CampaignId => CampaignCorrelationId}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, InstallationResult}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateCanceled, DeviceUpdateCompleted, DeviceUpdateEvent}
import com.advancedtelematic.libats.test.DatabaseSpec
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen

import scala.concurrent.ExecutionContext.Implicits.global

class DeviceUpdateEventListenerSpec extends CampaignerSpec
  with DatabaseSpec
  with DatabaseUpdateSpecUtil {
  import repositories.{updateRepo, deviceUpdateRepo}

  val listener = new DeviceUpdateEventListener(campaigns)

  "Listener" should "mark a device as successful using campaign CorrelationId" in {
    val (_, campaign, deviceUpdate) = prepareTest()
    val report = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = true)

    listener.apply(report).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.successful).futureValue should contain(deviceUpdate.device)
  }

  "Listener" should "ignore event if already received an event" in {
    val (_, campaign, deviceUpdate) = prepareTest()

    val report = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = true)

    listener.apply(report).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.successful).futureValue should contain(deviceUpdate.device)

    val report02 = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = false)

    listener.apply(report02).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.successful).futureValue should contain(deviceUpdate.device)
  }

  "Listener" should "not ignore event if already received an failed event" in {
    val (_, campaign, deviceUpdate) = prepareTest()

    val report = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = false)

    listener.apply(report).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.successful).futureValue shouldBe Set.empty

    val report02 = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = true)

    listener.apply(report02).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.successful).futureValue should contain only deviceUpdate.device
  }

  "Listener" should "mark a device as failed using campaign CorrelationId" in {
    val (_, campaign, deviceUpdate) = prepareTest()
    val report = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = false)

    listener.apply(report).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.failed).futureValue should contain(deviceUpdate.device)
    campaigns.findLatestFailedUpdates(campaign.id, ResultCode("FAILURE")).map(_.map(_.device)).futureValue should contain(deviceUpdate.device)
  }

  "Listener" should "save result descriptions to database, even if description is too big" in {
    val longDescription = Gen.listOfN(2048, Gen.alphaChar).map(_.mkString).generate

    val (_, campaign, deviceUpdate) = prepareTest()
    val report = makeReport(
      campaign,
      deviceUpdate,
      CampaignCorrelationId(campaign.id.uuid),
      isSuccessful = false,
      resultDescription = Option(ResultDescription(longDescription)))

    listener.apply(report).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.failed).futureValue should contain(deviceUpdate.device)
    val latest = campaigns.findLatestFailedUpdates(campaign.id, ResultCode("FAILURE")).map(_.flatMap(_.resultDescription)).futureValue.headOption
    longDescription should startWith(latest.value.value)
  }

  "Listener" should "mark a device as canceled using campaign CorrelationId" in {
    val (_, campaign, deviceUpdate) = prepareTest()
    val event = DeviceUpdateCanceled(
      campaign.namespace,
      Instant.now,
      CampaignCorrelationId(campaign.id.uuid),
      deviceUpdate.device)

    listener.apply(event).futureValue shouldBe (())
    deviceUpdateRepo.findByCampaign(campaign.id, DeviceStatus.cancelled).futureValue should contain(deviceUpdate.device)
  }

  private def prepareTest(): (UpdateSource, Campaign, DeviceUpdate) = {
    val updateSource = UpdateSource(ExternalUpdateId(UUID.randomUUID().toString), UpdateType.multi_target)
    val update = arbitrary[Update].generate.copy(source = updateSource)
    updateRepo.persist(update).futureValue

    val campaign = arbitrary[Campaign].generate.copy(namespace = update.namespace, updateId = update.uuid)
    campaigns.create(campaign, Set.empty, Set.empty, Seq.empty).futureValue

    val deviceUpdate = DeviceUpdate(campaign.id, update.uuid, DeviceId.generate(), DeviceStatus.accepted)
    deviceUpdateRepo.persistMany(Seq(deviceUpdate)).futureValue

    (updateSource, campaign, deviceUpdate)
  }

  private def makeReport(
      campaign: Campaign,
      deviceUpdate: DeviceUpdate,
      correlationId: CorrelationId,
      isSuccessful: Boolean,
      resultDescription: Option[ResultDescription] = None): DeviceUpdateEvent = {

    val installationResult =
      if (isSuccessful) {
        InstallationResult(true, ResultCode("SUCCESS"), resultDescription.getOrElse(ResultDescription("Successful update")))
      } else {
        InstallationResult(false, ResultCode("FAILURE"), resultDescription.getOrElse(ResultDescription("Failed update")))
      }

    DeviceUpdateCompleted(
      campaign.namespace,
      Instant.now,
      correlationId,
      deviceUpdate.device,
      installationResult,
      Map.empty,
      None)
  }
}
