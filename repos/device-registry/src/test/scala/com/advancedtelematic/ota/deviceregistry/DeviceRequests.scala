/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import java.time.OffsetDateTime

import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, Multipart, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import cats.instances.int._
import cats.instances.string._
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.http.HttpOps.HttpRequestOps
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.ota.deviceregistry.data.Codecs._
import com.advancedtelematic.ota.deviceregistry.data.DataType.InstallationStatsLevel.InstallationStatsLevel
import com.advancedtelematic.ota.deviceregistry.data.DataType.{DeviceT, DeviceUuids, TagInfo, UpdateDevice, UpdateTagValue}
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import com.advancedtelematic.ota.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.ota.deviceregistry.data.SortBy.SortBy
import com.advancedtelematic.ota.deviceregistry.data.{Device, DeviceName, GroupExpression, PackageId, TagId}
import com.advancedtelematic.ota.deviceregistry.http.`application/toml`
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json

import scala.concurrent.ExecutionContext

/**
  * Generic test resource object
  * Used in property-based testing
  */
object Resource {
  def uri(pathSuffixes: String*): Uri = {
    val BasePath = Path("/api") / "v1"
    Uri.Empty.withPath(pathSuffixes.foldLeft(BasePath)(_ / _))
  }

  def uriV2(pathSuffixes: String*): Uri = {
    val BasePath = Path("/api") / "v2"
    Uri.Empty.withPath(pathSuffixes.foldLeft(BasePath)(_ / _))
  }
}

/**
  * Testing Trait for building Device requests
  */
trait DeviceRequests { self: ResourceSpec =>

  import StatusCodes._
  import com.advancedtelematic.ota.deviceregistry.data.Device._

  val api = "devices"

  def fetchDevice(uuid: DeviceId): HttpRequest =
    Get(Resource.uri(api, uuid.show))

  def fetchDeviceOk(uuid: DeviceId): Device =
    Get(Resource.uri(api, uuid.show)) ~> route ~> check {
      status shouldBe OK
      responseAs[Device]
    }

  def listDevices(sortBy: Option[SortBy] = None): HttpRequest = {
    val m = sortBy.fold(Map.empty[String, String])(s => Map("sortBy" -> s.toString))
    Get(Resource.uri(api).withQuery(Query(m)))
  }

  def listDevicesByUuids(deviceUuids: Seq[DeviceId], sortBy: Option[SortBy] = None): HttpRequest = {
    val m = sortBy.fold(Map.empty[String, String])(s => Map("sortBy" -> s.toString))
    Get(Resource.uri(api).withQuery(Query(m)), DeviceUuids(deviceUuids))
  }

  def searchDevice(regex: String, offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      Resource
        .uri(api)
        .withQuery(Query("regex" -> regex, "offset" -> offset.toString, "limit" -> limit.toString))
    )

  def fetchByDeviceId(deviceId: DeviceOemId,
                      nameContains: Option[String] = None,
                      groupId: Option[GroupId] = None,
                      notSeenSinceHours: Option[Int] = None,
                     ): HttpRequest = {
    val m = Seq(
      deviceId.some.map("deviceId" -> _.show),
      nameContains.map("nameContains" -> _.show),
      groupId.map("groupId" -> _.show),
      notSeenSinceHours.map("notSeenSinceHours" -> _.show),
    ).collect { case Some(a) => a }
    Get(Resource.uri(api).withQuery(Query(m.toMap)))
  }

  def fetchByGroupId(groupId: GroupId, offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      Resource
        .uri(api)
        .withQuery(
          Query("groupId" -> groupId.show, "offset" -> offset.toString, "limit" -> limit.toString)
        )
    )

  def fetchUngrouped(offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      Resource
        .uri(api)
        .withQuery(
          Query("grouped" -> "false", "offset" -> offset.toString, "limit" -> limit.toString)
        )
    )

  def fetchNotSeenSince(hours: Int): HttpRequest =
    Get(Resource.uri(api).withQuery(Query("notSeenSinceHours" -> hours.toString, "limit" -> 1000.toString)))

  def updateDevice(uuid: DeviceId, newName: DeviceName)(implicit ec: ExecutionContext): HttpRequest =
    Put(Resource.uri(api, uuid.show), UpdateDevice(newName))

  def createDevice(device: DeviceT)(implicit ec: ExecutionContext): HttpRequest =
    Post(Resource.uri(api), device)

  def createDeviceOk(device: DeviceT)(implicit ec: ExecutionContext): DeviceId =
    createDevice(device) ~> route ~> check {
      status shouldBe Created
      responseAs[DeviceId]
  }

  def createDeviceInNamespaceOk(device: DeviceT, ns: Namespace)(implicit ec: ExecutionContext): DeviceId =
    Post(Resource.uri(api), device).withNs(ns) ~> route ~> check {
      status shouldBe Created
      responseAs[DeviceId]
    }

  def deleteDevice(uuid: DeviceId)(implicit ec: ExecutionContext): HttpRequest =
    Delete(Resource.uri(api, uuid.show))

  def fetchSystemInfo(uuid: DeviceId): HttpRequest =
    Get(Resource.uri(api, uuid.show, "system_info"))

  def createSystemInfo(uuid: DeviceId, json: Json)(implicit ec: ExecutionContext): HttpRequest =
    Post(Resource.uri(api, uuid.show, "system_info"), json)

  def updateSystemInfo(uuid: DeviceId, json: Json)(implicit ec: ExecutionContext): HttpRequest =
    Put(Resource.uri(api, uuid.show, "system_info"), json)

  def fetchNetworkInfo(uuid: DeviceId)(implicit ec: ExecutionContext): HttpRequest = {
    val uri = Resource.uri(api, uuid.show, "system_info", "network")
    Get(uri)
  }

  def uploadSystemConfig(uuid: DeviceId, config: String): HttpRequest =
    Post(Resource.uri(api, uuid.show, "system_info", "config")).withEntity(`application/toml`, config)

  def listGroupsForDevice(device: DeviceId)(implicit ec: ExecutionContext): HttpRequest =
    Get(Resource.uri(api, device.show, "groups"))

  def installSoftware(device: DeviceId, packages: Set[PackageId]): HttpRequest =
    Put(Resource.uri("mydevice", device.show, "packages"), packages)

  def installSoftwareOk(device: DeviceId, packages: Set[PackageId])(implicit route: Route): Unit =
    installSoftware(device, packages) ~> route ~> check {
      status shouldBe StatusCodes.NoContent
    }

  def listPackages(device: DeviceId, nameContains: Option[String] = None)(implicit ec: ExecutionContext): HttpRequest = {
    val uri = Resource.uri("devices", device.show, "packages")
    nameContains match {
      case None => Get(uri)
      case Some(s) => Get(uri.withQuery(Query("nameContains" -> s)))
    }
  }

  def getStatsForPackage(pkg: PackageId)(implicit ec: ExecutionContext): HttpRequest =
    Get(Resource.uri("device_count", pkg.name, pkg.version))

  def getActiveDeviceCount(start: OffsetDateTime, end: OffsetDateTime): HttpRequest =
    Get(
      Resource.uri("active_device_count").withQuery(Query("start" -> start.show, "end" -> end.show))
    )

  def getInstalledForAllDevices(offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      Resource
        .uri("device_packages")
        .withQuery(Query("offset" -> offset.toString, "limit" -> limit.toString))
    )

  def getAffected(pkgs: Set[PackageId]): HttpRequest =
    Post(Resource.uri("device_packages", "affected"), pkgs)

  def getPackageStats(name: PackageId.Name): HttpRequest =
    Get(Resource.uri("device_packages", name))

  def countDevicesForExpression(expression: Option[GroupExpression]): HttpRequest =
    Get(Resource.uri(api, "count").withQuery(Query(expression.map("expression" -> _.value).toMap)))

  def getEvents(deviceUuid: DeviceId, correlationId: Option[CorrelationId] = None): HttpRequest = {
    val query = Query(correlationId.map("correlationId" -> _.toString).toMap)
    Get(Resource.uri(api, deviceUuid.show, "events").withQuery(query))
  }

  def getEventsV2(deviceUuid: DeviceId, updateId: Option[CorrelationId] = None): HttpRequest = {
    val query = Query(updateId.map("updateId" -> _.toString).toMap)
    Get(Resource.uriV2(api, deviceUuid.show, "events").withQuery(query))
  }

  def getGroupsOfDevice(deviceUuid: DeviceId): HttpRequest = Get(Resource.uri(api, deviceUuid.show, "groups"))

  def getDevicesByGrouping(grouped: Boolean, groupType: Option[GroupType],
                           nameContains: Option[String] = None, limit: Long = 2000): HttpRequest = {
    val m = Map("grouped" -> grouped, "limit" -> limit) ++
      List("groupType" -> groupType, "nameContains" -> nameContains).collect { case (k, Some(v)) => k -> v }.toMap
    Get(Resource.uri(api).withQuery(Query(m.mapValues(_.toString))))
  }

  def getStats(correlationId: CorrelationId, level: InstallationStatsLevel): HttpRequest =
    Get(Resource.uri(api, "stats").withQuery(Query("correlationId" -> correlationId.toString, "level" -> level.toString)))

  def getFailedExport(correlationId: CorrelationId, failureCode: Option[String]): HttpRequest = {
    val m = Map("correlationId" -> correlationId.toString)
    val params = failureCode.fold(m)(fc => m + ("failureCode" -> fc))
    Get(Resource.uri(api, "failed-installations.csv").withQuery(Query(params)))
  }

  def getReportBlob(deviceId: DeviceId): HttpRequest =
    Get(Resource.uri(api, deviceId.show, "installation_history"))

  def getInstallationReports(deviceId: DeviceId): HttpRequest =
    Get(Resource.uri(api, deviceId.show, "installation_reports"))

  def postDeviceTags(tags: Seq[Seq[String]], headers: Seq[String] = Seq("DeviceID", "market", "trim")): HttpRequest = {
    require(tags.map(_.length == headers.length).reduce(_ && _))

    val csv = (headers +: tags).map(_.mkString(";")).mkString("\n")
    val multipartForm = Multipart.FormData(
      Multipart.FormData.BodyPart.Strict(
        "custom-device-fields",
        HttpEntity(ContentTypes.`text/csv(UTF-8)`, csv),
        Map("filename" -> "test-custom-fields.csv"))
    )
    Post(Resource.uri("device_tags"), multipartForm)
  }

  def postDeviceTagsOk(tags: Seq[Seq[String]]): Unit =
    postDeviceTags(tags) ~> route ~> check {
      status shouldBe NoContent
      ()
    }

  def getDeviceTagsOk: Seq[TagId] =
    Get(Resource.uri("device_tags")) ~> route ~> check {
      status shouldBe OK
      responseAs[Seq[TagInfo]].map(_.tagId)
    }

  def updateDeviceTagOk(deviceId: DeviceId, tagId: TagId, tagValue: String): Seq[(String, String)] =
    Patch(Resource.uri(api, deviceId.show, "device_tags"), UpdateTagValue(tagId, tagValue)) ~> route ~> check {
      status shouldBe OK
      responseAs[Seq[(String, String)]]
    }

  def deleteDeviceTagOk(tagId: TagId): Unit =
    Delete(Resource.uri("device_tags", tagId.value)) ~> route ~> check {
      status shouldBe OK
      ()
    }
}
