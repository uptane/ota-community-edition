/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.Uri.Query
import org.apache.pekko.http.scaladsl.server.Route
import cats.instances.int.*
import cats.instances.string.*
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.director.db.deviceregistry.SystemInfoRepository.NetworkInfo
import com.advancedtelematic.director.deviceregistry.data.*
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.DataType.InstallationStatsLevel.InstallationStatsLevel
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceT,
  SetDevice,
  TagInfo,
  UpdateDevice,
  UpdateTagValue
}
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.director.http.deviceregistry.TomlSupport.`application/toml`
import com.advancedtelematic.director.util.{DefaultPatience, ResourceSpec}
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.http.HttpOps.*
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.Json
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, OffsetDateTime}

// Default patience required here so the RouteTestTimeout implicit defined in DefaultPatience has priority over the
// one defined by pekko-testkit
trait RegistryDeviceRequests { self: DefaultPatience & ResourceSpec & Matchers =>

  import StatusCodes.*
  import com.advancedtelematic.director.deviceregistry.data.Device.*

  val api = "devices"

  def fetchDevice(uuid: DeviceId): HttpRequest =
    Get(DeviceRegistryResourceUri.uri(api, uuid.show))

  def fetchDeviceOk(uuid: DeviceId): Device =
    Get(DeviceRegistryResourceUri.uri(api, uuid.show)) ~> routes ~> check {
      status shouldBe OK
      responseAs[Device]
    }

  def fetchDeviceInNamespaceOk(uuid: DeviceId, namespace: Namespace): Device =
    Get(DeviceRegistryResourceUri.uri(api, uuid.show)).withNs(namespace) ~> routes ~> check {
      status shouldBe OK
      responseAs[Device]
    }

  def filterDevices(status: Option[DeviceStatus] = None,
                    hibernated: Option[Boolean] = None,
                    activatedAfter: Option[Instant] = None,
                    activatedBefore: Option[Instant] = None,
                    lastSeenStart: Option[Instant] = None,
                    lastSeenEnd: Option[Instant] = None,
                    createdAtStart: Option[Instant] = None,
                    createdAtEnd: Option[Instant] = None,
                    hardwareIds: Seq[HardwareIdentifier] = Seq.empty,
                    namespace: Namespace = defaultNs): HttpRequest = {
    val m = Seq(
      status.map("status" -> _.toString),
      hibernated.map("hibernated" -> _.toString),
      activatedBefore.map("activatedBefore" -> _.toString),
      activatedAfter.map("activatedAfter" -> _.toString),
      lastSeenStart.map("lastSeenStart" -> _.toString),
      lastSeenEnd.map("lastSeenEnd" -> _.toString),
      createdAtStart.map("createdAtStart" -> _.toString),
      createdAtEnd.map("createdAtEnd" -> _.toString)
    ) :+ (hardwareIds match {
      case x if x.nonEmpty => Option("hardwareIds" -> hardwareIds.map(_.value).mkString(","))
      case _               => None
    })

    Get(DeviceRegistryResourceUri.uri(api).withQuery(Query(m.flatten.toMap))).withNs(namespace)
  }

  def fetchByDeviceId(deviceId: DeviceOemId,
                      nameContains: Option[String] = None,
                      groupId: Option[GroupId] = None,
                      notSeenSinceHours: Option[Int] = None): HttpRequest = {
    val m = Seq(
      deviceId.some.map("deviceId" -> _.show),
      nameContains.map("nameContains" -> _.show),
      groupId.map("groupId" -> _.show),
      notSeenSinceHours.map("notSeenSinceHours" -> _.show)
    ).collect { case Some(a) => a }
    Get(DeviceRegistryResourceUri.uri(api).withQuery(Query(m.toMap)))
  }

  def fetchByGroupId(groupId: GroupId, offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri(api)
        .withQuery(
          Query("groupId" -> groupId.show, "offset" -> offset.toString, "limit" -> limit.toString)
        )
    )

  def fetchUngrouped(offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri(api)
        .withQuery(
          Query("grouped" -> "false", "offset" -> offset.toString, "limit" -> limit.toString)
        )
    )

  def fetchNotSeenSince(hours: Int): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri(api)
        .withQuery(Query("notSeenSinceHours" -> hours.toString, "limit" -> 1000.toString))
    )

  def setDevice(uuid: DeviceId, newName: DeviceName, notes: Option[String] = None): HttpRequest =
    Put(DeviceRegistryResourceUri.uri(api, uuid.show), SetDevice(newName, notes))

  def updateDevice(uuid: DeviceId,
                   newName: Option[DeviceName],
                   notes: Option[String] = None): HttpRequest =
    Patch(DeviceRegistryResourceUri.uri(api, uuid.show), UpdateDevice(newName, notes))

  def createDevice(device: DeviceT): HttpRequest =
    Post(DeviceRegistryResourceUri.uri(api), device)

  def createDeviceOk(device: DeviceT): DeviceId =
    createDevice(device) ~> routes ~> check {
      status shouldBe Created
      responseAs[DeviceId]
    }

  def createDeviceInNamespaceOk(device: DeviceT, ns: Namespace): DeviceId =
    Post(DeviceRegistryResourceUri.uri(api), device).withNs(ns) ~> routes ~> check {
      status shouldBe Created
      responseAs[DeviceId]
    }

  def deleteDevice(uuid: DeviceId): HttpRequest =
    Delete(DeviceRegistryResourceUri.uri(api, uuid.show))

  def fetchSystemInfo(uuid: DeviceId): HttpRequest =
    Get(DeviceRegistryResourceUri.uri(api, uuid.show, "system_info"))

  def createSystemInfo(uuid: DeviceId, json: Json): HttpRequest =
    Post(DeviceRegistryResourceUri.uri(api, uuid.show, "system_info"), json)

  def updateSystemInfo(uuid: DeviceId, json: Json): HttpRequest =
    Put(DeviceRegistryResourceUri.uri(api, uuid.show, "system_info"), json)

  def fetchNetworkInfo(uuid: DeviceId): HttpRequest = {
    val uri = DeviceRegistryResourceUri.uri(api, uuid.show, "system_info", "network")
    Get(uri)
  }

  def createNetworkInfo(uuid: DeviceId, networkInfo: NetworkInfo): HttpRequest = {
    val uri = DeviceRegistryResourceUri.uri(api, uuid.show, "system_info", "network")
    import com.advancedtelematic.director.db.deviceregistry.SystemInfoRepository.networkInfoWithDeviceIdEncoder
    Put(uri, networkInfo)
  }

  def postListNetworkInfos(uuids: Seq[DeviceId]): HttpRequest = {
    val uri = DeviceRegistryResourceUri.uri(api, "list-network-info")
    Post(uri, uuids)
  }

  def uploadSystemConfig(uuid: DeviceId, config: String): HttpRequest =
    Post(DeviceRegistryResourceUri.uri(api, uuid.show, "system_info", "config"))
      .withEntity(`application/toml`, config)

  def listGroupsForDevice(device: DeviceId): HttpRequest =
    Get(DeviceRegistryResourceUri.uri(api, device.show, "groups"))

  def installSoftware(device: DeviceId, packages: Set[PackageId]): HttpRequest =
    Put(DeviceRegistryResourceUri.uri("mydevice", device.show, "packages"), packages)

  def installSoftwareOk(device: DeviceId, packages: Set[PackageId])(implicit route: Route): Unit =
    installSoftware(device, packages) ~> route ~> check {
      status shouldBe StatusCodes.NoContent
    }

  def listPackages(device: DeviceId, nameContains: Option[String] = None): HttpRequest = {
    val uri = DeviceRegistryResourceUri.uri("devices", device.show, "packages")
    nameContains match {
      case None    => Get(uri)
      case Some(s) => Get(uri.withQuery(Query("nameContains" -> s)))
    }
  }

  def getStatsForPackage(pkg: PackageId): HttpRequest =
    Get(DeviceRegistryResourceUri.uri("device_count", pkg.name, pkg.version))

  def getActiveDeviceCount(start: OffsetDateTime, end: OffsetDateTime): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri("active_device_count")
        .withQuery(Query("start" -> start.show, "end" -> end.show))
    )

  def getInstalledForAllDevices(offset: Long = 0, limit: Long = 50): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri("device_packages")
        .withQuery(Query("offset" -> offset.toString, "limit" -> limit.toString))
    )

  def getAffected(pkgs: Set[PackageId]): HttpRequest =
    Post(DeviceRegistryResourceUri.uri("device_packages", "affected"), pkgs)

  def getPackageStats(name: PackageId.Name): HttpRequest =
    Get(DeviceRegistryResourceUri.uri("device_packages", name))

  def countDevicesForExpression(expression: Option[GroupExpression]): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri(api, "dynamic-group-count")
        .withQuery(Query(expression.map("expression" -> _.value).toMap))
    )

  def getEvents(deviceUuid: DeviceId, correlationId: Option[CorrelationId] = None): HttpRequest = {
    val query = Query(correlationId.map("correlationId" -> _.toString).toMap)
    Get(DeviceRegistryResourceUri.uri(api, deviceUuid.show, "events").withQuery(query))
  }

  def getEventsV2(deviceUuid: DeviceId, TargetSpecId: Option[CorrelationId] = None): HttpRequest = {
    val query = Query(TargetSpecId.map("TargetSpecId" -> _.toString).toMap)
    Get(DeviceRegistryResourceUri.uriV2(api, deviceUuid.show, "events").withQuery(query))
  }

  def getGroupsOfDevice(deviceUuid: DeviceId): HttpRequest = Get(
    DeviceRegistryResourceUri.uri(api, deviceUuid.show, "groups")
  )

  def getDevicesByGrouping(grouped: Boolean,
                           groupType: Option[GroupType],
                           nameContains: Option[String] = None,
                           limit: Long = 2000): HttpRequest = {
    val m = Map("grouped" -> grouped, "limit" -> limit) ++
      List("groupType" -> groupType, "nameContains" -> nameContains).collect { case (k, Some(v)) =>
        k -> v
      }.toMap
    Get(DeviceRegistryResourceUri.uri(api).withQuery(Query(m.view.mapValues(_.toString).toMap)))
  }

  def getStats(correlationId: CorrelationId, level: InstallationStatsLevel): HttpRequest =
    Get(
      DeviceRegistryResourceUri
        .uri(api, "stats")
        .withQuery(Query("correlationId" -> correlationId.toString, "level" -> level.toString))
    )

  def getFailedExport(correlationId: CorrelationId, failureCode: Option[String]): HttpRequest = {
    val m = Map("correlationId" -> correlationId.toString)
    val params = failureCode.fold(m)(fc => m + ("failureCode" -> fc))
    Get(DeviceRegistryResourceUri.uri(api, "failed-installations.csv").withQuery(Query(params)))
  }

  def getReportBlob(deviceId: DeviceId): HttpRequest =
    Get(DeviceRegistryResourceUri.uri(api, deviceId.show, "installation_history"))

  def getInstallationReports(deviceId: DeviceId): HttpRequest =
    Get(DeviceRegistryResourceUri.uri(api, deviceId.show, "installation_reports"))

  def postDeviceTags(tags: Seq[Seq[String]],
                     headers: Seq[String] = Seq("DeviceID", "market", "trim"),
                     ns: Namespace = defaultNs): HttpRequest = {
    require(tags.map(_.length == headers.length).reduce(_ && _))

    val csv = (headers +: tags).map(_.mkString(";")).mkString("\n")

    println(csv)


    val multipartForm = Multipart.FormData(
      Multipart.FormData.BodyPart.Strict(
        "custom-device-fields",
        HttpEntity(ContentTypes.`text/csv(UTF-8)`, csv),
        Map("filename" -> "test-custom-fields.csv")
      )
    )
    Post(DeviceRegistryResourceUri.uri("device_tags"), multipartForm).withNs(ns)
  }

  def postDeviceTagsOk(tags: Seq[Seq[String]]): Unit =
    postDeviceTags(tags) ~> routes ~> check {
      status shouldBe NoContent
      ()
    }

  def getDeviceTagsOk: Seq[TagId] =
    Get(DeviceRegistryResourceUri.uri("device_tags")) ~> routes ~> check {
      status shouldBe OK
      responseAs[Seq[TagInfo]].map(_.tagId)
    }

  def updateDeviceTagOk(deviceId: DeviceId, tagId: TagId, tagValue: String): Seq[(String, String)] =
    Patch(
      DeviceRegistryResourceUri.uri(api, deviceId.show, "device_tags"),
      UpdateTagValue(tagId, tagValue)
    ) ~> routes ~> check {
      status shouldBe OK
      responseAs[Seq[(String, String)]]
    }

  def deleteDeviceTagOk(tagId: TagId): Unit =
    Delete(DeviceRegistryResourceUri.uri("device_tags", tagId.value)) ~> routes ~> check {
      status shouldBe OK
      ()
    }

}
