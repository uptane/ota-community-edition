/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import com.advancedtelematic.libats.data.PaginationResult.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.apache.pekko.http.scaladsl.model.Uri.Query
import cats.implicits.toShow
import com.advancedtelematic.director.deviceregistry.GroupMembership
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceT,
  UpdateHibernationStatusRequest
}
import com.advancedtelematic.director.http.deviceregistry.DeviceGroupStats
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.*
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupGenerators.*
import com.advancedtelematic.director.deviceregistry.data.{
  Group,
  GroupExpression,
  GroupName,
  GroupSortBy
}
import com.advancedtelematic.director.http.deviceregistry.Errors.Codes.MalformedInput
import com.advancedtelematic.director.util.{DirectorSpec, ResourceSpec}
import com.advancedtelematic.libats.data.{ErrorCodes, ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import io.circe.Json
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen
import org.scalatest.EitherValues.*
import org.scalatest.Inspectors.*
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import java.util.UUID
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus
import com.advancedtelematic.director.db.deviceregistry.DeviceRepository

import java.time.temporal.ChronoUnit

class GroupsResourceSpec
    extends DirectorSpec
    with ResourceSpec
    with RegistryDeviceRequests
    with GroupRequests {
  import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*

  private val limit = 30

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(15, Millis))

  test("gets all existing groups") {
    // TODO: PRO-1182 turn this back into a property when we can delete groups
    val groupNames = Gen.listOfN(10, arbitrary[GroupName]).sample.get
    groupNames.foreach(createStaticGroupOk)

    listGroups() ~> routes ~> check {
      status shouldBe OK
      val responseGroups = responseAs[PaginationResult[Group]]
      responseGroups.total shouldBe groupNames.size
      responseGroups.values.foreach { group =>
        groupNames.count(name => name == group.groupName) shouldBe 1
      }
    }
  }

  test("gets all existing groups sorted by name") {
    val groupNames = Gen.listOfN(20, genGroupName(Gen.alphaNumChar)).sample.get
    val sortedGroupNames = groupNames.sortBy(_.value.toLowerCase)
    groupNames.foreach(n => createGroupOk(n))

    listGroups() ~> routes ~> check {
      status shouldBe OK
      val responseGroups = responseAs[PaginationResult[Group]].values
      responseGroups.map(_.groupName).filter(sortedGroupNames.contains) shouldBe sortedGroupNames
    }
  }

  import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*

  test("DELETE deletes a static group and its members") {
    val groupId = createStaticGroupOk()
    val device = genDeviceT.generate

    val deviceId = createDeviceOk(device)

    addDeviceToGroupOk(groupId, deviceId)

    countDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[Long] shouldBe 1
    }

    deleteGroup(groupId) ~> routes ~> check {
      status shouldBe NoContent
    }

    countDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  test("DELETE deletes a dynamic group") {
    val device = genDeviceT.generate
    val groupId = createDynamicGroupOk(
      GroupExpression.from(s"deviceid contains ${device.deviceId.underlying}").value
    )

    createDeviceOk(device)

    countDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[Long] shouldBe 1
    }

    deleteGroup(groupId) ~> routes ~> check {
      status shouldBe NoContent
    }

    countDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  test("gets all existing groups sorted by creation time") {
    val groupIds = (1 to 20).map(_ => createGroupOk())

    listGroups(Some(GroupSortBy.CreatedAt)) ~> routes ~> check {
      status shouldBe OK
      val responseGroups = responseAs[PaginationResult[Group]].values
      responseGroups.reverse.map(_.id).filter(groupIds.contains) shouldBe groupIds
    }
  }

  test("gets group sizes for multiple groups") {
    val groupIds = (1 to 3).map(_ => createStaticGroupOk()).sortBy(_.show)

    groupIds.zipWithIndex.foreach { case (groupId, idx) =>
      val deviceTs = genConflictFreeDeviceTs(idx + 1).sample.get
      val deviceIds = deviceTs.map(createDeviceOk)
      deviceIds.foreach(deviceId => addDeviceToGroupOk(groupId, deviceId))
    }

    countDevicesPerGroup(groupIds.toSet) ~> routes ~> check {
      status shouldBe OK
      val json = responseAs[Json]
      val groupSizes = json.hcursor.downField("values").as[Map[GroupId, Long]].value

      groupSizes.size shouldBe groupIds.size

      groupIds.zipWithIndex.foreach { case (groupId, idx) =>
        groupSizes.get(groupId) should contain(idx + 1)
      }
    }
  }

  test("fails with bad request when requesting to count too many groups") {
    val groupIds = (1 to 101).map(_ => GroupId.generate())

    countDevicesPerGroup(groupIds.toSet) ~> routes ~> check {
      status shouldBe BadRequest
    }
  }

  test("fails to get existing groups given an invalid sorting") {
    val q = Query(Map("sortBy" -> Gen.alphaNumStr.sample.get))
    Get(DeviceRegistryResourceUri.uri(groupsApi).withQuery(q)) ~> routes ~> check {
      status shouldBe BadRequest
    }
  }

  test("gets all existing groups that contain a string") {
    val names = Seq("aabb", "baaxbc", "a123ba", "cba3b")
    val groupNames = names.map(GroupName.from(_).toOption.get)
    groupNames.foreach(n => createGroupOk(n))

    val tests = Map(
      "" -> names,
      "a1" -> Seq("a123ba"),
      "aa" -> Seq("aabb", "baaxbc"),
      "3b" -> Seq("a123ba", "cba3b"),
      "3" -> Seq("a123ba", "cba3b")
    )

    tests.foreach { case (k, v) =>
      listGroups(nameContains = Some(k)) ~> routes ~> check {
        status shouldBe OK
        val responseGroupNames =
          responseAs[PaginationResult[Group]].values.map(_.groupName.value).filter(names.contains)
        responseGroupNames.size shouldBe v.size
        responseGroupNames should contain allElementsOf v
      }
    }
  }

  test("lists devices with custom pagination limit") {
    val deviceNumber = 50
    val groupId = createStaticGroupOk()

    val deviceTs = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)

    deviceIds.foreach(deviceId => addDeviceToGroupOk(groupId, deviceId))

    listDevicesInGroup(groupId, limit = Some(limit)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[DeviceId]]
      result.values.length shouldBe limit
    }
  }

  test("lists devices with custom pagination limit and offset") {
    val offset = 10
    val deviceNumber = 50
    val groupId = createStaticGroupOk()

    val deviceTs = genConflictFreeDeviceTs(deviceNumber).sample.get
    val deviceIds = deviceTs.map(createDeviceOk)

    deviceIds.foreach(deviceId => addDeviceToGroupOk(groupId, deviceId))

    val allDevices = listDevicesInGroup(groupId, limit = Some(deviceNumber)) ~> routes ~> check {
      responseAs[PaginationResult[DeviceId]].values
    }

    listDevicesInGroup(groupId, offset = Some(offset), limit = Some(limit)) ~> routes ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[DeviceId]]
      result.values.length shouldBe limit
      allDevices.slice(offset, offset + limit) shouldEqual result.values
    }
  }

  test("lists devices with negative pagination limit fails") {
    val groupId = createStaticGroupOk()

    listDevicesInGroup(groupId, limit = Some(-1)) ~> routes ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'limit' was malformed")
    }
  }

  test("lists devices with negative pagination offset fails") {
    val groupId = createStaticGroupOk()

    listDevicesInGroup(groupId, offset = Some(-1)) ~> routes ~> check {
      status shouldBe BadRequest
      val res = responseAs[ErrorRepresentation]
      res.code shouldBe ErrorCodes.InvalidEntity
      res.description should include("The query parameter 'offset' was malformed")
    }
  }

  test("gets detailed information of a group") {
    val groupName = genGroupName().sample.get
    val groupId = createStaticGroupOk(groupName)

    getGroupDetails(groupId) ~> routes ~> check {
      status shouldBe OK
      val group: Group = responseAs[Group]
      group.id shouldBe groupId
      group.groupName shouldBe groupName
    }
  }

  test("gets detailed information of a non-existing group fails") {
    val groupId = genStaticGroup.sample.get.id

    getGroupDetails(groupId) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  test("renames a group") {
    val newGroupName = genGroupName().sample.get
    val groupId = createStaticGroupOk()

    renameGroup(groupId, newGroupName) ~> routes ~> check {
      status shouldBe OK
    }

    listGroups(limit = Some(100L)) ~> routes ~> check {
      status shouldBe OK
      val groups = responseAs[PaginationResult[Group]]
      groups.values.count(e => e.id.equals(groupId) && e.groupName.equals(newGroupName)) shouldBe 1
    }
  }

  test("counting devices fails for non-existing groups") {
    countDevicesInGroup(GroupId.generate()) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

  test("adds devices to groups") {
    val groupId = createStaticGroupOk()
    val deviceUuid = createDeviceOk(genDeviceT.sample.get)

    addDeviceToGroup(groupId, deviceUuid) ~> routes ~> check {
      status shouldBe OK
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[DeviceId]]
      devices.values.contains(deviceUuid) shouldBe true
    }
  }

  test("renaming a group to existing group fails") {
    val groupBName = genGroupName().sample.get
    val groupAId = createStaticGroupOk()
    val _ = createStaticGroupOk(groupBName)

    renameGroup(groupAId, groupBName) ~> routes ~> check {
      status shouldBe Conflict
    }
  }

  test("removes devices from a group") {
    val deviceId = createDeviceOk(genDeviceT.sample.get)
    val groupId = createStaticGroupOk()

    addDeviceToGroup(groupId, deviceId) ~> routes ~> check {
      status shouldBe OK
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[DeviceId]]
      devices.values.contains(deviceId) shouldBe true
    }

    removeDeviceFromGroup(groupId, deviceId) ~> routes ~> check {
      status shouldBe OK
    }

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[DeviceId]]
      devices.values.contains(deviceId) shouldBe false
    }
  }

  test("creates a static group from a file") {
    val groupName = genGroupName().sample.get
    val deviceTs = Gen.listOf(genDeviceT).sample.get
    val uuidsCreated = deviceTs.map(createDeviceOk)

    importGroup(groupName, deviceTs.map(_.deviceId)) ~> routes ~> check {
      status shouldEqual Created
      val groupId = responseAs[GroupId]
      val uuidsInGroup = new GroupMembership()
        .listDevices(groupId, 0L.toOffset, deviceTs.size.toLimit)
        .futureValue
        .values
      uuidsInGroup should contain allElementsOf uuidsCreated
    }
  }

  test(
    "creates a static group from a file even when containing more than FILTER_EXISTING_DEVICES_BATCH_SIZE deviceIds"
  ) {
    val groupName = genGroupName().sample.get
    val deviceTs = Gen.listOfN(500, genDeviceT).sample.get
    val uuidsCreated = deviceTs.map(createDeviceOk)

    importGroup(groupName, deviceTs.map(_.deviceId)) ~> routes ~> check {
      status shouldEqual Created
      val groupId = responseAs[GroupId]
      val uuidsInGroup = new GroupMembership()
        .listDevices(groupId, 0L.toOffset, deviceTs.size.toLimit)
        .futureValue
        .values
      uuidsInGroup should contain allElementsOf uuidsCreated
    }
  }

  test("creates a static group from a file but doesn't add devices if they're not provisioned") {
    val groupName = genGroupName().sample.get
    val deviceTs = Gen.listOf(genDeviceT).sample.get

    importGroup(groupName, deviceTs.map(_.deviceId)) ~> routes ~> check {
      status shouldEqual Created
      val groupId = responseAs[GroupId]
      val uuidsInGroup = new GroupMembership()
        .listDevices(groupId, 0L.toOffset, deviceTs.size.toLimit)
        .futureValue
        .values
      uuidsInGroup shouldBe empty
    }
  }

  test(
    "creating a static group from a file fails with 400 if the deviceIds are longer than it's allowed"
  ) {
    val groupName = genGroupName().sample.get
    val oemId = Gen.listOfN(130, Gen.alphaNumChar).map(_.mkString).map(DeviceOemId.apply).sample.get

    importGroup(groupName, Seq(oemId)) ~> routes ~> check {
      status shouldEqual BadRequest
      responseAs[ErrorRepresentation].code shouldBe MalformedInput
    }
  }

  test("sets devices in group to hibernated") {
    val groupId = createStaticGroupOk()
    val deviceUuid = createDeviceOk(genDeviceT.sample.get)

    addDeviceToGroupOk(groupId, deviceUuid)

    Post(
      DeviceRegistryResourceUri.uri(groupsApi, groupId.show, "hibernation"),
      UpdateHibernationStatusRequest(true)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val device = fetchDeviceOk(deviceUuid)
    device.hibernated shouldBe true
  }

  test("sets many devices in group to hibernated") {
    val groupId = createStaticGroupOk()
    val devices = Gen.listOfN(3, arbitrary[DeviceT]).sample.get
    val deviceIds = scala.collection.mutable.Set.empty[DeviceId]

    forAll(devices) { device =>
      val id = createDeviceOk(device)
      addDeviceToGroupOk(groupId, id)
      deviceIds += id
    }

    Post(
      DeviceRegistryResourceUri.uri(groupsApi, groupId.show, "hibernation"),
      UpdateHibernationStatusRequest(true)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    forAll(deviceIds) { id =>
      val device = fetchDeviceOk(id)
      device.hibernated shouldBe true
    }
  }

  test("retrieve group membership for multiple devices") {
    val groupId1 = createStaticGroupOk()
    val groupId2 = createStaticGroupOk()
    val deviceUuid1 = createDeviceOk(genDeviceT.sample.get)
    val deviceUuid2 = createDeviceOk(genDeviceT.sample.get)

    addDeviceToGroupOk(groupId1, deviceUuid1)
    addDeviceToGroupOk(groupId1, deviceUuid2)
    addDeviceToGroupOk(groupId2, deviceUuid1)

    val devices = Seq[DeviceId](deviceUuid1, deviceUuid2)
    Get(
      DeviceRegistryResourceUri
        .uri(groupsApi, "membership")
        .withQuery(
          Query(Map[String, String]("deviceUuids" -> devices.map(_.uuid.toString).mkString(",")))
        )
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val res = responseAs[Map[DeviceId, Seq[GroupId]]]
      res.map { case (x, y) =>
        println(x.uuid.toString + " -> " + y.map(_.uuid.toString).mkString(","))
      }
      res(deviceUuid1) should contain(groupId1)
      res(deviceUuid1) should contain(groupId2)
      res(deviceUuid2) should contain(groupId1)
      res(deviceUuid2) should not contain groupId2
    }
  }

  test("retrieve group membership with one invalid device") {
    val groupId1 = createStaticGroupOk()
    val groupId2 = createStaticGroupOk()
    val deviceUuid1 = createDeviceOk(genDeviceT.sample.get)
    val deviceUuid2 = createDeviceOk(genDeviceT.sample.get)

    addDeviceToGroupOk(groupId1, deviceUuid1)
    addDeviceToGroupOk(groupId1, deviceUuid2)
    addDeviceToGroupOk(groupId2, deviceUuid1)

    val unknownDeviceId = DeviceId(UUID.randomUUID())
    val devices = Seq[String](deviceUuid1.uuid.toString, unknownDeviceId.uuid.toString)
    Get(
      DeviceRegistryResourceUri
        .uri(groupsApi, "membership")
        .withQuery(Query(Map[String, String]("deviceUuids" -> devices.mkString(","))))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val res = responseAs[Map[DeviceId, Seq[GroupId]]]
      println(res)
      res(deviceUuid1) should contain(groupId1)
      res(deviceUuid1) should contain(groupId2)
      res.keys should not contain unknownDeviceId
    }
  }

  test("gets device stats for an empty group") {
    val groupId = createStaticGroupOk()

    getDeviceStats(groupId) ~> routes ~> check {
      status shouldBe OK
      val stats = responseAs[DeviceGroupStats]
      stats.status.isEmpty shouldBe true
      stats.lastSeen.`10mins` shouldBe 0
      stats.lastSeen.`1hour` shouldBe 0
      stats.lastSeen.`1day` shouldBe 0
      stats.lastSeen.`1week` shouldBe 0
      stats.lastSeen.`1month` shouldBe 0
      stats.lastSeen.`1year` shouldBe 0
    }
  }

  test("gets device stats for a group with devices in different states and last seen times") {
    val groupId = createStaticGroupOk()

    val notSeenDevices = Gen.listOfN(2, genDeviceT).sample.get
    val errorDevices = Gen.listOfN(3, genDeviceT).sample.get
    val upToDateDevices = Gen.listOfN(1, genDeviceT).sample.get
    val updatePendingDevices = Gen.listOfN(4, genDeviceT).sample.get

    notSeenDevices.foreach { device =>
      val id = createDeviceOk(device)
      addDeviceToGroupOk(groupId, id)
      // NotSeen is default for new devices
    }

    errorDevices.foreach { device =>
      val id = createDeviceOk(device)
      addDeviceToGroupOk(groupId, id)

      db.run(DeviceRepository.setDeviceStatus(id, DeviceStatus.Error)).futureValue
      db.run(DeviceRepository.updateLastSeen(id, Instant.now().minus(5, ChronoUnit.MINUTES)))
        .futureValue
    }

    upToDateDevices.foreach { device =>
      val id = createDeviceOk(device)
      addDeviceToGroupOk(groupId, id)
      db.run(DeviceRepository.setDeviceStatus(id, DeviceStatus.UpToDate)).futureValue
      db.run(DeviceRepository.updateLastSeen(id, Instant.now().minus(5, ChronoUnit.HOURS)))
        .futureValue
    }

    updatePendingDevices.foreach { device =>
      val id = createDeviceOk(device)
      addDeviceToGroupOk(groupId, id)
      db.run(DeviceRepository.setDeviceStatus(id, DeviceStatus.UpdatePending)).futureValue
      db.run(DeviceRepository.updateLastSeen(id, Instant.now().minus(20, ChronoUnit.DAYS)))
        .futureValue
    }

    getDeviceStats(groupId) ~> routes ~> check {
      status shouldBe OK
      val stats = responseAs[DeviceGroupStats]

      stats.status(DeviceStatus.NotSeen) shouldBe 2
      stats.status(DeviceStatus.Error) shouldBe 3
      stats.status(DeviceStatus.UpToDate) shouldBe 1
      stats.status(DeviceStatus.UpdatePending) shouldBe 4

      stats.status.values.sum shouldBe (notSeenDevices.size + errorDevices.size +
        upToDateDevices.size + updatePendingDevices.size)

      stats.lastSeen.`10mins` shouldBe 3
      stats.lastSeen.`1hour` shouldBe 3
      stats.lastSeen.`1day` shouldBe 4
      stats.lastSeen.`1week` shouldBe 4
      stats.lastSeen.`1month` shouldBe 8
      stats.lastSeen.`1year` shouldBe 8
    }
  }

  test("getting device stats for non-existent group returns not found") {
    val nonExistentGroupId = GroupId.generate()

    getDeviceStats(nonExistentGroupId) ~> routes ~> check {
      status shouldBe NotFound
    }
  }

}
