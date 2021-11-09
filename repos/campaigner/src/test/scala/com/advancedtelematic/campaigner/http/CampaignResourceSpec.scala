package com.advancedtelematic.campaigner.http

import java.time.Duration
import java.time.temporal.ChronoUnit

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, _}
import akka.util.ByteString
import cats.Eval
import cats.data.NonEmptyList
import cats.syntax.either._
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.campaigner.client.DeviceRegistryHttpClient
import com.advancedtelematic.campaigner.data.Codecs._
import com.advancedtelematic.campaigner.data.DataType.CampaignStatus.CampaignStatus
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{CampaignerSpec, ResourceSpec, UpdateResourceSpecUtil}
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.ErrorCodes.InvalidEntity
import com.advancedtelematic.libats.data.{ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.parser.parse
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.annotation.tailrec
import scala.concurrent.Future

class CampaignResourceSpec
    extends CampaignerSpec
    with ResourceSpec
    with UpdateResourceSpecUtil
    with Eventually
    with GivenWhenThen
    with ScalaCheckPropertyChecks {

  import campaigns.repositories.deviceUpdateRepo

  implicit val defaultPatience = PatienceConfig(timeout = 5 seconds)


  def conductCampaign(campaignId: CampaignId, campaignCase: CampaignCase, failureCode: ResultCode): Future[Unit] = for {
    _ <- campaigns.scheduleDevices(campaignId, campaignCase.affectedDevices)
    _ <- campaigns.rejectDevices(campaignId, campaignCase.notAffectedDevices)
    _ <- campaigns.cancelDevices(campaignId, campaignCase.cancelledDevices)
    _ <- campaigns.failDevices(campaignId, campaignCase.failedDevices, failureCode, ResultDescription("failure-description-1"))
    _ <- campaigns.succeedDevices(campaignId, campaignCase.successfulDevices, ResultCode("success-code-1"), ResultDescription("success-description-1"))
  } yield ()

  def checkStats(id: CampaignId, campaignStatus: CampaignStatus, processed: Long = 1, affected: Long = 1,
                 finished: Long = 0, failed: Long = 0, cancelled: Long = 0, successful: Long = 0): Unit =
    Get(apiUri(s"campaigns/${id.show}/stats")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
      val campaignStats = responseAs[CampaignStats]
      campaignStats.status shouldBe campaignStatus
      campaignStats shouldBe CampaignStats(
        campaign = id,
        status = campaignStatus,
        processed = processed,
        affected = affected,
        cancelled = cancelled,
        finished = finished,
        failed = failed,
        successful = successful,
        failures = Set.empty)
    }

  "POST and GET /campaigns" should "create a campaign, return the created campaign" in {
    val (id, request) = createCampaignWithUpdateOk()

    val campaign = getCampaignOk(id)
    campaign shouldBe GetCampaign(
      testNs,
      id,
      request.name,
      request.update,
      CampaignStatus.prepared,
      campaign.createdAt,
      campaign.updatedAt,
      None,
      Set.empty,
      request.metadata.toList.flatten,
      autoAccept = true
    )
    campaign.createdAt shouldBe campaign.updatedAt

    Get(apiUri(s"campaigns/${id.uuid}")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
      responseAs[GetCampaign].status shouldBe CampaignStatus.prepared
    }

    checkStats(id, CampaignStatus.prepared)
  }

  "POST and GET /campaigns" should "create a campaign, return the created campaign if metadata contains emoji" in {
    val metadataWithEmoji = CreateCampaignMetadata(MetadataType.DESCRIPTION, "text with emoji: \uD83D\uDE33\uD83D\uDE97\uD83D\uDE12")
    val createData = genCreateCampaign().map(_.copy(metadata = Some(Seq(metadataWithEmoji))))
    val (id, request) = createCampaignWithUpdateOk(createData)

    val campaign = getCampaignOk(id)
    campaign shouldBe GetCampaign(
      testNs,
      id,
      request.name,
      request.update,
      CampaignStatus.prepared,
      campaign.createdAt,
      campaign.updatedAt,
      None,
      Set.empty,
      request.metadata.toList.flatten,
      autoAccept = true
    )
    campaign.createdAt shouldBe campaign.updatedAt

    Get(apiUri(s"campaigns/${id.uuid}")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
      responseAs[GetCampaign].status shouldBe CampaignStatus.prepared
    }

    checkStats(id, CampaignStatus.prepared)
  }

  "POST /campaigns" should "create a campaign and populate it with all the devices in groups" in {
    val createUpdateReq = genCreateUpdate().map(cu => cu.copy(updateSource = UpdateSource(cu.updateSource.id, UpdateType.multi_target))).generate
    val updateId = createUpdateOk(createUpdateReq)
    val groupIds = arbitrary[NonEmptyList[GroupId]].generate
    val devicesInGroups = groupIds.toList.take(5).map(gid => (gid, arbitrary[Seq[DeviceId]].generate.take(100))).toMap

    def failingHttpClient(req: HttpRequest): Future[HttpResponse] =
      Future.failed(new Error("Not supposed to be called ever"))

    val testDeviceRegistry = new DeviceRegistryHttpClient(Uri("http://whatever.com"), failingHttpClient) {
      override def devicesInGroup(namespace: Namespace, groupId: GroupId, offset: Long, limit: Long): Future[Seq[DeviceId]] = {
        val response = devicesInGroups.getOrElse(groupId, Seq.empty).drop(offset.toInt).take(limit.toInt)
        Future.successful(response)
      }
    }
    val routes = new Routes(testDeviceRegistry, fakeResolver, fakeUserProfile, campaigns).routes

    val request = genCreateCampaign().map(_.copy(update = updateId, groups = groupIds)).generate
    val id = createCampaign(request) ~> routes ~> check {
      status shouldBe Created
      responseAs[CampaignId]
    }

    getCampaignsOk().values should contain (id)

    val devices = deviceUpdateRepo.findAllByCampaign(id).futureValue
    devices shouldBe devicesInGroups.values.flatten.toSet
  }

  "POST/GET autoAccept campaign" should "create and return the created campaign" in {
    val (id, request) = createCampaignWithUpdateOk(arbitrary[CreateCampaign].map(_.copy(approvalNeeded = Some(false))))

    val campaign = getCampaignOk(id)

    campaign shouldBe GetCampaign(
      testNs,
      id,
      request.name,
      request.update,
      CampaignStatus.prepared,
      campaign.createdAt,
      campaign.updatedAt,
      None,
      Set.empty,
      request.metadata.toList.flatten,
      autoAccept = true
    )
  }

  "POST /campaigns without groups" should "fail with InvalidEntity" in {
    val request = parse(
      """{
             "name" : "A campaign without groups",
             "update" : "84ab8e3d-6667-4edb-83ad-6cffbe064801",
             "groups" : []
             }""").valueOr(throw _)
    createCampaign(request) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe InvalidEntity
    }
  }

  "POST /campaigns with groups that don't contain devices" should "fail with CampaignWithoutDevices" in {
    val createUpdate = genCreateUpdate(genType = Gen.const(UpdateType.multi_target)).generate
    val updateId = createUpdateOk(createUpdate)
    val request = genCreateCampaign().map(_.copy(update = updateId)).generate
    createCampaign(request) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.CampaignWithoutDevices
    }
  }

  "PUT /campaigns/:campaign_id" should "update a campaign" in {
    val (id, _) = createCampaignWithUpdateOk()
    val update  = arbitrary[UpdateCampaign].generate
    val createdAt = getCampaignOk(id).createdAt

    Put(apiUri("campaigns/" + id.show), update).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
    }

    val updated = getCampaignOk(id)

    updated.updatedAt.isBefore(createdAt) shouldBe false
    updated.name shouldBe update.name
    updated.metadata shouldBe update.metadata.get

    checkStats(id, CampaignStatus.prepared)
  }

  "POST /campaigns/:campaign_id/retry-failed" should "create and launch a retry-campaign" in {
    val (mainCampaignId, mainCampaign) = createCampaignWithUpdateOk()
    val deviceId = genDeviceId.generate
    val failureCode = ResultCode("failure-code-1")
    val deviceUpdate = DeviceUpdate(mainCampaignId, mainCampaign.update, deviceId, DeviceStatus.accepted, Some(failureCode))

    deviceUpdateRepo.persistMany(deviceUpdate :: Nil).futureValue
    campaigns.failDevices(mainCampaignId, deviceId :: Nil, failureCode, Gen.alphaNumStr.map(ResultDescription).generate).futureValue

    val retryCampaignId = createAndLaunchRetryCampaign(mainCampaignId, RetryFailedDevices(failureCode)) ~> routes ~> check {
      status shouldBe Created
      responseAs[CampaignId]
    }

    assertRetryCampaign(Eval.always(getCampaignOk(retryCampaignId)), failureCode, mainCampaign.update, mainCampaignId)
  }

  "POST /campaigns/:campaign_id/retry-failed" should "create and launch a second retry-campaign when the device fails with a new code after the first retry" in {
    val (mainCampaignId, mainCampaign) = createCampaignWithUpdateOk()
    val deviceId = genDeviceId.generate

    val deviceUpdates = Gen.listOfN(2, Gen.alphaNumStr).generate.map(ResultCode).map { failureCode =>
      DeviceUpdate(mainCampaignId, mainCampaign.update, deviceId, DeviceStatus.accepted, Some(failureCode))
    }
    deviceUpdateRepo.persistMany(deviceUpdates).futureValue

    val failureCode1 :: failureCode2 :: Nil = deviceUpdates.map(_.resultCode.get)

    campaigns.failDevices(mainCampaignId, deviceId :: Nil, failureCode1, Gen.alphaNumStr.map(ResultDescription).generate).futureValue
    val retryCampaignId1 = createAndLaunchRetryCampaign(mainCampaignId, RetryFailedDevices(failureCode1)) ~> routes ~> check {
      status shouldBe Created
      responseAs[CampaignId]
    }

    assertRetryCampaign(Eval.always(getCampaignOk(retryCampaignId1)), failureCode1, mainCampaign.update, mainCampaignId)

    campaigns.failDevices(mainCampaignId, deviceId :: Nil, failureCode2, Gen.alphaNumStr.map(ResultDescription).generate).futureValue
    val retryCampaignId2 = createAndLaunchRetryCampaign(mainCampaignId, RetryFailedDevices(failureCode2)) ~> routes ~> check {
      status shouldBe Created
      responseAs[CampaignId]
    }

    assertRetryCampaign(Eval.always(getCampaignOk(retryCampaignId2)), failureCode2, mainCampaign.update, mainCampaignId)
  }

  "POST /campaigns/:campaign_id/retry-failed" should "fail if there are no failed devices for the given failure code" in {
    val (campaignId, _) = createCampaignWithUpdateOk()
    val request = RetryFailedDevices(ResultCode("failureCode-2"))

    createAndLaunchRetryCampaign(campaignId, request) ~> routes ~> check {
      status shouldBe PreconditionFailed
      responseAs[ErrorRepresentation].code shouldBe Errors.MissingFailedDevices(request.failureCode).code
    }
  }

  "POST /campaigns/:campaign_id/retry-failed" should "fail if there is no main campaign with id :campaign_id" in {
    val request = RetryFailedDevices(ResultCode("failureCode-3"))
    createAndLaunchRetryCampaign(CampaignId.generate(), request) ~> routes ~> check {
      status shouldBe NotFound
      responseAs[ErrorRepresentation].code shouldBe Errors.CampaignMissing.code
    }
  }

  "POST /campaigns/:campaign_id/launch" should "trigger an update" in {
    val (campaignId, _) = createCampaignWithUpdateOk()
    val request = Post(apiUri(s"campaigns/${campaignId.show}/launch")).withHeaders(header)

    request ~> routes ~> check {
      status shouldBe OK
    }

    checkStats(campaignId, CampaignStatus.launched)

    request ~> routes ~> check {
      status shouldBe Conflict
    }
  }

  "POST /campaigns/:campaign_id/cancel" should "cancel a campaign" in {
    val (campaignId, _) = createCampaignWithUpdateOk()

    checkStats(campaignId, CampaignStatus.prepared)

    Post(apiUri(s"campaigns/${campaignId.show}/launch")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
    }

    checkStats(campaignId, CampaignStatus.launched)

    Post(apiUri(s"campaigns/${campaignId.show}/cancel")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
    }

    checkStats(campaignId, CampaignStatus.cancelled)
  }

  "DELETE an update from a campaign" should "fail with 'MethodNotAllowed' if the campaign does not require approval" in {
    val (campaignId, _) = createCampaignWithUpdateOk()
    val device = DeviceId.generate()

    Post(apiUri(s"campaigns/${campaignId.show}/launch")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
    }

    campaigns.scheduleDevices(campaignId, Seq(device)).futureValue

    Delete(apiUri(s"campaigns/${campaignId.show}/devices/${device.show}")).withHeaders(header) ~> routes ~> check {
      status shouldBe MethodNotAllowed
    }
  }

  "DELETE an update from a campaign" should "cancel a single device update" in {
    val createCampaignWithApprovalNeeded = genCreateCampaign().map(_.copy(approvalNeeded = Some(true)))
    val (campaignId, _) = createCampaignWithUpdateOk(createCampaignWithApprovalNeeded)
    val device = DeviceId.generate()

    Post(apiUri(s"campaigns/${campaignId.show}/launch")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
    }

    campaigns.scheduleDevices(campaignId, Seq(device)).futureValue

    Delete(apiUri(s"campaigns/${campaignId.show}/devices/${device.show}")).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
    }
  }

  it should "return metadata if campaign has it" in {
    val (id, request) = createCampaignWithUpdateOk(arbitrary[CreateCampaign].retryUntil(_.metadata.nonEmpty))
    val result = getCampaignOk(id)

    result.metadata shouldBe request.metadata.get
  }

  it should "return error when metadata is duplicated in request" in {
    val updateId = createUpdateOk(arbitrary[CreateUpdate].generate)
    val metadata = Seq(CreateCampaignMetadata(MetadataType.DESCRIPTION, "desc"), CreateCampaignMetadata(MetadataType.DESCRIPTION, "desc 2"))
    val createRequest = arbitrary[CreateCampaign].map(_.copy(update = updateId, metadata = Some(metadata))).generate
    fakeRegistry.setGroup(createRequest.groups.head, Seq(arbitrary[DeviceId].generate))

    Post(apiUri("campaigns"), createRequest).withHeaders(header) ~> routes ~> check {
      status shouldBe Conflict
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.ConflictingMetadata
    }
  }

  "GET all campaigns" should "return only campaigns matching campaign filter" in {
    val request = arbitrary[CreateCampaign].generate
    val (id, _) = createCampaignWithUpdateOk(request)

    getCampaignsOk(CampaignStatus.prepared.some).values should contain(id)

    getCampaignsOk(CampaignStatus.launched.some).values shouldNot contain(id)
  }

  "GET all campaigns sorted by name" should "get all campaigns sorted alphabetically by name" in {
    val requests = Gen.listOfN(10, genCreateCampaign(Gen.alphaNumStr.retryUntil(_.nonEmpty))).generate
    val sortedNames = requests.map(_.name).sortBy(_.toLowerCase)
    requests.map(createCampaignWithUpdateOk(_))

    val campaignNames = getCampaignsOk(sortBy = Some(SortBy.Name)).values.map(getCampaignOk).map(_.name).filter(sortedNames.contains)
    campaignNames shouldBe sortedNames
  }

  "GET all campaigns" should "get all campaigns sorted from newest to oldest when no sorting is given" in {
    val requests = Gen.listOfN(10, genCreateCampaign()).generate
    requests.map(createCampaignWithUpdateOk(_))

    val campaignsNewestToOldest = getCampaignsOk().values.map(getCampaignOk)
    campaignsNewestToOldest.reverse.map(_.createdAt) shouldBe sorted
  }

  "GET campaigns filtered by name" should "get only the campaigns that contain the filter parameter" in {
    val names = Seq("aabb", "baaxbc", "a123ba", "cba3b")
    val campaignsIdNames = names
      .map(Gen.const)
      .map(genCreateCampaign(_))
      .map(createCampaignWithUpdateOk(_))
      .map(_._1)
      .map(getCampaignOk)
      .map(c => c.id -> c.name)
      .toMap

    val tests = Map("" -> names, "a1" -> Seq("a123ba"), "aa" -> Seq("aabb", "baaxbc"), "3b" -> Seq("a123ba", "cba3b"), "3" -> Seq("a123ba", "cba3b"))

    tests.foreach{ case (nameContains, expected) =>
      val resultIds = getCampaignsOk(nameContains = Some(nameContains)).values.filter(campaignsIdNames.keySet.contains)
      val resultNames = campaignsIdNames.filterKeys(resultIds.contains).values
      resultNames.size shouldBe expected.size
      resultNames should contain allElementsOf expected
    }
  }

  "GET /campaigns?withErrors=true" should "return only campaigns with errors" in {
    forAll { campaignCases: Seq[CampaignCase] =>

      val campaignIdsAndCases = campaignCases.map { cc =>
        val (cid, _) = createCampaignWithUpdateOk(
          genCreateCampaign(genGroupId = Gen.const(NonEmptyList.one(cc.groupId))),
          Gen.const(cc.affectedDevices ++ cc.notAffectedDevices)
        )
        cid -> cc
      }

      val failureCode = ResultCode("failure-code")
      campaignIdsAndCases.foreach { case (cid, cc) =>
        campaigns
          .launch(cid)
          .flatMap(_ => conductCampaign(cid, cc, failureCode))
          .futureValue
      }

      getCampaigns(withErrors = Some(true)) ~> routes ~> check {
        status shouldBe OK
        val cids = responseAs[PaginationResult[Campaign]].values.map(_.id)
        cids should contain allElementsOf campaignIdsAndCases.filter(_._2.failedCount > 0).map(_._1)
        cids should contain noElementsOf campaignIdsAndCases.filter(_._2.failedCount == 0).map(_._1)
      }
    }
  }

  "GET /campaigns?withErrors=false" should "fail with a BadRequest" in {
    getCampaigns(withErrors = Some(false)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("only value 'true' is supported for parameter 'withErrors'")
    }
  }

  "GET /campaigns?status=<string>&withErrors=<string>" should "fail with a BadRequest" in {
    getCampaigns(campaignStatus = Some(CampaignStatus.prepared), withErrors = Some(true)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("'status' must be empty when searching by 'withErrors'")
    }
  }

  "GET /campaigns?nameContains=<string>&withErrors=<string>" should "fail with a BadRequest" in {
    getCampaigns(nameContains = Gen.some(Gen.alphaNumStr).generate, withErrors = Some(true)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("'nameContains' must be empty when searching by 'withErrors'")
    }
  }

  "GET /campaigns?limit=<negativeLong>" should "fail with a BadRequest" in {
    getCampaigns(limit = Some(-1)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("The query parameter 'limit' was malformed")
    }
  }

  "GET /campaigns?offset=<negativeLong>" should "fail with a BadRequest" in {
    getCampaigns(offset = Some(-1)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("The query parameter 'offset' was malformed")
    }
  }

  "GET /campaigns/:id/failed-installations.csv" should "export the failed installations in a CSV" in {

    def assertCsvResponse(expected: Seq[String]): Unit = {
      status shouldBe OK
      contentType shouldBe ContentTypes.`text/csv(UTF-8)`
      val result = entityAs[ByteString].utf8String.split("\n")
      result.tail should contain allElementsOf expected
    }

    @tailrec
    def failInstallationsAndCheckExport(campaignId: CampaignId, testCase: Seq[(DeviceId, String, ResultCode, ResultDescription)]): Unit = testCase match {
      case Nil => ()

      case succeeded :: failedHead :: failedTail =>
        val failed = failedHead :: failedTail
        campaigns
          .succeedDevices(campaignId, succeeded._1 :: Nil, ResultCode("SUCCESS"), ResultDescription("EUREKA"))
          .flatMap(_ => campaigns.failDevices(campaignId, failed.map(_._1), failedHead._3, failedHead._4))
          .futureValue

        getFailedExport(campaignId, failedHead._3) ~> routes ~> check {
          val expected = failed.map(_._2).tail.map(oemId => s"$oemId;${failedHead._3.value};${failedHead._4.value}")
          assertCsvResponse(expected)
        }

        failInstallationsAndCheckExport(campaignId, failed)

      case succeeded :: _ =>
        campaigns.succeedDevices(campaignId, succeeded._1 :: Nil, ResultCode("SUCCESS"), ResultDescription("EUREKA")).futureValue
    }

    val testCase = Gen.listOfN(20, genExportCase).generate
    val (campaignId, _) = createCampaignWithUpdateOk()

    fakeRegistry.setOemIds(testCase.map(_._1).zip(testCase.map(_._2)): _*)
    campaigns.scheduleDevices(campaignId, testCase.map(_._1)).futureValue

    failInstallationsAndCheckExport(campaignId, testCase)
  }

  "GET /campaigns/:id/stats" should "return correct statistics" in {
    forAll(genCampaignCase) { mainCase =>
      whenever(mainCase.processedCount > 0) {
        val (mainCampaignId, _) = createCampaignWithUpdateOk(
          genCreateCampaign().map(_.copy(groups = NonEmptyList.one(mainCase.groupId))),
          Gen.const(mainCase.affectedDevices ++ mainCase.notAffectedDevices)
        )
        val mainCampaignFailureCode = ResultCode("failure-code-main")
        campaigns
          .launch(mainCampaignId)
          .flatMap(_ => conductCampaign(mainCampaignId, mainCase, mainCampaignFailureCode))
          .futureValue

        Get(apiUri(s"campaigns/${mainCampaignId.show}/stats")).withHeaders(header) ~> routes ~> check {
          status shouldBe OK

          val campaignStats = responseAs[CampaignStats]
          campaignStats.status shouldBe CampaignStatus.finished
          campaignStats.finished shouldBe (mainCase.successfulDevices.size + mainCase.failedCount)
          campaignStats.failed shouldBe mainCase.failedCount
          campaignStats.cancelled shouldBe mainCase.cancelledDevices.size
          campaignStats.processed shouldBe mainCase.processedCount
          campaignStats.affected shouldBe mainCase.affectedCount

          if (mainCase.failedDevices.nonEmpty) {
            campaignStats.failures shouldBe Set(CampaignFailureStats(
              code = mainCampaignFailureCode,
              count = mainCase.failedCount,
              retryStatus = RetryStatus.not_launched
            ))
          }
        }

        if (mainCase.failedDevices.nonEmpty) {
          val Seq(retriedS, retriedF, retriedC, notAffectedDevices) =
            splitIntoGroupsRandomly(mainCase.failedDevices, 4)
          val retryCase = CampaignCase(
            successfulDevices = retriedS,
            failedDevices = retriedF,
            cancelledDevices = retriedC,
            notAffectedDevices = notAffectedDevices
          )

          val retryCampaignFailureCode = ResultCode("failure-code-retry")
          val retryCampaignId = createAndLaunchRetryCampaignOk(mainCampaignId, RetryFailedDevices(mainCampaignFailureCode))
          conductCampaign(retryCampaignId, retryCase, retryCampaignFailureCode).futureValue

          Get(apiUri(s"campaigns/${mainCampaignId.show}/stats")).withHeaders(header) ~> routes ~> check {
            status shouldBe OK

            val campaignStats = responseAs[CampaignStats]
            campaignStats.status shouldBe CampaignStatus.finished
            campaignStats.finished shouldBe (
              mainCase.successfulDevices.size + mainCase.failedDevices.size -
                (retryCase.processedCount - retryCase.affectedCount + retryCase.cancelledDevices.size)
              )
            campaignStats.cancelled shouldBe (mainCase.cancelledDevices.size + retryCase.cancelledDevices.size)
            campaignStats.processed shouldBe mainCase.processedCount
            campaignStats.affected shouldBe (
              mainCase.affectedCount - (retryCase.processedCount - retryCase.affectedCount)
              )
            campaignStats.failed shouldBe (
              mainCase.failedCount - retryCase.processedCount + retryCase.failedCount
              )

            if (retryCase.failedDevices.nonEmpty) {
              campaignStats.failures shouldBe Set(CampaignFailureStats(
                code = retryCampaignFailureCode,
                count = retryCase.failedCount,
                retryStatus = RetryStatus.not_launched,
              ))
            }
          }
        }
      }
    }
  }

  private def splitIntoGroupsRandomly[T](seq: Seq[T], n: Int): Seq[Seq[T]] = {
    val elemsWithGroupNumAssigned = seq.groupBy(_ => Gen.choose(0, n - 1).generate)
    0.until(n).map(groupNum => elemsWithGroupNumAssigned.getOrElse(groupNum, Seq.empty))
  }

  private def assertRetryCampaign(retryCampaign: Eval[GetCampaign], failureCode: ResultCode, updateId: UpdateId, mainCampaignId: CampaignId): Assertion = {
    eventually(timeout(20 seconds)) {
      val retry = retryCampaign.value

      retry shouldBe GetCampaign(
        testNs,
        retry.id,
        s"retryCampaignWith-mainCampaign-${mainCampaignId.uuid}-failureCode-$failureCode",
        updateId,
        CampaignStatus.launched,
        retry.createdAt,
        retry.updatedAt,
        Some(mainCampaignId),
        Set.empty,
        Nil,
        autoAccept = true
      )
      ChronoUnit.SECONDS.between(retry.createdAt, retry.updatedAt) should be <= Duration.ofSeconds(2).getSeconds
    }
  }

}
