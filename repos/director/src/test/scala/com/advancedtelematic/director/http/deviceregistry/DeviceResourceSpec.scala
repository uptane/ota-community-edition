/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.model.Uri.Query
import cats.syntax.either.*
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.director.daemon.{DeviceMqttLifecycle, EventType, MqttLifecycleListener}
import com.advancedtelematic.director.db.DeleteDeviceDBIO
import com.advancedtelematic.director.db.deviceregistry.InstalledPackages.{
  DevicesCount,
  InstalledPackage
}
import com.advancedtelematic.director.db.deviceregistry.{InstalledPackages, TaggedDeviceRepository}
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceStatusCounts,
  DeviceT,
  MqttStatus,
  RenameTagId,
  TagInfo,
  UpdateHibernationStatusRequest
}
import org.scalatest.OptionValues.*
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.*
import com.advancedtelematic.director.deviceregistry.data.DeviceName.validatedDeviceType
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.*
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupGenerators.*
import com.advancedtelematic.director.deviceregistry.data.PackageIdGenerators.*
import com.advancedtelematic.director.deviceregistry.data.{PackageStat, *}
import com.advancedtelematic.director.http.LogDeviceSeen
import com.advancedtelematic.director.util.{DirectorSpec, ResourceSpec}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.{ErrorCodes, ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libats.http.HttpOps.HttpRequestOps
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import io.circe.Json
import io.circe.generic.auto.*
import org.scalacheck.Arbitrary.*
import org.scalacheck.{Gen, Shrink}
import org.scalatest.EitherValues.*
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, OffsetDateTime}

class DeviceResourceSpec
    extends DirectorSpec
    with ResourceSpec
    with ResourcePropSpec
    with RegistryDeviceRequests
    with GroupRequests
    with Eventually {

  import Device.*
  import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*

  private implicit val exec: scala.concurrent.ExecutionContextExecutor = system.dispatcher

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(100, Millis))

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  def isRecent(time: Option[Instant]): Boolean = time match {
    case Some(t) => t.isAfter(Instant.now.minus(3, ChronoUnit.MINUTES))
    case None    => false
  }

  private def logDeviceSeen(uuid: DeviceId,
                            lastSeen: Instant = Instant.now(),
                            namespace: Namespace = defaultNs): Unit =
    LogDeviceSeen
      .logDevice(namespace, uuid, lastSeen)
      .recover { case Errors.MissingDevice =>
        println("could not log device seen, missing device")
      }
      .futureValue

  private def createGroupedAndUngroupedDevices(): Map[String, Seq[DeviceId]] = {
    val deviceTs = genConflictFreeDeviceTs(12).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)
    val staticGroup = createStaticGroupOk()

    deviceIds.take(4).foreach(addDeviceToGroupOk(staticGroup, _))
    val expr = deviceTs
      .slice(4, 8)
      .map(_.deviceId.underlying.take(6))
      .map(n => s"deviceid contains $n")
      .reduce(_ + " or " + _)
    createDynamicGroupOk(GroupExpression.from(expr).toOption.get)

    Map(
      "all" -> deviceIds,
      "groupedStatic" -> deviceIds.take(4),
      "groupedDynamic" -> deviceIds.slice(4, 8),
      "ungrouped" -> deviceIds.drop(8)
    )
  }

  test("GET, PUT, DELETE, and POST '/ping' request fails on non-existent device") {
    forAll { (uuid: DeviceId, device: DeviceT) =>
      fetchDevice(uuid) ~> routes ~> check(status shouldBe NotFound)
      setDevice(uuid, device.deviceName) ~> routes ~> check(status shouldBe NotFound)
      deleteDevice(uuid) ~> routes ~> check(status shouldBe NotFound)
    }
  }

  test("uses correct codec for device") {
    import org.scalatest.EitherValues.*
    import org.scalatest.OptionValues.*

    forAll { (dt1: DeviceT) =>
      val d1 = createDeviceInNamespaceOk(dt1, defaultNs)

      Get(DeviceRegistryResourceUri.uri(api)) ~> routes ~> check {
        status shouldBe OK
        val devicesJson = responseAs[PaginationResult[Json]].values

        val createdDevice = devicesJson.find { d =>
          d.hcursor.downField("uuid").as[DeviceId].value == d1
        }.value

        val createdAt = createdDevice.hcursor.downField("createdAt").as[String].value

        val expected =
          s"""
            |{
            |  "namespace" : "default",
            |  "uuid" : "${d1.uuid.toString}",
            |  "deviceName" : "${dt1.deviceName.value}",
            |  "deviceId" : "${dt1.deviceId.underlying}",
            |  "deviceType" : "${dt1.deviceType.toString}",
            |  "lastSeen" : null,
            |  "createdAt" : "$createdAt",
            |  "activatedAt" : null,
            |  "deviceStatus" : "NotSeen",
            |  "notes" : null,
            |  "hibernated" : false,
            |  "mqttStatus" : "NotSeen",
            |  "mqttLastSeen" : null,
            |  "attributes": {}
            |}
            |""".stripMargin

        createdDevice shouldBe io.circe.parser.parse(expected).value
      }
    }
  }

  test("GET request (for Id) after POST yields same device") {
    forAll { (devicePre: DeviceT) =>
      val uuid: DeviceId = createDeviceOk(devicePre)

      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val devicePost: Device = responseAs[Device]
        devicePost.deviceId shouldBe devicePre.deviceId
        devicePost.deviceType shouldBe devicePre.deviceType
        devicePost.lastSeen shouldBe None
      }
    }
  }

  test("GET request with ?deviceId after creating yields same device.") {
    forAll { (deviceId: DeviceOemId, devicePre: DeviceT) =>
      val uuid = createDeviceOk(devicePre.copy(deviceId = deviceId))
      fetchByDeviceId(deviceId) ~> routes ~> check {
        status shouldBe OK
        val devicePost1: Device = responseAs[PaginationResult[Device]].values.head
        fetchDevice(uuid) ~> routes ~> check {
          status shouldBe OK
          val devicePost2: Device = responseAs[Device]

          devicePost1 shouldBe devicePost2
        }
      }
    }
  }

  test("GET devices not seen for the last hours") {
    forAll(sizeRange(20)) {
      (neverSeen: Seq[DeviceT], notSeenLately: Seq[DeviceT], seenLately: Seq[DeviceT]) =>
        val neverSeenIds = neverSeen.map(createDeviceOk)
        val notSeenLatelyIds = notSeenLately.map(createDeviceOk)
        val seenLatelyIds = seenLately.map(createDeviceOk)

        seenLatelyIds.foreach(logDeviceSeen(_))
        val hours = Gen.chooseNum(1, 100000).sample.get
        notSeenLatelyIds.foreach { did =>
          val i = Instant.now.minus(hours, ChronoUnit.HOURS).minusSeconds(600)
          logDeviceSeen(did, i)
        }

        fetchNotSeenSince(hours) ~> routes ~> check {
          status shouldBe OK
          val notSeenSinceHours = responseAs[PaginationResult[Device]].values
          notSeenSinceHours.map(
            _.uuid
          ) should contain allElementsOf neverSeenIds ++ notSeenLatelyIds
          notSeenSinceHours.map(_.uuid) should contain noElementsOf seenLatelyIds
        }
    }
  }

  test("PUT request after POST succeeds with updated device.") {
    forAll(genConflictFreeDeviceTs(2)) { case Seq(d1, d2) =>
      val uuid: DeviceId = createDeviceOk(d1)

      setDevice(uuid, d2.deviceName) ~> routes ~> check {
        status shouldBe OK
        fetchDevice(uuid) ~> routes ~> check {
          status shouldBe OK
          val devicePost: Device = responseAs[Device]
          devicePost.uuid shouldBe uuid
          devicePost.deviceId shouldBe d1.deviceId
          devicePost.deviceType shouldBe d1.deviceType
          devicePost.lastSeen shouldBe None
          devicePost.deviceName shouldBe d2.deviceName
        }
      }
    }
  }

  test("POST request creates a new device.") {
    forAll { (devicePre: DeviceT) =>
      val uuid = createDeviceOk(devicePre)
      devicePre.uuid.foreach(_ should equal(uuid))
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val devicePost: Device = responseAs[Device]
        devicePost.uuid shouldBe uuid
        devicePost.deviceId shouldBe devicePre.deviceId
        devicePost.deviceType shouldBe devicePre.deviceType
      }
    }
  }

  test("POST request on 'ping' should update 'lastSeen' field for device.") {
    forAll { (devicePre: DeviceT) =>
      val uuid: DeviceId = createDeviceOk(devicePre)

      logDeviceSeen(uuid)

      fetchDevice(uuid) ~> routes ~> check {
        val devicePost: Device = responseAs[Device]

        devicePost.lastSeen should not be None
        isRecent(devicePost.lastSeen) shouldBe true
        devicePost.deviceStatus should not be DeviceStatus.NotSeen
      }
    }
  }

  test("device gets mqtt status updated when lifcycle message is received") {
    val mqttListener = new MqttLifecycleListener()

    forAll { (devicePre: DeviceT) =>
      val uuid: DeviceId = createDeviceOk(devicePre)
      fetchDevice(uuid) ~> routes ~> check {
        val devicePost: Device = responseAs[Device]

        devicePost.mqttLastSeen shouldBe empty
        devicePost.mqttStatus shouldBe MqttStatus.NotSeen
      }

      // json encoders truncate dates to seconds
      val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

      mqttListener(
        DeviceMqttLifecycle(uuid, EventType.Connected, payload = Json.Null, Instant.now)
      ).futureValue

      val lastOnlineDate = fetchDevice(uuid) ~> routes ~> check {
        val devicePost: Device = responseAs[Device]

        val ts = devicePost.mqttLastSeen.value

        (ts.compareTo(now) >= 0) shouldBe true
        devicePost.mqttStatus shouldBe MqttStatus.Online
        ts
      }

      mqttListener(
        DeviceMqttLifecycle(uuid, EventType.Disconnected, payload = Json.Null, Instant.now)
      ).futureValue

      fetchDevice(uuid) ~> routes ~> check {
        val devicePost: Device = responseAs[Device]

        isRecent(devicePost.mqttLastSeen) shouldBe true
        devicePost.mqttLastSeen shouldNot be(empty)
        devicePost.mqttStatus shouldBe MqttStatus.Offline

        devicePost.mqttLastSeen.value shouldBe lastOnlineDate
      }
    }
  }

  test("POST request with same deviceName fails with conflict.") {
    forAll(genConflictFreeDeviceTs(2)) { case Seq(d1, d2) =>
      val name = arbitrary[DeviceName].sample.get
      createDeviceOk(d1.copy(deviceName = name))

      createDevice(d2.copy(deviceName = name)) ~> routes ~> check {
        status shouldBe Conflict
      }
    }
  }

  test("POST request with same deviceId fails with conflict.") {
    forAll(genConflictFreeDeviceTs(2)) { case Seq(d1, d2) =>
      createDeviceOk(d1)
      createDevice(d2.copy(deviceId = d1.deviceId)) ~> routes ~> check {
        status shouldBe Conflict
      }
    }
  }

  test("First POST request on 'ping' should update 'activatedAt' field for device.") {
    forAll { (devicePre: DeviceT) =>
      val uuid = createDeviceOk(devicePre)

      logDeviceSeen(uuid)

      fetchDevice(uuid) ~> routes ~> check {
        val firstDevice = responseAs[Device]

        val firstActivation = firstDevice.activatedAt
        firstActivation should not be None
        isRecent(firstActivation) shouldBe true

        fetchDevice(uuid) ~> routes ~> check {
          val secondDevice = responseAs[Device]

          secondDevice.activatedAt shouldBe firstActivation
        }
      }
    }
  }

  test("POST request on ping gets counted") {
    forAll { (devicePre: DeviceT) =>
      val start = OffsetDateTime.now()
      val uuid = createDeviceOk(devicePre)
      val end = start.plusHours(1)

      logDeviceSeen(uuid)

      getActiveDeviceCount(start, end) ~> routes ~> check {
        responseAs[ActiveDeviceCount].deviceCount shouldBe 1
      }
    }
  }

  test("PUT request updates device.") {
    forAll(genConflictFreeDeviceTs(2)) { case Seq(d1: DeviceT, d2: DeviceT) =>
      val uuid = createDeviceOk(d1)

      setDevice(uuid, d2.deviceName, "my notes".some) ~> routes ~> check {
        status shouldBe OK
        fetchDevice(uuid) ~> routes ~> check {
          status shouldBe OK
          val updatedDevice: Device = responseAs[Device]
          updatedDevice.deviceId shouldBe d1.deviceId
          updatedDevice.deviceType shouldBe d1.deviceType
          updatedDevice.lastSeen shouldBe None
          updatedDevice.notes should contain("my notes")
        }
      }
    }
  }

  test("unsets device notes on partial PUT") {
    val deviceT = genDeviceT.generate
    val uuid = createDeviceOk(deviceT)

    setDevice(uuid, deviceT.deviceName, notes = "some notes".some) ~> routes ~> check {
      status shouldBe OK
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val updatedDevice: Device = responseAs[Device]
        updatedDevice.notes should contain("some notes")
      }
    }

    setDevice(uuid, deviceT.deviceName, notes = None) ~> routes ~> check {
      status shouldBe OK
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val updatedDevice: Device = responseAs[Device]
        updatedDevice.notes shouldBe empty
      }
    }
  }

  test("sets device notes on partial PATCH") {
    val deviceT = genDeviceT.generate
    val uuid = createDeviceOk(deviceT)

    updateDevice(uuid, newName = None, notes = "some notes".some) ~> routes ~> check {
      status shouldBe OK
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val updatedDevice: Device = responseAs[Device]
        updatedDevice.deviceName shouldBe deviceT.deviceName
        updatedDevice.notes should contain("some notes")
      }
    }
  }

  test("sets device name on partial PATCH") {
    val deviceT = genDeviceT.generate
    val uuid = createDeviceOk(deviceT)

    updateDevice(uuid, newName = None, notes = "some notes".some) ~> routes ~> check {
      status shouldBe OK
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val updatedDevice: Device = responseAs[Device]
        updatedDevice.deviceName shouldBe deviceT.deviceName
        updatedDevice.notes should contain("some notes")
      }
    }

    updateDevice(
      uuid,
      newName = DeviceName.from("New name").toOption,
      notes = None
    ) ~> routes ~> check {
      status shouldBe OK
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val updatedDevice: Device = responseAs[Device]
        updatedDevice.deviceName shouldBe DeviceName.from("New name").toOption.get
        updatedDevice.notes should contain("some notes")
      }
    }
  }

  test("sets device name and notes on full PATCH") {
    val deviceT = genDeviceT.generate
    val uuid = createDeviceOk(deviceT)

    updateDevice(
      uuid,
      newName = DeviceName.from("myname").toOption,
      notes = "some \uD83D\uDD25 notes".some
    ) ~> routes ~> check {
      status shouldBe OK
      fetchDevice(uuid) ~> routes ~> check {
        status shouldBe OK
        val updatedDevice: Device = responseAs[Device]
        updatedDevice.deviceName shouldBe DeviceName.from("myname").toOption.get
        updatedDevice.notes should contain("some \uD83D\uDD25 notes")
      }
    }
  }

  test("PUT request does not update last seen") {
    forAll(genConflictFreeDeviceTs(2)) { case Seq(d1: DeviceT, d2: DeviceT) =>
      val uuid = createDeviceOk(d1)

      logDeviceSeen(uuid)

      setDevice(uuid, d2.deviceName) ~> routes ~> check {
        status shouldBe OK
        fetchDevice(uuid) ~> routes ~> check {
          status shouldBe OK
          val updatedDevice: Device = responseAs[Device]
          updatedDevice.lastSeen shouldBe defined
        }
      }
    }
  }

  test("PUT request with same deviceName fails with conflict.") {
    forAll(genConflictFreeDeviceTs(2)) { case Seq(d1, d2) =>
      val uuid1 = createDeviceOk(d1)
      val _ = createDeviceOk(d2)

      setDevice(uuid1, d2.deviceName) ~> routes ~> check {
        status shouldBe Conflict
      }
    }
  }

  private[this] implicit val InstalledPackageDecoderInstance
    : io.circe.Decoder[InstalledPackages.InstalledPackage] = {
    import com.advancedtelematic.libats.codecs.CirceCodecs.*
    io.circe.generic.semiauto.deriveDecoder[InstalledPackage]
  }

  test("Can install packages on a device") {
    forAll { (device: DeviceT, pkg: PackageId) =>
      val uuid = createDeviceOk(device)

      installSoftware(uuid, Set(pkg)) ~> routes ~> check {
        status shouldBe NoContent
      }

      listPackages(uuid) ~> routes ~> check {
        status shouldBe OK
        val response = responseAs[PaginationResult[InstalledPackage]]
        response.total shouldBe 1
        response.values.head.packageId shouldEqual pkg
        response.values.head.device shouldBe uuid
      }
    }
  }

  test("Can filter list of installed packages on a device") {
    val uuid = createDeviceOk(genDeviceT.generate)
    val pkgs = List(PackageId("foo", "1.0.0"), PackageId("bar", "1.0.0"))

    installSoftware(uuid, pkgs.toSet) ~> routes ~> check {
      status shouldBe NoContent
    }

    listPackages(uuid, Some("foo")) ~> routes ~> check {
      status shouldBe OK
      val response = responseAs[PaginationResult[InstalledPackage]]
      response.total shouldBe 1
      response.values.head.packageId shouldEqual pkgs.head
      response.values.head.device shouldBe uuid
    }
  }

  test("Can get stats for a package") {
    val deviceNumber = 20
    val groupNumber = 5
    val deviceTs = genConflictFreeDeviceTs(deviceNumber).sample.get
    val groups = Gen.listOfN(groupNumber, genGroupName()).sample.get
    val pkg = genPackageId.sample.get

    val deviceIds = deviceTs.map(createDeviceOk)
    val groupIds = groups.map(createStaticGroupOk)

    (0 until deviceNumber).foreach { i =>
      addDeviceToGroupOk(groupIds(i % groupNumber), deviceIds(i))
    }
    deviceIds.foreach(device => installSoftwareOk(device, Set(pkg)))

    getStatsForPackage(pkg) ~> routes ~> check {
      status shouldBe OK
      val resp = responseAs[DevicesCount]
      resp.deviceCount shouldBe deviceNumber
      // convert to sets as order isn't important
      resp.groupIds shouldBe groupIds.toSet
    }
  }

  test("searching a device by 'nameContains' and 'deviceId' fails") {
    val deviceT = genDeviceT.sample.get
    createDeviceOk(deviceT)

    fetchByDeviceId(deviceT.deviceId, Some(""), None) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include(
        "nameContains must be empty when searching by deviceId"
      )
    }
  }

  test("searching a device by 'groupId' and 'deviceId' fails") {
    val deviceT = genDeviceT.sample.get
    createDeviceOk(deviceT)

    fetchByDeviceId(deviceT.deviceId, None, Some(genStaticGroup.sample.get.id)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include(
        "groupId must be empty when searching by deviceId"
      )
    }
  }

  test("searching a device by 'notSeenSinceHours' and 'deviceId' fails") {
    val h = Gen.some(Gen.posNum[Int]).generate
    val deviceT = genDeviceT.sample.get
    createDeviceOk(deviceT)

    fetchByDeviceId(deviceT.deviceId, notSeenSinceHours = h) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include(
        "notSeenSinceHours must be empty when searching by deviceId"
      )
    }
  }

  test("can list devices by group ID") {
    val limit = 30
    val offset = 10
    val deviceNumber = 50
    val deviceTs = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)
    val groupId = createStaticGroupOk()

    deviceIds.foreach { id =>
      addDeviceToGroupOk(groupId, id)
    }

    // test that we get back all the devices
    fetchByGroupId(groupId, offset = 0, limit = deviceNumber) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]]
      devices.total shouldBe deviceNumber
      devices.values.map(_.uuid).toSet shouldBe deviceIds.toSet
    }

    // test that the limit works
    fetchByGroupId(groupId, offset = offset, limit = limit) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]]
      devices.values.length shouldBe limit
    }
  }

  test("can list ungrouped devices") {
    val deviceNumber = 50
    val deviceTs = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)

    val beforeGrouping = fetchUngrouped(offset = 0, limit = deviceNumber) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[Device]]
    }

    // add devices to group and check that we get less ungrouped devices
    val groupId = createStaticGroupOk()

    deviceIds.foreach { id =>
      addDeviceToGroupOk(groupId, id)
    }

    val afterGrouping = fetchUngrouped(offset = 0, limit = deviceNumber) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[Device]]
    }

    beforeGrouping.total shouldBe afterGrouping.total + deviceNumber
  }

  test("search by 'nameContains' is case-insensitive") {
    val originalDeviceName = genDeviceName.generate.value
    val deviceUuids =
      Seq(originalDeviceName, originalDeviceName.toLowerCase, originalDeviceName.toUpperCase)
        .map(DeviceName.from(_).toOption.get)
        .map(deviceName => genDeviceT.generate.copy(deviceName = deviceName))
        .map(createDeviceOk)

    getDevicesByGrouping(grouped = false, None, originalDeviceName.some) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result should contain allElementsOf deviceUuids
    }
  }

  test("can search for hibernated devices") {
    val ns = Namespace("hibernated_tests")

    val deviceT = genDeviceT.sample.get
    val uuid = createDeviceInNamespaceOk(deviceT, ns)

    Post(
      DeviceRegistryResourceUri.uri(api, uuid.show, "hibernation"),
      UpdateHibernationStatusRequest(true)
    )
      .withNs(ns) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val device = fetchDeviceInNamespaceOk(uuid, ns)
    device.hibernated shouldBe true

    filterDevices(hibernated = true.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values
      result.length shouldBe 1
    }

    filterDevices(hibernated = false.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values
      result shouldBe empty
    }
  }

  test("can search devices by status") {
    val ns = Namespace("status_tests")

    val deviceT = genDeviceT.sample.get
    val uuid = createDeviceInNamespaceOk(deviceT, ns)

    filterDevices(status = DeviceStatus.NotSeen.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.deviceStatus)
      result.foreach { deviceStatus =>
        deviceStatus shouldBe DeviceStatus.NotSeen
      }
    }

    logDeviceSeen(uuid, namespace = ns)

    filterDevices(status = DeviceStatus.UpToDate.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.deviceStatus)
      result.foreach { deviceStatus =>
        deviceStatus shouldBe DeviceStatus.UpToDate
      }
    }

    val otherStatuses: Seq[DeviceStatus] = Seq(NotSeen, Error, UpdatePending, Outdated)
    otherStatuses.foreach { deviceStatus =>
      filterDevices(status = deviceStatus.some, namespace = ns) ~> routes ~> check {
        status shouldBe OK
        val result = responseAs[PaginationResult[Device]].values.map(_.deviceStatus)
        result shouldBe empty
      }
    }
  }

  test("can search devices by activation time") {
    val ns = Namespace("activation_tests")

    val device1 = genDeviceT.sample.get
    val uuid1 = createDeviceInNamespaceOk(device1, ns)

    val device2 = genDeviceT.sample.get
    val uuid2 = createDeviceInNamespaceOk(device2, ns)

    val device3 = genDeviceT.sample.get
    val uuid3 = createDeviceInNamespaceOk(device3, ns)

    val device4 = genDeviceT.sample.get
    val uuid4 = createDeviceInNamespaceOk(device4, ns)

    val now = Instant.now
    val oneHourAgo = now.minus(Duration.ofHours(1))
    val twoHoursAgo = now.minus(Duration.ofHours(2))

    logDeviceSeen(uuid1, now, ns)
    logDeviceSeen(uuid2, oneHourAgo, ns)
    logDeviceSeen(uuid3, twoHoursAgo, ns)

    val afterDate = oneHourAgo.minus(Duration.ofMinutes(1))
    filterDevices(activatedAfter = afterDate.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 2
      result should contain allElementsOf Seq(uuid1, uuid2)
    }

    val beforeDate = oneHourAgo.plus(Duration.ofMinutes(1))
    filterDevices(activatedBefore = beforeDate.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 2
      result should contain allElementsOf Seq(uuid2, uuid3)
    }

    filterDevices(
      activatedAfter = afterDate.some,
      activatedBefore = beforeDate.some,
      namespace = ns
    ) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 1
      result.head shouldBe uuid2
    }

    filterDevices(namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 4
      result should contain allElementsOf Seq(uuid1, uuid2, uuid3, uuid4)
    }
  }

  test("can search devices by lastSeen time") {
    val ns = Namespace("lastseen_tests")

    val device1 = genDeviceT.sample.get
    val uuid1 = createDeviceInNamespaceOk(device1, ns)

    val device2 = genDeviceT.sample.get
    val uuid2 = createDeviceInNamespaceOk(device2, ns)

    val device3 = genDeviceT.sample.get
    val uuid3 = createDeviceInNamespaceOk(device3, ns)

    val device4 = genDeviceT.sample.get
    val uuid4 = createDeviceInNamespaceOk(device4, ns)

    val now = Instant.now
    val oneHourAgo = now.minus(Duration.ofHours(1))
    val twoHoursAgo = now.minus(Duration.ofHours(2))

    logDeviceSeen(uuid1, now, ns)
    logDeviceSeen(uuid2, oneHourAgo, ns)
    logDeviceSeen(uuid3, twoHoursAgo, ns)

    val startDate = oneHourAgo.minus(Duration.ofMinutes(1))
    filterDevices(lastSeenStart = startDate.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 2
      result should contain allElementsOf Seq(uuid1, uuid2)
    }

    val endDate = oneHourAgo.plus(Duration.ofMinutes(1))
    filterDevices(lastSeenEnd = endDate.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 2
      result should contain allElementsOf Seq(uuid2, uuid3)
    }

    filterDevices(
      lastSeenStart = startDate.some,
      activatedBefore = endDate.some,
      namespace = ns
    ) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 1
      result.head shouldBe uuid2
    }

    filterDevices(namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 4
      result should contain allElementsOf Seq(uuid1, uuid2, uuid3, uuid4)
    }
  }

  test("can search devices by creation time") {
    val ns = Namespace("createdat_tests")

    val device1 = genDeviceT.sample.get
    val uuid1 = createDeviceInNamespaceOk(device1, ns)

    val now = Instant.now
    val oneHourAgo = now.minus(Duration.ofHours(1))
    val oneHourAfter = now.plus(Duration.ofHours(1))

    filterDevices(createdAtStart = oneHourAgo.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 1
      result should contain allElementsOf Seq(uuid1)
    }

    filterDevices(createdAtEnd = oneHourAgo.some, namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 0
    }

    filterDevices(
      createdAtStart = oneHourAgo.some,
      createdAtEnd = oneHourAfter.some,
      namespace = ns
    ) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 1
      result should contain allElementsOf Seq(uuid1)
    }

    filterDevices(namespace = ns) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result.length shouldBe 1
      result should contain allElementsOf Seq(uuid1)
    }
  }

  test("can search static group devices") {
    val deviceT = genDeviceT.generate
    val deviceUuid1 = createDeviceOk(deviceT)
    val deviceUuid2 = createDeviceOk(genDeviceT.generate)
    val group = createStaticGroupOk()
    addDeviceToGroupOk(group, deviceUuid1)
    addDeviceToGroupOk(group, deviceUuid2)

    val nameContains = deviceT.deviceName.value.substring(0, 10)
    getDevicesByGrouping(
      grouped = true,
      GroupType.static.some,
      nameContains.some
    ) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result should contain(deviceUuid1)
      result shouldNot contain(deviceUuid2)
    }
  }

  test("can search dynamic group devices") {
    val deviceT1 = genDeviceTWith(
      Gen.const(validatedDeviceType.from("d1-xxyy-1234").toOption.get),
      Gen.const(DeviceOemId("d1-xxyy-1234"))
    ).generate
    val deviceT2 = genDeviceTWith(
      Gen.const(validatedDeviceType.from("d2-xxyy-5678").toOption.get),
      Gen.const(DeviceOemId("d2-xxyy-5678"))
    ).generate
    val deviceUuid1 = createDeviceOk(deviceT1)
    val deviceUuid2 = createDeviceOk(deviceT2)
    createDynamicGroupOk(GroupExpression.from("deviceid contains xxyy").toOption.get)

    val nameContains = "1234"
    getDevicesByGrouping(
      grouped = true,
      GroupType.dynamic.some,
      nameContains.some
    ) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result should contain(deviceUuid1)
      result shouldNot contain(deviceUuid2)
    }
  }

  test("can list installed packages for all devices with custom pagination limit and offset") {

    val limit = 30
    val offset = 10

    val deviceTs = genConflictFreeDeviceTs(limit * 2).generate
    val deviceIds = deviceTs.map(createDeviceOk)

    // the database is case-insensitive so when we need to take that in to account when sorting in scala
    // furthermore PackageId is not lexicographically ordered so we just use pairs
    def canonPkg(pkg: PackageId) =
      (pkg.name.toLowerCase, pkg.version)

    val commonPkg = genPackageId.generate

    // get packages directly through the DB without pagination
    val beforePkgsAction = InstalledPackages.getInstalledForAllDevices(defaultNs)
    val beforePkgs = db.run(beforePkgsAction).futureValue.map(canonPkg)

    val allDevicesPackages = deviceIds.map { device =>
      val pkgs = Gen.listOfN(2, genPackageId).generate.toSet + commonPkg
      installSoftwareOk(device, pkgs)
      pkgs
    }

    val allPackages =
      (allDevicesPackages.map(_.map(canonPkg)).toSet.flatten ++ beforePkgs.toSet).toSeq.sorted

    getInstalledForAllDevices(offset = offset, limit = limit) ~> routes ~> check {
      status shouldBe OK
      val paginationResult = responseAs[PaginationResult[PackageId]]
      paginationResult.total shouldBe allPackages.length
      paginationResult.limit shouldBe limit
      paginationResult.offset shouldBe offset
      val packages = paginationResult.values.map(canonPkg)
      packages.length shouldBe scala.math.min(limit, allPackages.length)
      packages shouldBe sorted
      packages shouldBe allPackages.slice(offset, offset + limit)
    }
  }

  test("list installed packages for all devices with negative pagination limit fails") {
    getInstalledForAllDevices(limit = -1) ~> routes ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'limit' was malformed")
    }
  }

  test("list installed packages for all devices with negative pagination offset fails") {
    getInstalledForAllDevices(offset = -1) ~> routes ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'offset' was malformed")
    }
  }

  test("Posting to affected packages returns affected devices") {
    forAll { (device: DeviceT, p: PackageId) =>
      val uuid = createDeviceOk(device)

      installSoftwareOk(uuid, Set(p))

      getAffected(Set(p)) ~> routes ~> check {
        status shouldBe OK
        responseAs[Map[DeviceId, Seq[PackageId]]].apply(uuid) shouldBe Seq(p)
      }
    }
  }

  test("Package stats correct reports number of installed instances") {
    val devices = genConflictFreeDeviceTs(10).sample.get
    val pkgName = genPackageIdName.sample.get
    val pkgVersion = genConflictFreePackageIdVersion(2)

    val uuids = devices.map(createDeviceOk(_))
    uuids.zipWithIndex.foreach { case (uuid, i) =>
      if (i % 2 == 0) {
        installSoftwareOk(uuid, Set(PackageId(pkgName, pkgVersion.head)))
      } else {
        installSoftwareOk(uuid, Set(PackageId(pkgName, pkgVersion(1))))
      }
    }
    getPackageStats(pkgName) ~> routes ~> check {
      status shouldBe OK
      val r = responseAs[PaginationResult[PackageStat]]
      r.total shouldBe 2
      r.values.contains(PackageStat(pkgVersion.head, 5)) shouldBe true
      r.values.contains(PackageStat(pkgVersion(1), 5)) shouldBe true
    }
  }

  test("DELETE existing device returns 202") {
    forAll { (devicePre: DeviceT) =>
      val uuid = createDeviceOk(devicePre)

      deleteDevice(uuid) ~> routes ~> check {
        status shouldBe Accepted
      }
    }
  }

  test("DELETE device removes it from its group") {
    forAll { (devicePre: DeviceT, groupName: GroupName) =>
      val uuid = createDeviceOk(devicePre)
      val groupId = createStaticGroupOk(groupName)

      addDeviceToGroupOk(groupId, uuid)
      listDevicesInGroup(groupId) ~> routes ~> check {
        status shouldBe OK
        val devices = responseAs[PaginationResult[DeviceId]]
        devices.values.find(_ == uuid) shouldBe Some(uuid)
      }

      db.run(DeleteDeviceDBIO.deleteDeviceIO(defaultNs, uuid)).futureValue

      eventually {
        fetchByGroupId(groupId, offset = 0, limit = 10) ~> routes ~> check {
          status shouldBe OK
          val devices = responseAs[PaginationResult[Device]]
          devices.values.find(_.uuid == uuid) shouldBe None
        }
      }

      listDevicesInGroup(groupId) ~> routes ~> check {
        status shouldBe OK
        val devices = responseAs[PaginationResult[DeviceId]]
        devices.values.find(_ == uuid) shouldBe None
      }
    }
  }

  test("DELETE device removes it from all groups") {
    val deviceNumber = 50
    val deviceTs = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)

    val groupNumber = 10
    val groups = Gen.listOfN(groupNumber, genGroupName()).sample.get
    val groupIds = groups.map(createStaticGroupOk)

    (0 until deviceNumber).foreach { i =>
      addDeviceToGroupOk(groupIds(i % groupNumber), deviceIds(i))
    }

    val uuid: DeviceId = deviceIds.head

    db.run(DeleteDeviceDBIO.deleteDeviceIO(defaultNs, uuid)).futureValue

    eventually {
      (0 until groupNumber).foreach { i =>
        fetchByGroupId(groupIds(i), offset = 0, limit = deviceNumber) ~> routes ~> check {
          status shouldBe OK
          val devices = responseAs[PaginationResult[Device]]
          devices.values.find(_.uuid == uuid) shouldBe None
        }
      }
    }
  }

  test("getting the groups of a device returns the correct static groups") {
    val groupId1 = createStaticGroupOk()
    val groupId2 = createStaticGroupOk()
    val deviceUuid = createDeviceOk(genDeviceT.sample.get)

    addDeviceToGroup(groupId1, deviceUuid) ~> routes ~> check {
      status shouldBe OK
    }
    addDeviceToGroup(groupId2, deviceUuid) ~> routes ~> check {
      status shouldBe OK
    }

    getGroupsOfDevice(deviceUuid) ~> routes ~> check {
      status shouldBe OK
      val groups = responseAs[PaginationResult[GroupId]]
      groups.total should be(2)
      groups.values should contain(groupId1)
      groups.values should contain(groupId2)
    }
  }

  test("counts devices that satisfy a dynamic group expression") {
    val testDevices = Map(
      validatedDeviceType.from("device1").value -> DeviceOemId("abc123"),
      validatedDeviceType.from("device2").value -> DeviceOemId("123abc456"),
      validatedDeviceType.from("device3").value -> DeviceOemId("123aba456")
    )
    testDevices
      .map { case (deviceName, oemId) => (Gen.const(deviceName), Gen.const(oemId)) }
      .map((genDeviceTWith _).tupled(_))
      .map(_.sample.get)
      .map(createDeviceOk)

    val expression: GroupExpression = GroupExpression
      .from("deviceid contains abc and deviceid position(5) is b and deviceid position(9) is 6")
      .toOption
      .get
    countDevicesForExpression(expression.some) ~> routes ~> check {
      status shouldBe OK
      responseAs[Int] shouldBe 1
    }
  }

  test("counts devices by recent and offline") {
    val uuid = createDeviceOk(genDeviceT.generate.copy(hibernated = true.some))

    logDeviceSeen(uuid, Instant.now().minusSeconds(10))

    Get(
      DeviceRegistryResourceUri
        .uri(api, "count")
        .withQuery(Query("recentSinceSecs" -> "0", "offlineSinceSecs" -> "0"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val counts = responseAs[DeviceStatusCounts]

      counts.hibernated shouldBe 1
      counts.recent shouldBe 0
      counts.offline should be > 0L
      counts.updateFailed shouldBe 0
      counts.updatePending shouldBe 0
      counts.updateInProgess shouldBe 0
    }
  }

  test("counting devices that satisfy a dynamic group expression fails if no expression is given") {
    countDevicesForExpression(None) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.Codes.InvalidGroupExpression
    }
  }

  test("finds all (and only) grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = true, None) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("groupedStatic") ++ m("groupedDynamic")
    }
  }

  test("finds all (and only) static grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = true, Some(GroupType.static)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("groupedStatic")
    }
  }

  test("finds all (and only) dynamic grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = true, Some(GroupType.dynamic)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("groupedDynamic")
    }
  }

  test("finds all (and only) ungrouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = false, None) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("ungrouped")
    }
  }

  test("finds all (and only) non-static grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = false, Some(GroupType.static)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("all").filterNot(m("groupedStatic").contains)
    }
  }

  test("finds all (and only) non-dynamic grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = false, Some(GroupType.dynamic)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("all").filterNot(m("groupedDynamic").contains)
    }
  }

  test("finds static group devices only once") {
    val deviceUuid = createDeviceOk(genDeviceT.generate)
    val group1 = createStaticGroupOk()
    val group2 = createStaticGroupOk()
    addDeviceToGroupOk(group1, deviceUuid)
    addDeviceToGroupOk(group2, deviceUuid)

    getDevicesByGrouping(grouped = true, Some(GroupType.static)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(_ == deviceUuid)
      result.length shouldBe 1
    }
  }

  test("finds dynamic group devices only once") {
    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd-1234")))
    createDynamicGroupOk(GroupExpression.from("deviceid contains abcd").toOption.get)
    createDynamicGroupOk(GroupExpression.from("deviceid contains 1234").toOption.get)

    getDevicesByGrouping(grouped = true, Some(GroupType.dynamic)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(_ == deviceUuid)
      result.length shouldBe 1
    }
  }

  test("finds grouped devices only once") {
    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd")))
    createDynamicGroupOk(GroupExpression.from("deviceid contains abcd").toOption.get)
    val group = createStaticGroupOk()
    addDeviceToGroupOk(group, deviceUuid)

    getDevicesByGrouping(grouped = true, groupType = None) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(_ == deviceUuid)
      result.length shouldBe 1
    }
  }

  test("can tag devices from a csv file and ignores unprovisioned devices") {
    val device1 = genDeviceT.generate
    val device2 = genDeviceT.generate
    val duid1 = createDeviceOk(device1)
    val duid2 = createDeviceOk(device2)
    val csvRows = Seq(
      Seq(device1.deviceId.underlying, "Germany", "Premium"),
      Seq(genDeviceId.generate.underlying, "France", "Standard"),
      Seq(device2.deviceId.underlying, "China", "Deluxe"),
      Seq(genDeviceId.generate.underlying, "Spain", "Standard")
    )

    postDeviceTags(csvRows) ~> routes ~> check {
      status shouldBe NoContent
      db.run(TaggedDeviceRepository.fetchForDevice(duid1))
        .futureValue
        .map { case (k, v) =>
          k.value -> v
        } should contain only ("market" -> "Germany", "trim" -> "Premium")
      db.run(TaggedDeviceRepository.fetchForDevice(duid2))
        .futureValue
        .map { case (k, v) =>
          k.value -> v
        } should contain only ("market" -> "China", "trim" -> "Deluxe")
    }

    getDeviceTagsOk.map(_.value) should contain theSameElementsAs Seq("market", "trim")
  }

  test("tagging from a csv overrides the previous tags") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val csvRows = Seq(
      Seq(deviceT.deviceId.underlying, "Germany", "Premium"),
      Seq(genDeviceId.generate.underlying, "France", "Standard")
    )

    postDeviceTags(csvRows) ~> routes ~> check {
      status shouldBe NoContent
      db.run(TaggedDeviceRepository.fetchForDevice(duid))
        .futureValue
        .map { case (k, v) =>
          k.value -> v
        } should contain only ("market" -> "Germany", "trim" -> "Premium")
    }

    val newRows = Seq(Seq(deviceT.deviceId.underlying, "China", "Deluxe"))
    postDeviceTags(newRows) ~> routes ~> check {
      status shouldBe NoContent
      db.run(TaggedDeviceRepository.fetchForDevice(duid))
        .futureValue
        .map { case (k, v) =>
          k.value -> v
        } should contain only ("market" -> "China", "trim" -> "Deluxe")
    }
  }

  test("fails if the csv file contains no 'DeviceID' column") {
    val csvRows = Seq(Seq(genDeviceId.generate.underlying, "France", "Standard"))

    postDeviceTags(csvRows, Seq("did", "market", "trim")) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.MalformedInputFile.code
    }
  }

  test("fails if the csv file headers contains invalid characters") {
    val csvRows = Seq(Seq(genDeviceId.generate.underlying, "France", "Standard"))

    postDeviceTags(csvRows, Seq("DeviceID", "mar*ket", "trim")) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.MalformedInputFile.code
    }
  }

  test("tagging devices from a csv file adds the devices to existing groups") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(market) contains pain").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val csvRows = Seq(
      Seq(deviceT.deviceId.underlying, "Spain", "Premium"),
      Seq(genDeviceId.generate.underlying, "France", "Standard")
    )

    postDeviceTags(csvRows) ~> routes ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }
  }

  test("tagging devices from a csv file removes devices from existing groups") {
    val deviceT1 = genDeviceT.generate
    val deviceT2 = genDeviceT.generate
    val duid1 = createDeviceOk(deviceT1)
    val duid2 = createDeviceOk(deviceT2)
    val expression = GroupExpression.from("tag(market) contains france").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val csvRows = Seq(
      Seq(deviceT1.deviceId.underlying, "France", "Premium"),
      Seq(deviceT2.deviceId.underlying, "France", "Standard")
    )

    postDeviceTagsOk(csvRows)
    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only (duid1, duid2)
    }

    val newCsvRows = Seq(
      Seq(deviceT1.deviceId.underlying, "France", "Premium"),
      Seq(deviceT2.deviceId.underlying, "", "Standard")
    )

    postDeviceTagsOk(newCsvRows)
    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid1
    }
  }

  test("tagging devices from a csv file doesn't add devices to groups they were already in") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(market) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val csvRows = Seq(
      Seq(deviceT.deviceId.underlying, "Italy", "Premium"),
      Seq(genDeviceId.generate.underlying, "France", "Standard")
    )

    postDeviceTags(csvRows) ~> routes ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    postDeviceTags(csvRows) ~> routes ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }
  }

  test("can rename a device tag id and it's idempotent") {
    forAll(sizeRange(10)) { (deviceTs: Seq[DeviceT]) =>
      whenever(deviceTs.nonEmpty) {
        deviceTs.map(createDeviceOk)
        val csvRows = deviceTs.map(d => Seq(d.deviceId.underlying, "some tag value"))
        val tagId = TagId.from("Market").valueOr(throw _)
        val newTagId = TagId.from("Country").valueOr(throw _)

        postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> routes ~> check {
          status shouldBe NoContent
        }
        getDeviceTagsOk should contain(tagId)

        Put(
          DeviceRegistryResourceUri.uri("device_tags", tagId.value),
          RenameTagId(newTagId)
        ) ~> routes ~> check {
          status shouldBe OK
        }
        getDeviceTagsOk should not contain tagId
        getDeviceTagsOk should contain(newTagId)

        // Idempotence
        Put(
          DeviceRegistryResourceUri.uri("device_tags", tagId.value),
          RenameTagId(newTagId)
        ) ~> routes ~> check {
          status shouldBe OK
        }
        getDeviceTagsOk should not contain tagId
        getDeviceTagsOk should contain(newTagId)
      }
    }
  }

  test("renaming a device tag id also changes the tag in the dynamic group expressions") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(colour) contains ue").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = TagId.from("colour").valueOr(throw _)
    val newTagId = TagId.from("chromatic spectrum").valueOr(throw _)

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Blue"))

    postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> routes ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    Put(
      DeviceRegistryResourceUri.uri("device_tags", tagId.value),
      RenameTagId(newTagId)
    ) ~> routes ~> check {
      status shouldBe OK
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    getGroupDetails(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[Group].expression shouldBe GroupExpression
        .from(s"tag(${newTagId.value}) contains ue")
        .toOption
    }
  }

  test("fails to rename a device tag id if the current tag is invalid") {
    val newTagId = TagId.from("Country").valueOr(throw _)

    Put(
      DeviceRegistryResourceUri.uri("device_tags", "in+valid*"),
      RenameTagId(newTagId)
    ) ~> routes ~> check {
      status shouldBe NotFound
    }

    Put(DeviceRegistryResourceUri.uri("device_tags", "in+valid*")) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  test("fails to rename a device tag id if the new tag is invalid") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Monday", "Morning"))
    val tagId = TagId.from("market").valueOr(throw _)

    postDeviceTagsOk(csvRows)
    getDeviceTagsOk should contain(tagId)

    val newTagId = io.circe.parser.parse("""{ "tagId" : "in*valid*" }""").valueOr(throw _)
    Put(DeviceRegistryResourceUri.uri("device_tags", tagId.value), newTagId) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidEntity
    }
    getDeviceTagsOk should contain(tagId)
  }

  test("fails to rename a device tag id to an existing value") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Monday", "Morning"))
    val tagId = TagId.from("market").valueOr(throw _)
    val newTagId = TagId.from("trim").valueOr(throw _)

    postDeviceTagsOk(csvRows)
    getDeviceTagsOk should contain(tagId)

    Put(
      DeviceRegistryResourceUri.uri("device_tags", tagId.value),
      RenameTagId(newTagId)
    ) ~> routes ~> check {
      status shouldBe Conflict
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.ConflictingEntity
    }
    getDeviceTagsOk should contain(tagId)
  }

  test("updates a device tag value and updates the corresponding smart groups") {
    val deviceT1 = genDeviceT.generate
    val deviceT2 = genDeviceT.generate
    val duid1 = createDeviceOk(deviceT1)
    val duid2 = createDeviceOk(deviceT2)
    val expression = GroupExpression.from("tag(country) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = TagId.from("country").toOption.get

    val csvRows = Seq(
      Seq(deviceT1.deviceId.underlying, "Italy"),
      Seq(deviceT2.deviceId.underlying, "Spain"),
      Seq(genDeviceId.generate.underlying, "France")
    )
    postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> routes ~> check {
      status shouldBe NoContent
    }

    updateDeviceTagOk(duid1, tagId, "Germany")
    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values shouldBe empty
    }

    val updatedTags = updateDeviceTagOk(duid2, tagId, "NotItaly")
    updatedTags should contain only "country" -> "NotItaly"

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid2
    }
  }

  test("updating a device tag value for a non-existing tagId creates a tag") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(land) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val updatedTags = updateDeviceTagOk(duid, TagId.from("land").value, "Italy")
    updatedTags should contain only ("land" -> "Italy")

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      val updatedTags = responseAs[PaginationResult[DeviceId]].values
      updatedTags should contain only duid
    }
  }

  test("deleting a device tag updates the groups' expression and members") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression =
      GroupExpression.from("tag(pais) contains Ita or deviceid contains nonsense").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = TagId.from("pais").valueOr(throw _)

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Italy"))
    postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> routes ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroupOk(groupId, Seq(duid))

    deleteDeviceTagOk(tagId)

    getGroupDetails(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[Group].expression shouldBe GroupExpression
        .from("deviceid contains nonsense")
        .toOption
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].total shouldBe 0
    }
  }

  test(
    "fails to delete a device tag if there is at least one smart group that uses only that tag in the expression"
  ) {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(paese) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = "paese"

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Italy"))
    postDeviceTags(csvRows, Seq("DeviceID", tagId)) ~> routes ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroupOk(groupId, Seq(duid))

    Delete(DeviceRegistryResourceUri.uri("device_tags", tagId)) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.CannotRemoveDeviceTag.code
    }

    getGroupDetails(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[Group].expression shouldBe Some(expression)
    }

    listDevicesInGroupOk(groupId, Seq(duid))
  }

  test("checks which device tags are delible and which are not") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(deviceT.deviceId.underlying +: List.fill(8)("ha-ha"))
    val tagIds = (1 to 8).map(_.toString)
    postDeviceTags(csvRows, "DeviceID" +: tagIds) ~> routes ~> check {
      status shouldBe NoContent
    }

    // Delible expressions
    Seq(
      GroupExpression.from("tag(1) contains ha-ha or deviceid contains abc").valueOr(throw _),
      GroupExpression
        .from("tag(2) contains ha-ha or (deviceid contains abc and tag(2) position(4) is h)")
        .valueOr(throw _),
      GroupExpression.from("tag(3) contains ha-ha and tag(4) position(1) is h").valueOr(throw _),
      GroupExpression
        .from(
          "tag(3) contains ha-ha and tag(4) position(4) is h or tag(3) contains ha-ha and tag(4) position(4) is h"
        )
        .valueOr(throw _)
    ).map(createDynamicGroupOk(_))
    // Indelible expressions
    Seq(
      GroupExpression.from("tag(5) contains ha-ha").valueOr(throw _),
      GroupExpression.from("tag(6) position(4) is h").valueOr(throw _),
      GroupExpression.from("tag(7) contains ha-ha and tag(7) position(4) is h").valueOr(throw _),
      GroupExpression
        .from("tag(8) contains ha-ha or (tag(8) contains ha-ha and tag(8) position(4) is h)")
        .valueOr(throw _)
    ).map(createDynamicGroupOk(_))

    Get(DeviceRegistryResourceUri.uri("device_tags")) ~> routes ~> check {
      status shouldBe OK
      val (delibles, indelibles) = responseAs[Seq[TagInfo]]
        .filter(ti => tagIds.contains(ti.tagId.value))
        .partition(_.isDelible)
      delibles.map(_.tagId.value) should contain only ("1", "2", "3", "4")
      indelibles.map(_.tagId.value) should contain only ("5", "6", "7", "8")
    }
  }

  test("device tag becomes indelible after another is deleted") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "ha-ha", "ha-ha"))
    postDeviceTags(csvRows, Seq("DeviceID", "10", "11")) ~> routes ~> check {
      status shouldBe NoContent
    }

    val expression =
      GroupExpression.from("tag(10) position(2) is h and tag(11) contains ha-ha").valueOr(throw _)
    createDynamicGroupOk(expression)

    Get(DeviceRegistryResourceUri.uri("device_tags")) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[Seq[TagInfo]].map(ti => ti.tagId.value -> ti.isDelible)
      (result should contain).allOf("10" -> true, "11" -> true)
    }

    deleteDeviceTagOk(TagId.from("10").valueOr(throw _))

    Get(DeviceRegistryResourceUri.uri("device_tags")) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[Seq[TagInfo]].map(ti => ti.tagId.value -> ti.isDelible)
      result should contain("11" -> false)
    }
  }

  test("sets hibernate state") {
    val deviceT = genDeviceT.generate
    val uuid = createDeviceOk(deviceT)

    Post(
      DeviceRegistryResourceUri.uri(api, uuid.show, "hibernation"),
      UpdateHibernationStatusRequest(true)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val device = fetchDeviceOk(uuid)
    device.hibernated shouldBe true
  }

  test("sends message including previous hibernate state") {
    val deviceT = genDeviceT.generate
    val uuid = createDeviceOk(deviceT)

    Post(
      DeviceRegistryResourceUri.uri(api, uuid.show, "hibernation"),
      UpdateHibernationStatusRequest(true)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    var device = fetchDeviceOk(uuid)
    device.hibernated shouldBe true
    msgPub.reset()

    Post(
      DeviceRegistryResourceUri.uri(api, uuid.show, "hibernation"),
      UpdateHibernationStatusRequest(false)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    device = fetchDeviceOk(uuid)
    device.hibernated shouldBe false
  }

}
