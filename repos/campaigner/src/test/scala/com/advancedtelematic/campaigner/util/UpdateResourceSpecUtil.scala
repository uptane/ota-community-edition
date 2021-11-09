package com.advancedtelematic.campaigner.util

import akka.http.scaladsl.model.StatusCodes._
import com.advancedtelematic.campaigner.data.Codecs._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.db.{Campaigns, Repositories}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import CampaignerSpecUtil._
import cats.data.NonEmptyList
import com.advancedtelematic.libats.data.DataType.Namespace

import scala.concurrent.Future

trait UpdateResourceSpecUtil {
  self: ResourceSpec with Matchers =>

  val campaigns = Campaigns()

  def createUpdateOk(request: CreateUpdate): UpdateId = {
    Post(apiUri("updates"), request).withHeaders(header) ~> routes ~> check {
      status shouldBe Created
      responseAs[UpdateId]
    }
  }

  def createCampaignWithUpdateOk(createCampaignGen: Gen[CreateCampaign] = genCreateCampaign(),
                                 devicesGen: Gen[Seq[DeviceId]] = arbitrary[DeviceId].map(Seq(_))): (CampaignId, CreateCampaign) = {
    val createCampaign = for {
      createUpdate <- genCreateUpdate(genType = Gen.const(UpdateType.multi_target))
      updateId = createUpdateOk(createUpdate)
      createCampaign <- createCampaignGen.map(_.copy(update = updateId))
      devices <- devicesGen
      _ = fakeRegistry.setGroup(createCampaign.groups.head, devices)
    } yield createCampaign
    createCampaign.map(cc => createCampaignOk(cc) -> cc).generate
  }
}

trait DatabaseUpdateSpecUtil {
  self: DatabaseSpec with ScalaFutures =>

  import scala.concurrent.ExecutionContext.Implicits.global

  val repositories = Repositories()
  val campaigns = new Campaigns(repositories)

  def createDbUpdate(updateId: UpdateId): Future[UpdateId] = {
    val update = genMultiTargetUpdate.generate.copy(uuid = updateId)
    repositories.updateRepo.persist(update)
  }

  def createDbCampaign(namespace: Namespace, updateId: UpdateId, groups: NonEmptyList[GroupId]): Future[Campaign] = {
    val campaign = arbitrary[Campaign].generate.copy(updateId = updateId, namespace = namespace)
    campaigns.create(campaign, groups.toList.toSet, Set.empty, Seq.empty).map(_ => campaign)
  }

  def createDbCampaignWithUpdate(maybeCampaign: Option[Campaign] = None, maybeGroups: Option[NonEmptyList[GroupId]] = None): Future[Campaign] = {
    val campaign = maybeCampaign.getOrElse(arbitrary[Campaign].generate)
    val groups = maybeGroups.map(_.toList.toSet).getOrElse(Set.empty)
    for {
      _ <- createDbUpdate(campaign.updateId)
      _ <- campaigns.create(campaign, groups, Set.empty, Seq.empty)
    }  yield campaign
  }
}
