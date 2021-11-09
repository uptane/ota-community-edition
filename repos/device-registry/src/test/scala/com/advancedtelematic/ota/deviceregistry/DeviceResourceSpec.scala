/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import java.time.temporal.ChronoUnit
import java.time.{Instant, OffsetDateTime}
import akka.http.scaladsl.model.StatusCodes._
import cats.syntax.either._
import cats.syntax.option._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.{ErrorCodes, ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeleteDeviceRequest, DeviceSeen}
import com.advancedtelematic.ota.deviceregistry.common.{Errors, PackageStat}
import com.advancedtelematic.ota.deviceregistry.daemon.{DeleteDeviceListener, DeviceSeenListener}
import com.advancedtelematic.ota.deviceregistry.data.DataType.{DeviceT, RenameTagId, TagInfo}
import com.advancedtelematic.ota.deviceregistry.data.DeviceName.validatedDeviceType
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import com.advancedtelematic.ota.deviceregistry.data.{Device, DeviceStatus, PackageId, _}
import com.advancedtelematic.ota.deviceregistry.db.InstalledPackages.{DevicesCount, InstalledPackage}
import com.advancedtelematic.ota.deviceregistry.db.{InstalledPackages, TaggedDeviceRepository}
import io.circe.Json
import io.circe.generic.auto._
import org.scalacheck.Arbitrary._
import org.scalacheck.{Gen, Shrink}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

/**
  * Spec for DeviceRepository REST actions
  */
class DeviceResourceSpec extends ResourcePropSpec with ScalaFutures with Eventually {

  import Device._
  import GeneratorOps._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  private implicit val exec = system.dispatcher
  private val publisher     = new DeviceSeenListener(MessageBusPublisher.ignore)

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(100, Millis))

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  def isRecent(time: Option[Instant]): Boolean = time match {
    case Some(t) => t.isAfter(Instant.now.minus(3, ChronoUnit.MINUTES))
    case None    => false
  }

  private def sendDeviceSeen(uuid: DeviceId, lastSeen: Instant = Instant.now()): Unit =
    publisher(DeviceSeen(defaultNs, uuid, lastSeen)).futureValue

  private def createGroupedAndUngroupedDevices(): Map[String, Seq[DeviceId]] = {
    val deviceTs = genConflictFreeDeviceTs(12).sample.get
    val deviceIds    = deviceTs.map(createDeviceOk)
    val staticGroup = createStaticGroupOk()

    deviceIds.take(4).foreach(addDeviceToGroupOk(staticGroup, _))
    val expr = deviceTs.slice(4, 8).map(_.deviceId.underlying.take(6)).map(n => s"deviceid contains $n").reduce(_ + " or " + _)
    createDynamicGroupOk(GroupExpression.from(expr).right.get)

    Map("all" -> deviceIds, "groupedStatic" -> deviceIds.take(4),
      "groupedDynamic" -> deviceIds.slice(4, 8), "ungrouped" -> deviceIds.drop(8))
  }

  property("GET, PUT, DELETE, and POST '/ping' request fails on non-existent device") {
    forAll { (uuid: DeviceId, device: DeviceT) =>
      fetchDevice(uuid) ~> route ~> check { status shouldBe NotFound }
      updateDevice(uuid, device.deviceName) ~> route ~> check { status shouldBe NotFound }
      deleteDevice(uuid) ~> route ~> check { status shouldBe NotFound }
    }
  }

  property("fetches only devices for the given namespace") {
    forAll { (dt1: DeviceT, dt2: DeviceT) =>
      val d1 = createDeviceInNamespaceOk(dt1, defaultNs)
      val d2 = createDeviceInNamespaceOk(dt2, Namespace("test-namespace"))

      listDevices() ~> route ~> check {
        status shouldBe OK
        val devices = responseAs[PaginationResult[Device]].values.map(_.uuid)
        devices should contain(d1)
        devices should not contain d2
      }
    }
  }

  property("uses correct codec for device") {
    import org.scalatest.EitherValues._
    import org.scalatest.OptionValues._
    import io.circe.syntax._

    forAll { (dt1: DeviceT) =>
      val d1 = createDeviceInNamespaceOk(dt1, defaultNs)

      listDevices() ~> route ~> check {
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
            |  "deviceStatus" : "NotSeen"
            |}
            |""".stripMargin

        createdDevice shouldBe io.circe.parser.parse(expected).value
      }
    }
  }


  property("GET request (for Id) after POST yields same device") {
    forAll { devicePre: DeviceT =>
      val uuid: DeviceId = createDeviceOk(devicePre)

      fetchDevice(uuid) ~> route ~> check {
        status shouldBe OK
        val devicePost: Device = responseAs[Device]
        devicePost.deviceId shouldBe devicePre.deviceId
        devicePost.deviceType shouldBe devicePre.deviceType
        devicePost.lastSeen shouldBe None
      }
    }
  }

  property("GET request with ?deviceId after creating yields same device.") {
    forAll { (deviceId: DeviceOemId, devicePre: DeviceT) =>
      val uuid = createDeviceOk(devicePre.copy(deviceId = deviceId))
      fetchByDeviceId(deviceId) ~> route ~> check {
        status shouldBe OK
        val devicePost1: Device = responseAs[PaginationResult[Device]].values.head
        fetchDevice(uuid) ~> route ~> check {
          status shouldBe OK
          val devicePost2: Device = responseAs[Device]

          devicePost1 shouldBe devicePost2
        }
      }
    }
  }

  property("GET devices not seen for the last hours") {
    forAll(sizeRange(20)) { (neverSeen: Seq[DeviceT], notSeenLately: Seq[DeviceT], seenLately: Seq[DeviceT]) =>
      val neverSeenIds = neverSeen.map(createDeviceOk(_))
      val notSeenLatelyIds = notSeenLately.map(createDeviceOk(_))
      val seenLatelyIds = seenLately.map(createDeviceOk(_))

      seenLatelyIds.foreach(sendDeviceSeen(_))
      val hours = Gen.chooseNum(1, 100000).sample.get
      notSeenLatelyIds.foreach { did =>
        val i = Instant.now.minus(hours, ChronoUnit.HOURS).minusSeconds(600)
        sendDeviceSeen(did, i)
      }

      fetchNotSeenSince(hours) ~> route ~> check {
        status shouldBe OK
        val notSeenSinceHours = responseAs[PaginationResult[Device]].values
        notSeenSinceHours.map(_.uuid) should contain allElementsOf neverSeenIds ++ notSeenLatelyIds
        notSeenSinceHours.map(_.uuid) should contain noElementsOf seenLatelyIds
      }
    }
  }

  property("PUT request after POST succeeds with updated device.") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1, d2) =>
        val uuid: DeviceId = createDeviceOk(d1)

        updateDevice(uuid, d2.deviceName) ~> route ~> check {
          status shouldBe OK
          fetchDevice(uuid) ~> route ~> check {
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

  property("POST request creates a new device.") {
    forAll { devicePre: DeviceT =>
      val uuid = createDeviceOk(devicePre)
      devicePre.uuid.foreach(_ should equal(uuid))
      fetchDevice(uuid) ~> route ~> check {
        status shouldBe OK
        val devicePost: Device = responseAs[Device]
        devicePost.uuid shouldBe uuid
        devicePost.deviceId shouldBe devicePre.deviceId
        devicePost.deviceType shouldBe devicePre.deviceType
      }
    }
  }

  property("POST request on 'ping' should update 'lastSeen' field for device.") {
    forAll { devicePre: DeviceT =>
      val uuid: DeviceId = createDeviceOk(devicePre)

      sendDeviceSeen(uuid)

      fetchDevice(uuid) ~> route ~> check {
        val devicePost: Device = responseAs[Device]

        devicePost.lastSeen should not be None
        isRecent(devicePost.lastSeen) shouldBe true
        devicePost.deviceStatus should not be DeviceStatus.NotSeen
      }
    }
  }

  property("POST request with same deviceName fails with conflict.") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1, d2) =>
        val name       = arbitrary[DeviceName].sample.get
        createDeviceOk(d1.copy(deviceName = name))

        createDevice(d2.copy(deviceName = name)) ~> route ~> check {
          status shouldBe Conflict
        }
    }
  }

  property("POST request with same deviceId fails with conflict.") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1, d2) =>
        createDeviceOk(d1)
        createDevice(d2.copy(deviceId = d1.deviceId)) ~> route ~> check {
          status shouldBe Conflict
        }
    }
  }

  property("First POST request on 'ping' should update 'activatedAt' field for device.") {
    forAll { devicePre: DeviceT =>
      val uuid = createDeviceOk(devicePre)

      sendDeviceSeen(uuid)

      fetchDevice(uuid) ~> route ~> check {
        val firstDevice = responseAs[Device]

        val firstActivation = firstDevice.activatedAt
        firstActivation should not be None
        isRecent(firstActivation) shouldBe true

        fetchDevice(uuid) ~> route ~> check {
          val secondDevice = responseAs[Device]

          secondDevice.activatedAt shouldBe firstActivation
        }
      }
    }
  }

  property("POST request on ping gets counted") {
    forAll { devicePre: DeviceT =>
      val start      = OffsetDateTime.now()
      val uuid = createDeviceOk(devicePre)
      val end        = start.plusHours(1)

      sendDeviceSeen(uuid)

      getActiveDeviceCount(start, end) ~> route ~> check {
        responseAs[ActiveDeviceCount].deviceCount shouldBe 1
      }
    }
  }

  property("PUT request updates device.") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1: DeviceT, d2: DeviceT) =>
        val uuid = createDeviceOk(d1)

        updateDevice(uuid, d2.deviceName) ~> route ~> check {
          status shouldBe OK
          fetchDevice(uuid) ~> route ~> check {
            status shouldBe OK
            val updatedDevice: Device = responseAs[Device]
            updatedDevice.deviceId shouldBe d1.deviceId
            updatedDevice.deviceType shouldBe d1.deviceType
            updatedDevice.lastSeen shouldBe None
          }
        }
    }
  }

  property("PUT request does not update last seen") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1: DeviceT, d2: DeviceT) =>
        val uuid = createDeviceOk(d1)

        sendDeviceSeen(uuid)

        updateDevice(uuid, d2.deviceName) ~> route ~> check {
          status shouldBe OK
          fetchDevice(uuid) ~> route ~> check {
            status shouldBe OK
            val updatedDevice: Device = responseAs[Device]
            updatedDevice.lastSeen shouldBe defined
          }
        }
    }
  }

  property("PUT request with same deviceName fails with conflict.") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1, d2) =>
        val uuid1 = createDeviceOk(d1)
        val _ = createDeviceOk(d2)

        updateDevice(uuid1, d2.deviceName) ~> route ~> check {
          status shouldBe Conflict
        }
    }
  }

  private[this] implicit val InstalledPackageDecoderInstance = {
    import com.advancedtelematic.libats.codecs.CirceCodecs._
    io.circe.generic.semiauto.deriveDecoder[InstalledPackage]
  }

  property("Can install packages on a device") {
    forAll { (device: DeviceT, pkg: PackageId) =>
      val uuid = createDeviceOk(device)

      installSoftware(uuid, Set(pkg)) ~> route ~> check {
        status shouldBe NoContent
      }

      listPackages(uuid) ~> route ~> check {
        status shouldBe OK
        val response = responseAs[PaginationResult[InstalledPackage]]
        response.total shouldBe 1
        response.values.head.packageId shouldEqual pkg
        response.values.head.device shouldBe uuid
      }
    }
  }

  property("Can filter list of installed packages on a device") {
    val uuid = createDeviceOk(genDeviceT.generate)
    val pkgs = List(PackageId("foo", "1.0.0"), PackageId("bar", "1.0.0"))

    installSoftware(uuid, pkgs.toSet) ~> route ~> check {
      status shouldBe NoContent
    }

    listPackages(uuid, Some("foo")) ~> route ~> check {
      status shouldBe OK
      val response = responseAs[PaginationResult[InstalledPackage]]
      response.total shouldBe 1
      response.values.head.packageId shouldEqual pkgs.head
      response.values.head.device shouldBe uuid
    }
  }

  property("Can get stats for a package") {
    val deviceNumber = 20
    val groupNumber  = 5
    val deviceTs     = genConflictFreeDeviceTs(deviceNumber).sample.get
    val groups       = Gen.listOfN(groupNumber, genGroupName()).sample.get
    val pkg          = genPackageId.sample.get

    val deviceIds = deviceTs.map(createDeviceOk)
    val groupIds = groups.map(createStaticGroupOk)

    (0 until deviceNumber).foreach { i =>
      addDeviceToGroupOk(groupIds(i % groupNumber), deviceIds(i))
    }
    deviceIds.foreach(device => installSoftwareOk(device, Set(pkg)))

    getStatsForPackage(pkg) ~> route ~> check {
      status shouldBe OK
      val resp = responseAs[DevicesCount]
      resp.deviceCount shouldBe deviceNumber
      //convert to sets as order isn't important
      resp.groupIds shouldBe groupIds.toSet
    }
  }

  property("can list devices with custom pagination limit") {
    val limit                = 30
    val deviceTs             = genConflictFreeDeviceTs(limit * 2).sample.get
    deviceTs.foreach(createDeviceOk)

    searchDevice("", limit = limit) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]]
      result.values.length shouldBe limit
    }
  }

  property("can list devices with custom pagination limit and offset") {
    val limit                = 30
    val offset               = 10
    val deviceTs             = genConflictFreeDeviceTs(limit * 2).sample.get
    deviceTs.foreach(createDeviceOk)

    searchDevice("", offset = offset, limit = limit) ~> route ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]].values
      devices.length shouldBe limit
      devices.zip(devices.tail).foreach {
        case (device1, device2) =>
          device1.deviceName.value.compareTo(device2.deviceName.value) should be <= 0
      }
    }
  }

  property("list devices with negative pagination limit fails") {
    searchDevice("", limit = -1) ~> route ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'limit' was malformed")
    }
  }

  property("list devices with negative pagination offset fails") {
    searchDevice("", offset = -1) ~> route ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'offset' was malformed")
    }
  }

  property("searching a device by 'nameContains' and 'deviceId' fails") {
    val deviceT = genDeviceT.sample.get
    createDeviceOk(deviceT)

    fetchByDeviceId(deviceT.deviceId, Some(""), None) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("nameContains must be empty when searching by deviceId")
    }
  }

  property("searching a device by 'groupId' and 'deviceId' fails") {
    val deviceT = genDeviceT.sample.get
    createDeviceOk(deviceT)

    fetchByDeviceId(deviceT.deviceId, None, Some(genStaticGroup.sample.get.id)) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("groupId must be empty when searching by deviceId")
    }
  }

  property("searching a device by 'notSeenSinceHours' and 'deviceId' fails") {
    val h = Gen.some(Gen.posNum[Int]).generate
    val deviceT = genDeviceT.sample.get
    createDeviceOk(deviceT)

    fetchByDeviceId(deviceT.deviceId, notSeenSinceHours = h) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].description should include ("notSeenSinceHours must be empty when searching by deviceId")
    }
  }

  property("can list devices by group ID") {
    val limit                = 30
    val offset               = 10
    val deviceNumber         = 50
    val deviceTs             = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)
    val groupId              = createStaticGroupOk()

    deviceIds.foreach { id =>
      addDeviceToGroupOk(groupId, id)
    }

    // test that we get back all the devices
    fetchByGroupId(groupId, offset = 0, limit = deviceNumber) ~> route ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]]
      devices.total shouldBe deviceNumber
      devices.values.map(_.uuid).toSet shouldBe deviceIds.toSet
    }

    // test that the limit works
    fetchByGroupId(groupId, offset = offset, limit = limit) ~> route ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]]
      devices.values.length shouldBe limit
    }
  }

  property("can list ungrouped devices") {
    val deviceNumber         = 50
    val deviceTs             = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)

    val beforeGrouping = fetchUngrouped(offset = 0, limit = deviceNumber) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[Device]]
    }

    // add devices to group and check that we get less ungrouped devices
    val groupId = createStaticGroupOk()

    deviceIds.foreach { id =>
      addDeviceToGroupOk(groupId, id)
    }

    val afterGrouping = fetchUngrouped(offset = 0, limit = deviceNumber) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[Device]]
    }

    beforeGrouping.total shouldBe afterGrouping.total + deviceNumber
  }

  property("search by 'nameContains' is case-insensitive") {
    val originalDeviceName = genDeviceName.generate.value
    val deviceUuids =
      Seq(originalDeviceName, originalDeviceName.toLowerCase, originalDeviceName.toUpperCase)
        .map(DeviceName.from(_).right.get)
        .map(deviceName => genDeviceT.generate.copy(deviceName = deviceName))
        .map(createDeviceOk)

    getDevicesByGrouping(grouped = false, None, originalDeviceName.some) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result should contain allElementsOf deviceUuids
    }
  }

  property("can search static group devices") {
    val deviceT     = genDeviceT.generate
    val deviceUuid1 = createDeviceOk(deviceT)
    val deviceUuid2 = createDeviceOk(genDeviceT.generate)
    val group       = createStaticGroupOk()
    addDeviceToGroupOk(group, deviceUuid1)
    addDeviceToGroupOk(group, deviceUuid2)

    val nameContains = deviceT.deviceName.value.substring(0, 10)
    getDevicesByGrouping(grouped = true, GroupType.static.some, nameContains.some) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result should contain(deviceUuid1)
      result shouldNot contain(deviceUuid2)
    }
  }

  property("can search dynamic group devices") {
    val deviceT1    = genDeviceTWith(Gen.const(validatedDeviceType.from("d1-xxyy-1234").right.get), Gen.const(DeviceOemId("d1-xxyy-1234"))).generate
    val deviceT2    = genDeviceTWith(Gen.const(validatedDeviceType.from("d2-xxyy-5678").right.get), Gen.const(DeviceOemId("d2-xxyy-5678"))).generate
    val deviceUuid1 = createDeviceOk(deviceT1)
    val deviceUuid2 = createDeviceOk(deviceT2)
    createDynamicGroupOk(GroupExpression.from("deviceid contains xxyy").right.get)

    val nameContains = "1234"
    getDevicesByGrouping(grouped = true, GroupType.dynamic.some, nameContains.some) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid)
      result should contain(deviceUuid1)
      result shouldNot contain(deviceUuid2)
    }
  }

  property("can list installed packages for all devices with custom pagination limit and offset") {

    val limit  = 30
    val offset = 10

    val deviceTs             = genConflictFreeDeviceTs(limit * 2).generate
    val deviceIds = deviceTs.map(createDeviceOk)

    // the database is case-insensitive so when we need to take that in to account when sorting in scala
    // furthermore PackageId is not lexicographically ordered so we just use pairs
    def canonPkg(pkg: PackageId) =
      (pkg.name.toLowerCase, pkg.version)

    val commonPkg = genPackageId.generate

    // get packages directly through the DB without pagination
    val beforePkgsAction = InstalledPackages.getInstalledForAllDevices(defaultNs)
    val beforePkgs       = db.run(beforePkgsAction).futureValue.map(canonPkg)

    val allDevicesPackages = deviceIds.map { device =>
      val pkgs = Gen.listOfN(2, genPackageId).generate.toSet + commonPkg
      installSoftwareOk(device, pkgs)
      pkgs
    }

    val allPackages =
      (allDevicesPackages.map(_.map(canonPkg)).toSet.flatten ++ beforePkgs.toSet).toSeq.sorted

    getInstalledForAllDevices(offset = offset, limit = limit) ~> route ~> check {
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

  property("list installed packages for all devices with negative pagination limit fails") {
    getInstalledForAllDevices(limit = -1) ~> route ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'limit' was malformed")
    }
  }

  property("list installed packages for all devices with negative pagination offset fails") {
    getInstalledForAllDevices(offset = -1) ~> route ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'offset' was malformed")
    }
  }

  property("Posting to affected packages returns affected devices") {
    forAll { (device: DeviceT, p: PackageId) =>
      val uuid = createDeviceOk(device)

      installSoftwareOk(uuid, Set(p))

      getAffected(Set(p)) ~> route ~> check {
        status shouldBe OK
        responseAs[Map[DeviceId, Seq[PackageId]]].apply(uuid) shouldBe Seq(p)
      }
    }
  }

  property("Package stats correct reports number of installed instances") {
    val devices    = genConflictFreeDeviceTs(10).sample.get
    val pkgName    = genPackageIdName.sample.get
    val pkgVersion = genConflictFreePackageIdVersion(2)

    val uuids = devices.map(createDeviceOk(_))
    uuids.zipWithIndex.foreach {
      case (uuid, i) =>
        if (i % 2 == 0) {
          installSoftwareOk(uuid, Set(PackageId(pkgName, pkgVersion.head)))
        } else {
          installSoftwareOk(uuid, Set(PackageId(pkgName, pkgVersion(1))))
        }
    }
    getPackageStats(pkgName) ~> route ~> check {
      status shouldBe OK
      val r = responseAs[PaginationResult[PackageStat]]
      r.total shouldBe 2
      r.values.contains(PackageStat(pkgVersion.head, 5)) shouldBe true
      r.values.contains(PackageStat(pkgVersion(1), 5)) shouldBe true
    }
  }

  property("DELETE existing device returns 202") {
    forAll { devicePre: DeviceT =>
      val uuid = createDeviceOk(devicePre)

      deleteDevice(uuid) ~> route ~> check {
        status shouldBe Accepted
      }
    }
  }

  val listener = new DeleteDeviceListener()

  property("DELETE device removes it from its group") {
    forAll { (devicePre: DeviceT, groupName: GroupName) =>
      val uuid = createDeviceOk(devicePre)
      val groupId    = createStaticGroupOk(groupName)

      addDeviceToGroupOk(groupId, uuid)
      listDevicesInGroup(groupId) ~> route ~> check {
        status shouldBe OK
        val devices = responseAs[PaginationResult[DeviceId]]
        devices.values.find(_ == uuid) shouldBe Some(uuid)
      }

      listener.apply(DeleteDeviceRequest(defaultNs, uuid))

      import org.scalatest.time.SpanSugar._
      eventually {
        fetchByGroupId(groupId, offset = 0, limit = 10) ~> route ~> check {
          status shouldBe OK
          val devices = responseAs[PaginationResult[Device]]
          devices.values.find(_.uuid == uuid) shouldBe None
        }
      }

      listDevicesInGroup(groupId) ~> route ~> check {
        status shouldBe OK
        val devices = responseAs[PaginationResult[DeviceId]]
        devices.values.find(_ == uuid) shouldBe None
      }
    }
  }

  property("DELETE device removes it from all groups") {
    val deviceNumber         = 50
    val deviceTs             = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)

    val groupNumber            = 10
    val groups                 = Gen.listOfN(groupNumber, genGroupName()).sample.get
    val groupIds = groups.map(createStaticGroupOk)

    (0 until deviceNumber).foreach { i =>
      addDeviceToGroupOk(groupIds(i % groupNumber), deviceIds(i))
    }

    val uuid: DeviceId = deviceIds.head

    listener.apply(new DeleteDeviceRequest(defaultNs, uuid))

    import org.scalatest.time.SpanSugar._
    eventually {
      (0 until groupNumber).foreach { i =>
        fetchByGroupId(groupIds(i), offset = 0, limit = deviceNumber) ~> route ~> check {
          status shouldBe OK
          val devices = responseAs[PaginationResult[Device]]
          devices.values.find(_.uuid == uuid) shouldBe None
        }
      }
    }
  }

  property("DELETE device does not cause error on subsequent DeviceSeen events") {
    forAll(genConflictFreeDeviceTs(2)) {
      case Seq(d1, d2) =>
        val uuid1 = createDeviceOk(d1)
        val uuid2 = createDeviceOk(d2)

        listener.apply(new DeleteDeviceRequest(defaultNs, uuid1))

        sendDeviceSeen(uuid1)
        sendDeviceSeen(uuid2)
        fetchDevice(uuid2) ~> route ~> check {
          val devicePost: Device = responseAs[Device]
          devicePost.lastSeen should not be None
          isRecent(devicePost.lastSeen) shouldBe true
          devicePost.deviceStatus should not be DeviceStatus.NotSeen
        }
    }
  }

  property("getting the groups of a device returns the correct static groups") {
    val groupId1   = createStaticGroupOk()
    val groupId2   = createStaticGroupOk()
    val deviceUuid = createDeviceOk(genDeviceT.sample.get)

    addDeviceToGroup(groupId1, deviceUuid) ~> route ~> check {
      status shouldBe OK
    }
    addDeviceToGroup(groupId2, deviceUuid) ~> route ~> check {
      status shouldBe OK
    }

    getGroupsOfDevice(deviceUuid) ~> route ~> check {
      status shouldBe OK
      val groups = responseAs[PaginationResult[GroupId]]
      groups.total should be(2)
      groups.values should contain(groupId1)
      groups.values should contain(groupId2)
    }
  }

  property("counts devices that satisfy a dynamic group expression") {
    val testDevices = Map(
      validatedDeviceType.from("device1").right.get -> DeviceOemId("abc123"),
      validatedDeviceType.from("device2").right.get -> DeviceOemId("123abc456"),
      validatedDeviceType.from("device3").right.get -> DeviceOemId("123aba456")
    )
    testDevices
      .map(t => (Gen.const(t._1), Gen.const(t._2)))
      .map((genDeviceTWith _).tupled(_))
      .map(_.sample.get)
      .map(createDeviceOk)

    val expression: GroupExpression = GroupExpression.from("deviceid contains abc and deviceid position(5) is b and deviceid position(9) is 6").right.get
    countDevicesForExpression(expression.some) ~> route ~> check {
      status shouldBe OK
      responseAs[Int] shouldBe 1
    }
  }

  property("counting devices that satisfy a dynamic group expression fails if no expression is given") {
    countDevicesForExpression(None) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.Codes.InvalidGroupExpression
    }
  }

  property("finds all (and only) grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = true, None) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("groupedStatic") ++ m("groupedDynamic")
    }
  }

  property("finds all (and only) static grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = true, Some(GroupType.static)) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("groupedStatic")
    }
  }

  property("finds all (and only) dynamic grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = true, Some(GroupType.dynamic)) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("groupedDynamic")
    }
  }

  property("finds all (and only) ungrouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = false, None) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("ungrouped")
    }
  }

  property("finds all (and only) non-static grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = false, Some(GroupType.static)) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("all").filterNot(m("groupedStatic").contains)
    }
  }

  property("finds all (and only) non-dynamic grouped devices") {
    val m = createGroupedAndUngroupedDevices()

    getDevicesByGrouping(grouped = false, Some(GroupType.dynamic)) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(m("all").contains)
      result should contain theSameElementsAs m("all").filterNot(m("groupedDynamic").contains)
    }
  }

  property("finds static group devices only once") {
    val deviceUuid = createDeviceOk(genDeviceT.generate)
    val group1 = createStaticGroupOk()
    val group2 = createStaticGroupOk()
    addDeviceToGroupOk(group1, deviceUuid)
    addDeviceToGroupOk(group2, deviceUuid)

    getDevicesByGrouping(grouped = true, Some(GroupType.static)) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(_ == deviceUuid)
      result.length shouldBe 1
    }
  }

  property("finds dynamic group devices only once") {
    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd-1234")))
    createDynamicGroupOk(GroupExpression.from("deviceid contains abcd").right.get)
    createDynamicGroupOk(GroupExpression.from("deviceid contains 1234").right.get)

    getDevicesByGrouping(grouped = true, Some(GroupType.dynamic)) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(_ == deviceUuid)
      result.length shouldBe 1
    }
  }

  property("finds grouped devices only once") {
    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd")))
    createDynamicGroupOk(GroupExpression.from("deviceid contains abcd").right.get)
    val group = createStaticGroupOk()
    addDeviceToGroupOk(group, deviceUuid)

    getDevicesByGrouping(grouped = true, groupType = None) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Device]].values.map(_.uuid).filter(_ == deviceUuid)
      result.length shouldBe 1
    }
  }

  property("finds all the devices sorted by name") {
    listDevices(Some(SortBy.Name)) ~> route ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]].values
      devices shouldBe devices.sortBy(_.deviceName.value)
    }
  }

  property("finds all the devices sorted by creation date") {
    listDevices(Some(SortBy.CreatedAt)) ~> route ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[Device]].values
      devices.map(_.createdAt) shouldBe devices.sortBy(_.createdAt).map(_.createdAt).reverse
    }
  }

  property("can tag devices from a csv file and ignores unprovisioned devices") {
    val device1 = genDeviceT.generate
    val device2 = genDeviceT.generate
    val duid1 = createDeviceOk(device1)
    val duid2 = createDeviceOk(device2)
    val csvRows = Seq(
      Seq(device1.deviceId.underlying, "Germany", "Premium"),
      Seq(genDeviceId.generate.underlying , "France", "Standard"),
      Seq(device2.deviceId.underlying, "China", "Deluxe"),
      Seq(genDeviceId.generate.underlying , "Spain", "Standard"),
    )

    postDeviceTags(csvRows) ~> route ~> check {
      status shouldBe NoContent
      db.run(TaggedDeviceRepository.fetchForDevice(duid1)).futureValue
        .map { case (k, v) => k.value -> v } should contain only ("market" -> "Germany", "trim" -> "Premium")
      db.run(TaggedDeviceRepository.fetchForDevice(duid2)).futureValue
        .map { case (k, v) => k.value -> v } should contain only ("market" -> "China", "trim" -> "Deluxe")
    }

    getDeviceTagsOk.map(_.value) should contain theSameElementsAs Seq("market", "trim")
  }

  property("tagging from a csv overrides the previous tags") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val csvRows = Seq(
      Seq(deviceT.deviceId.underlying, "Germany", "Premium"),
      Seq(genDeviceId.generate.underlying , "France", "Standard"),
    )

    postDeviceTags(csvRows) ~> route ~> check {
      status shouldBe NoContent
      db.run(TaggedDeviceRepository.fetchForDevice(duid)).futureValue
        .map { case (k, v) => k.value -> v } should contain only ("market" -> "Germany", "trim" -> "Premium")
    }

    val newRows = Seq(Seq(deviceT.deviceId.underlying, "China", "Deluxe"))
    postDeviceTags(newRows) ~> route ~> check {
      status shouldBe NoContent
      db.run(TaggedDeviceRepository.fetchForDevice(duid)).futureValue
        .map { case (k, v) => k.value -> v } should contain only ("market" -> "China", "trim" -> "Deluxe")
    }
  }

  property("fails if the csv file contains no 'DeviceID' column") {
    val csvRows = Seq(Seq(genDeviceId.generate.underlying , "France", "Standard"))

    postDeviceTags(csvRows, Seq("did", "market", "trim")) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.MalformedInputFile.code
    }
  }

  property("fails if the csv file headers contains invalid characters") {
    val csvRows = Seq(Seq(genDeviceId.generate.underlying , "France", "Standard"))

    postDeviceTags(csvRows, Seq("DeviceID", "mar*ket", "trim")) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.MalformedInputFile.code
    }
  }

  property("tagging devices from a csv file adds the devices to existing groups") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(market) contains pain").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val csvRows = Seq(
      Seq(deviceT.deviceId.underlying, "Spain", "Premium"),
      Seq(genDeviceId.generate.underlying , "France", "Standard"),
    )

    postDeviceTags(csvRows) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }
  }

  property("tagging devices from a csv file removes devices from existing groups") {
    val deviceT1 = genDeviceT.generate
    val deviceT2 = genDeviceT.generate
    val duid1 = createDeviceOk(deviceT1)
    val duid2 = createDeviceOk(deviceT2)
    val expression = GroupExpression.from("tag(market) contains france").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val csvRows = Seq(
      Seq(deviceT1.deviceId.underlying, "France", "Premium"),
      Seq(deviceT2.deviceId.underlying , "France", "Standard"),
    )

    postDeviceTagsOk(csvRows)
    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only (duid1, duid2)
    }

    val newCsvRows = Seq(
      Seq(deviceT1.deviceId.underlying, "France", "Premium"),
      Seq(deviceT2.deviceId.underlying , "", "Standard"),
    )

    postDeviceTagsOk(newCsvRows)
    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid1
    }
  }

  property("tagging devices from a csv file doesn't add devices to groups they were already in") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(market) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)

    val csvRows = Seq(
      Seq(deviceT.deviceId.underlying, "Italy", "Premium"),
      Seq(genDeviceId.generate.underlying , "France", "Standard"),
    )

    postDeviceTags(csvRows) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    postDeviceTags(csvRows) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }
  }

  property("can rename a device tag id and it's idempotent") {
    forAll(sizeRange(10)) { deviceTs: Seq[DeviceT] =>
      whenever(deviceTs.nonEmpty) {
        deviceTs.map(createDeviceOk)
        val csvRows = deviceTs.map(d => Seq(d.deviceId.underlying, "some tag value"))
        val tagId = TagId.from("Market").valueOr(throw _)
        val newTagId = TagId.from("Country").valueOr(throw _)

        postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> route ~> check {
          status shouldBe NoContent
        }
        getDeviceTagsOk should contain (tagId)

        Put(Resource.uri("device_tags", tagId.value), RenameTagId(newTagId)) ~> route ~> check {
          status shouldBe OK
        }
        getDeviceTagsOk should not contain tagId
        getDeviceTagsOk should contain (newTagId)

        // Idempotence
        Put(Resource.uri("device_tags", tagId.value), RenameTagId(newTagId)) ~> route ~> check {
          status shouldBe OK
        }
        getDeviceTagsOk should not contain tagId
        getDeviceTagsOk should contain (newTagId)
      }
    }
  }

  property("renaming a device tag id also changes the tag in the dynamic group expressions") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(colour) contains ue").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = TagId.from("colour").valueOr(throw _)
    val newTagId = TagId.from("chromatic spectrum").valueOr(throw _)

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Blue"))

    postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    Put(Resource.uri("device_tags", tagId.value), RenameTagId(newTagId)) ~> route ~> check {
      status shouldBe OK
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    getGroupDetails(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[Group].expression shouldBe GroupExpression.from(s"tag(${newTagId.value}) contains ue").toOption
    }
  }

  property("fails to rename a device tag id if the current tag is invalid") {
    val newTagId = TagId.from("Country").valueOr(throw _)

    Put(Resource.uri("device_tags", "in+valid*"), RenameTagId(newTagId)) ~> route ~> check {
      status shouldBe NotFound
    }

    Put(Resource.uri("device_tags", "in+valid*")) ~> route ~> check {
      status shouldBe NotFound
    }
  }

  property("fails to rename a device tag id if the new tag is invalid") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Monday", "Morning"))
    val tagId = TagId.from("market").valueOr(throw _)

    postDeviceTagsOk(csvRows)
    getDeviceTagsOk should contain (tagId)

    val newTagId = io.circe.parser.parse("""{ "tagId" : "in*valid*" }""").valueOr(throw _)
    Put(Resource.uri("device_tags", tagId.value), newTagId) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidEntity
    }
    getDeviceTagsOk should contain (tagId)
  }

  property("fails to rename a device tag id to an existing value") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Monday", "Morning"))
    val tagId = TagId.from("market").valueOr(throw _)
    val newTagId = TagId.from("trim").valueOr(throw _)

    postDeviceTagsOk(csvRows)
    getDeviceTagsOk should contain (tagId)

    Put(Resource.uri("device_tags", tagId.value), RenameTagId(newTagId)) ~> route ~> check {
      status shouldBe Conflict
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.ConflictingEntity
    }
    getDeviceTagsOk should contain (tagId)
  }

  property("updates a device tag value and updates the corresponding smart groups") {
    val deviceT1 = genDeviceT.generate
    val deviceT2 = genDeviceT.generate
    val duid1 = createDeviceOk(deviceT1)
    val duid2 = createDeviceOk(deviceT2)
    val expression = GroupExpression.from("tag(country) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = TagId.from("country").right.get

    val csvRows = Seq(
      Seq(deviceT1.deviceId.underlying, "Italy"),
      Seq(deviceT2.deviceId.underlying, "Spain"),
      Seq(genDeviceId.generate.underlying , "France"),
    )
    postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> route ~> check {
      status shouldBe NoContent
    }

    updateDeviceTagOk(duid1, tagId, "Germany")
    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values shouldBe empty
    }

    val updatedTags = updateDeviceTagOk(duid2, tagId, "NotItaly")
    updatedTags should contain only "country" -> "NotItaly"

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid2
    }
  }

  property("updating a device tag value for a non-existing tagId has no effect") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(land) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = "land"

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Italy"))
    postDeviceTags(csvRows, Seq("DeviceID", tagId)) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }

    val updatedTags = updateDeviceTagOk(duid, TagId.from("nonsense").right.get, "NotItaly")
    updatedTags should contain only "land" -> "Italy"

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain only duid
    }
  }

  property("deleting a device tag updates the groups' expression and members") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(pais) contains Ita or deviceid contains nonsense").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = TagId.from("pais").valueOr(throw _)

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Italy"))
    postDeviceTags(csvRows, Seq("DeviceID", tagId.value)) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroupOk(groupId, Seq(duid))

    deleteDeviceTagOk(tagId)

    getGroupDetails(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[Group].expression shouldBe GroupExpression.from("deviceid contains nonsense").toOption
    }

    listDevicesInGroup(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].total shouldBe 0
    }
  }

  property("fails to delete a device tag if there is at least one smart group that uses only that tag in the expression") {
    val deviceT = genDeviceT.generate
    val duid = createDeviceOk(deviceT)
    val expression = GroupExpression.from("tag(paese) contains Ita").valueOr(throw _)
    val groupId = createDynamicGroupOk(expression = expression)
    val tagId = "paese"

    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "Italy"))
    postDeviceTags(csvRows, Seq("DeviceID", tagId)) ~> route ~> check {
      status shouldBe NoContent
    }

    listDevicesInGroupOk(groupId, Seq(duid))

    Delete(Resource.uri("device_tags", tagId)) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.CannotRemoveDeviceTag.code
    }

    getGroupDetails(groupId) ~> route ~> check {
      status shouldBe OK
      responseAs[Group].expression shouldBe Some(expression)
    }

    listDevicesInGroupOk(groupId, Seq(duid))
  }

  property("checks which device tags are delible and which are not") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(deviceT.deviceId.underlying +: List.fill(8)("ha-ha"))
    val tagIds = (1 to 8).map(_.toString)
    postDeviceTags(csvRows, "DeviceID" +: tagIds) ~> route ~> check {
      status shouldBe NoContent
    }

    // Delible expressions
    Seq(
      GroupExpression.from("tag(1) contains ha-ha or deviceid contains abc").valueOr(throw _),
      GroupExpression.from("tag(2) contains ha-ha or (deviceid contains abc and tag(2) position(4) is h)").valueOr(throw _),
      GroupExpression.from("tag(3) contains ha-ha and tag(4) position(1) is h").valueOr(throw _),
      GroupExpression.from("tag(3) contains ha-ha and tag(4) position(4) is h or tag(3) contains ha-ha and tag(4) position(4) is h").valueOr(throw _),
    ).map(createDynamicGroupOk(_))
    // Indelible expressions
    Seq(
      GroupExpression.from("tag(5) contains ha-ha").valueOr(throw _),
      GroupExpression.from("tag(6) position(4) is h").valueOr(throw _),
      GroupExpression.from("tag(7) contains ha-ha and tag(7) position(4) is h").valueOr(throw _),
      GroupExpression.from("tag(8) contains ha-ha or (tag(8) contains ha-ha and tag(8) position(4) is h)").valueOr(throw _),
    ).map(createDynamicGroupOk(_))

    Get(Resource.uri("device_tags")) ~> route ~> check {
      status shouldBe OK
      val (delibles, indelibles) = responseAs[Seq[TagInfo]].filter(ti => tagIds.contains(ti.tagId.value)).partition(_.isDelible)
      delibles.map(_.tagId.value) should contain only ("1", "2", "3", "4")
      indelibles.map(_.tagId.value) should contain only ("5", "6", "7", "8")
    }
  }

  property("device tag becomes indelible after another is deleted") {
    val deviceT = genDeviceT.generate
    createDeviceOk(deviceT)
    val csvRows = Seq(Seq(deviceT.deviceId.underlying, "ha-ha", "ha-ha"))
    postDeviceTags(csvRows, Seq("DeviceID", "10", "11")) ~> route ~> check {
      status shouldBe NoContent
    }

    val expression = GroupExpression.from("tag(10) position(2) is h and tag(11) contains ha-ha").valueOr(throw _)
    createDynamicGroupOk(expression)

    Get(Resource.uri("device_tags")) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[Seq[TagInfo]].map(ti => ti.tagId.value -> ti.isDelible)
      result should contain allOf ("10" -> true, "11" -> true)
    }

    deleteDeviceTagOk(TagId.from("10").valueOr(throw _))

    Get(Resource.uri("device_tags")) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[Seq[TagInfo]].map(ti => ti.tagId.value -> ti.isDelible)
      result should contain ("11" -> false)
    }
  }

  property("can fetch devices by UUIDs") {
    forAll(genConflictFreeDeviceTs(10)) { devices =>
      val uuids = devices.map(createDeviceOk(_))

      listDevicesByUuids(uuids) ~> route ~> check {
        status shouldBe OK
        val page = responseAs[PaginationResult[Device]]
        page.total shouldBe devices.length
        page.values.map(_.uuid) should contain theSameElementsAs uuids
      }
    }
  }

  property("can fetch devices by UUIDs and sorted") {
    forAll(genConflictFreeDeviceTs(10)) { devices =>
      val uuids = devices.map(createDeviceOk(_))

      listDevicesByUuids(uuids, Some(SortBy.CreatedAt)) ~> route ~> check {
        status shouldBe OK
        val page = responseAs[PaginationResult[Device]]
        page.total shouldBe devices.length
        page.values.map(_.uuid) should contain theSameElementsAs uuids
      }
    }
  }

}
