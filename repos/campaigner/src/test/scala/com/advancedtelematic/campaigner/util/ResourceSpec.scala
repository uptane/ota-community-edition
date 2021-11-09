package com.advancedtelematic.campaigner.util

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import cats.syntax.show._
import com.advancedtelematic.campaigner.data.Codecs._
import com.advancedtelematic.campaigner.data.DataType.CampaignStatus.CampaignStatus
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.Campaigns
import com.advancedtelematic.campaigner.http.Routes
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.http.HttpOps._
import com.advancedtelematic.libats.http.tracing.NullServerRequestTracing
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import org.scalatest.Suite
import org.scalatest.time.{Seconds, Span}

trait ResourceSpec extends ScalatestRouteTest
  with DatabaseSpec {
  self: Suite with CampaignerSpec =>

  implicit val tracing = new NullServerRequestTracing

  implicit val defaultTimeout: RouteTestTimeout = RouteTestTimeout(Span(5, Seconds))

  def apiUri(path: String): Uri = "/api/v2/" + path

  def testNs = Namespace("testNs")

  def header = RawHeader("x-ats-namespace", testNs.get)

  def testResolverUri = Uri("http://test.com")

  val fakeDirector = new FakeDirectorClient
  val fakeRegistry = new FakeDeviceRegistry
  val fakeUserProfile = new FakeUserProfileClient
  val fakeResolver = new FakeResolverClient

  lazy val routes: Route = new Routes(fakeRegistry, fakeResolver, fakeUserProfile, Campaigns()).routes

  def createCampaign(request: Json): HttpRequest =
    Post(apiUri("campaigns"), request).withHeaders(header)

  def createCampaign(request: CreateCampaign): HttpRequest =
    Post(apiUri("campaigns"), request).withHeaders(header)

  def createAndLaunchRetryCampaign(mainCampaignId: CampaignId, request: RetryFailedDevices): HttpRequest =
    Post(apiUri(s"campaigns/${mainCampaignId.show}/retry-failed"), request).withHeaders(header)

  def createCampaignOk(request: CreateCampaign): CampaignId =
    createCampaign(request) ~> routes ~> check {
      status shouldBe Created
      responseAs[CampaignId]
    }

  def createAndLaunchRetryCampaignOk(mainCampaignId: CampaignId, request: RetryFailedDevices): CampaignId =
    createAndLaunchRetryCampaign(mainCampaignId, request) ~> routes ~> check {
      status shouldBe Created
      responseAs[CampaignId]
    }

  def getCampaignOk(id: CampaignId): GetCampaign =
    Get(apiUri("campaigns/" + id.show)).withHeaders(header) ~> routes ~> check {
      status shouldBe OK
      responseAs[GetCampaign]
    }

  def getCampaigns(campaignStatus: Option[CampaignStatus] = None,
                   nameContains: Option[String] = None,
                   sortBy: Option[SortBy] = None,
                   withErrors: Option[Boolean] = None,
                   limit: Option[Long] = None,
                   offset: Option[Long] = None
                  ): HttpRequest = {
    val m = Seq(
      "status" -> campaignStatus,
      "nameContains" -> nameContains,
      "sortBy" -> sortBy,
      "withErrors" -> withErrors,
      "limit" -> limit,
      "offset" -> offset
    ).collect { case (k, Some(v)) => k -> v.toString }.toMap
    Get(apiUri("campaigns").withQuery(Query(m))).withHeaders(header)
  }

  def getCampaignsOk(campaignStatus: Option[CampaignStatus] = None,
                     nameContains: Option[String] = None,
                     sortBy: Option[SortBy] = None,
                     withErrors: Option[Boolean] = None,
                    ): PaginationResult[CampaignId] =
    getCampaigns(campaignStatus, nameContains, sortBy, withErrors) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[Campaign]].map(_.id)
    }

  def getFailedExport(campaignId: CampaignId, failureCode: ResultCode): HttpRequest = {
    val q = Query("failureCode" -> failureCode.value)
    Get(apiUri(s"campaigns/${campaignId.show}/failed-installations.csv").withQuery(q)).withNs(testNs)
  }
}

