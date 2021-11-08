package com.advancedtelematic.campaigner.db

import com.advancedtelematic.campaigner.data.DataType.CampaignStatus._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{CampaignerSpecUtil, DatabaseUpdateSpecUtil}
import com.advancedtelematic.libats.data.DataType.{ResultCode, ResultDescription}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.test.DatabaseSpec
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.SpanSugar._
import org.scalatest.{AsyncFlatSpec, Matchers}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Future

class CampaignsSpec extends AsyncFlatSpec
  with DatabaseSpec
  with Matchers
  with ScalaFutures
  with DatabaseUpdateSpecUtil
  with CampaignerSpecUtil {

  import Arbitrary._

  "count campaigns" should "return a list of how many campaigns there are for each status" in {
    val statuses = Seq(launched, finished, finished, cancelled, cancelled, cancelled)
    val cs = statuses.map(s => genCampaign.generate.copy(status = s))
    for {
      _ <- Future.sequence(cs.map(c => createDbCampaignWithUpdate(Some(c))))
      res <- campaigns.countByStatus
    } yield res shouldBe Map(prepared -> 0, launched -> 1, finished -> 2, cancelled -> 3)
  }

  "finishing devices" should "work with one campaign" in {
    val devices  = arbitrary[Seq[DeviceId]].generate

    for {
      campaign <- createDbCampaignWithUpdate()
      _ <- campaigns.scheduleDevices(campaign.id, devices)
      _ <- campaigns.failDevices(campaign.id, devices, ResultCode("failure-code-1"), ResultDescription("failure-description-1"))
      stats <- campaigns.campaignStats(campaign.id)
    } yield stats.finished shouldBe devices.length
  }

  "finishing devices" should "not change the status if the campaign was cancelled" in {
    val devices  = arbitrary[Seq[DeviceId]].generate

    for {
      campaign <- createDbCampaignWithUpdate()
      _ <- campaigns.scheduleDevices(campaign.id, devices)
      _ <- campaigns.cancel(campaign.id)
      _ <- campaigns.failDevices(campaign.id, devices, ResultCode("failure-code-1"), ResultDescription("failure-description-1"))
      finalStatus <- db.run(repositories.campaignRepo.findAction(campaign.id).map(_.status))
    } yield finalStatus shouldBe CampaignStatus.cancelled
  }
}

final class CampaignsFindFailedDevicesSpec extends AsyncFlatSpec
  with DatabaseSpec
  with Matchers
  with ScalaFutures
  with DatabaseUpdateSpecUtil
  with CampaignerSpecUtil {

  implicit val defaultPatience = PatienceConfig(timeout = 2 seconds)

  "findFailedDeviceUpdates" should "find all failed device update from all the given campaigns and return only the most recent ones" in {
    val campaignIds = Gen.choose(3, 6).flatMap(Gen.listOfN(_, genCampaignId)).generate
    val updateId = createDbUpdate(UpdateId.generate()).futureValue
    val campaignObjects = campaignIds.map(cid => genCampaign.generate.copy(id = cid, updateId = updateId))

    val deviceIds = Gen.choose(2, 8).flatMap(Gen.listOfN(_, genDeviceId)).generate
    val updates = campaignIds.flatMap(cid => deviceIds.map { did =>
      genDeviceUpdate().map(_.copy(campaign = cid, update = updateId, device = did)).generate
    })

    val expectedFailures = updates
      .filter { du =>
        du.status == DeviceStatus.rejected || du.status == DeviceStatus.successful ||
          du.status == DeviceStatus.cancelled || du.status == DeviceStatus.failed
      }
      .groupBy(_.device)
      .mapValues(_.maxBy(_.updatedAt))
      .values
      .filter(_.status == DeviceStatus.failed)
      .toSet

    val resultAction = for {
      _ <- Schema.campaigns ++= campaignObjects
      _ <- Schema.deviceUpdates ++= updates
      failures <- campaigns.findFailedDeviceUpdatesAction(campaignIds.toSet)
    } yield failures

    db.run(resultAction).flatMap(_ should contain theSameElementsAs expectedFailures)
  }
}
