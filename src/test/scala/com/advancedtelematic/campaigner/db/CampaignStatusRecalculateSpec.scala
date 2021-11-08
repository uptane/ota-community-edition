package com.advancedtelematic.campaigner.db

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.data.NonEmptyList
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.CampaignerSpecUtil
import com.advancedtelematic.campaigner.util.DatabaseUpdateSpecUtil
import com.advancedtelematic.libats.test.DatabaseSpec
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.time.SpanSugar._
import scala.concurrent.Future
import slick.jdbc.MySQLProfile.api._

final class CampaignStatusRecalculateSpec
    extends TestKit(ActorSystem("CampaignStatusRecalculateSpec"))
    with FlatSpecLike
    with ScalaFutures
    with DatabaseSpec
    with Matchers
    with DatabaseUpdateSpecUtil
    with CampaignerSpecUtil {

  implicit lazy val ec = system.dispatcher
  implicit val mat = ActorMaterializer()
  implicit val defaultPatience = PatienceConfig(timeout = Span(2, Seconds))

  it should "set campaign status to `finished` if all devices finished the campaign" in {
    val groups = GroupWithDevices.genNonEmptyListOf.generate
    val totalDevicesNum = groups.map(_.devicesNum).toList.sum
    val devicesNumToSucceed = Gen.chooseNum(0, totalDevicesNum).generate
    val devicesNumToFail = totalDevicesNum - devicesNumToSucceed

    val setupTest = for {
      campaign <- createCampaignWithoutStatus(maybeGroups = Some(groups.map(_.id)))
      _ <- insertDeviceUpdatesFor(campaign, devicesNumToSucceed, DeviceStatus.successful)
      _ <- insertDeviceUpdatesFor(campaign, devicesNumToFail, DeviceStatus.failed)
      _ <- new CampaignStatusRecalculate(repositories).run
    } yield campaign

    whenReady(setupTest, timeout(40 seconds)) { campaign =>
      val updatedCampaign = repositories.campaignRepo.find(campaign.id).futureValue
      updatedCampaign.status shouldBe CampaignStatus.finished
    }
  }

  it should "set campaign status to `launched` if not all devices finished the campaign" in {
    val groups = GroupWithDevices.genNonEmptyListOf.generate
    val totalDevicesNum = groups.map(_.devicesNum).toList.sum
    val numOfDevicesToFinish = Gen.chooseNum(0, totalDevicesNum - 1).generate
    val numOfDevicesToSchedule = totalDevicesNum - numOfDevicesToFinish

    val setupTest = for {
      campaign <- createCampaignWithoutStatus(maybeGroups = Some(groups.map(_.id)))
      _ <- insertDeviceUpdatesFor(campaign, numOfDevicesToSchedule, DeviceStatus.scheduled)
      _ <- insertDeviceUpdatesFor(campaign, numOfDevicesToFinish, DeviceStatus.successful)
      _ <- new CampaignStatusRecalculate(repositories).run
    } yield campaign

    whenReady(setupTest, timeout(40 seconds)) { campaign =>
      val updatedCampaign = repositories.campaignRepo.find(campaign.id).futureValue
      updatedCampaign.status shouldBe CampaignStatus.launched
    }
  }

  private def createCampaignWithoutStatus(maybeGroups: Option[NonEmptyList[GroupId]]): Future[Campaign] = {
    for {
      campaign <- createDbCampaignWithUpdate(maybeGroups = maybeGroups)
      _ <- db.run(sqlu"update campaigns set status = null where uuid = ${campaign.id.uuid.toString}")
    } yield campaign
  }

  private def insertDeviceUpdatesFor(campaign: Campaign, numOfDevicesToFinish: Int, status: DeviceStatus.Value): Future[Unit] = {
    val updates = 1.to(numOfDevicesToFinish).map(_ =>
        DeviceUpdate(campaign.id, campaign.updateId, genDeviceId.generate, status))
    repositories.deviceUpdateRepo.persistMany(updates)
  }

  private case class GroupWithDevices(id: GroupId, devicesNum: Int)
  private object GroupWithDevices {
    def genNonEmptyListOf: Gen[NonEmptyList[GroupWithDevices]] = {
      val genGroup = for {
        gid <- genGroupId
        devicesNum <- Gen.chooseNum(1, 5)
      } yield GroupWithDevices(gid, devicesNum)

      Gen.nonEmptyListOf(genGroup).map(NonEmptyList.fromListUnsafe)
    }
  }
}
