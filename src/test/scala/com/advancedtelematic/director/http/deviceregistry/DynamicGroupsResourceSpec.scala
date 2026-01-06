package com.advancedtelematic.director.http.deviceregistry

import akka.http.scaladsl.model.StatusCodes.*
import cats.syntax.either.*
import cats.syntax.show.*
import com.advancedtelematic.director.db.DeleteDeviceDBIO
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.DeviceName.validatedDeviceType
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.{GroupExpression, GroupType}
import com.advancedtelematic.director.util.{DirectorSpec, ResourceSpec}
import com.advancedtelematic.libats.data.{ErrorCodes, ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar.*
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.*

class DynamicGroupsResourceSpec
    extends DirectorSpec
    with ResourceSpec
    with Eventually
    with RegistryDeviceRequests
    with GroupRequests {

  implicit class DeviceIdToExpression(value: DeviceOemId) {

    def toValidExp: GroupExpression =
      GroupExpression
        .from(s"deviceid contains ${value.underlying}")
        .valueOr(err => throw new IllegalArgumentException(err))

  }

  test("dynamic group gets created.") {
    createGroup(
      GroupType.dynamic,
      Some(GroupExpression.from("deviceid contains something").toOption.get),
      None
    ) ~> routes ~> check {
      status shouldBe Created
    }
  }

  test("device gets added to dynamic group") {
    val deviceT = genDeviceT.sample.get
    val deviceUuid = createDeviceOk(deviceT)
    val groupId = createDynamicGroupOk(deviceT.deviceId.toValidExp)

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[DeviceId]]
      devices.values should have size 1
      devices.values.contains(deviceUuid) shouldBe true
    }
  }

  test("dynamic group is empty when no device matches the expression") {
    val groupId =
      createDynamicGroupOk(GroupExpression.from("deviceid contains nothing").toOption.get)

    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      val devices = responseAs[PaginationResult[DeviceId]]
      devices.values should be(empty)
    }
  }

  test("dynamic group with invalid expression is not created") {
    val body = io.circe.parser
      .parse("""
        | {
        |   "name"       : "some name",
        |   "groupType"  : "dynamic",
        |   "expression" : ""
        | }
      """.stripMargin)
      .valueOr(throw _)
    createGroup(body) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidEntity
    }
  }

  test("manually adding a device to dynamic group fails") {
    val deviceT = genDeviceT.sample.get
    val deviceUuid = createDeviceOk(deviceT)
    val groupId = createDynamicGroupOk(deviceT.deviceId.toValidExp)

    addDeviceToGroup(groupId, deviceUuid) ~> routes ~> check {
      status shouldBe BadRequest
    }
  }

  test("counts devices for dynamic group") {
    val deviceT = genDeviceT.sample.get
    val _ = createDeviceOk(deviceT)
    val groupId = createDynamicGroupOk(deviceT.deviceId.toValidExp)

    countDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[Long] shouldBe 1
    }
  }

  test("removing a device from a dynamic group fails") {
    val deviceT = genDeviceT.sample.get
    val deviceUuid = createDeviceOk(deviceT)
    val groupId = createDynamicGroupOk(deviceT.deviceId.toValidExp)

    removeDeviceFromGroup(groupId, deviceUuid) ~> routes ~> check {
      status shouldBe BadRequest
    }
  }

  test("deleting a device causes it to be removed from dynamic group") {
    val deviceT = genDeviceT.sample.get
    val deviceUuid = createDeviceOk(deviceT)
    val groupId = createDynamicGroupOk(deviceT.deviceId.toValidExp)
    listDevicesInGroup(groupId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceId]].values should contain(deviceUuid)
    }

    db.run(DeleteDeviceDBIO.deleteDeviceIO(defaultNs, deviceUuid)).futureValue

    eventually(timeout(5.seconds), interval(100.millis)) {
      listDevicesInGroup(groupId) ~> routes ~> check {
        status shouldBe OK
        responseAs[PaginationResult[DeviceId]].values should be(empty)
      }
    }
  }

  test("getting the groups of a device returns the correct dynamic groups") {
    val deviceT = genDeviceT.sample.get
      .copy(deviceName = validatedDeviceType.from("12347890800808").toOption.get)
    val deviceId: DeviceOemId = deviceT.deviceId
    val deviceUuid = createDeviceOk(deviceT)

    val expression1 = GroupExpression
      .from(s"deviceid contains ${deviceId.show.substring(0, 5)}")
      .toOption
      .get // To test "starts with"
    val groupId1 = createDynamicGroupOk(expression1)
    val expression2 = GroupExpression
      .from(s"deviceid contains ${deviceId.show.substring(2, 10)}")
      .toOption
      .get // To test "contains"
    val groupId2 = createDynamicGroupOk(expression2)

    getGroupsOfDevice(deviceUuid) ~> routes ~> check {
      status shouldBe OK
      val groups = responseAs[PaginationResult[GroupId]]
      groups.total should be(2)
      groups.values should contain(groupId1)
      groups.values should contain(groupId2)
    }
  }

  test("getting the groups of a device using two 'contains' returns the correct dynamic groups") {
    val deviceT =
      genDeviceT.sample.get.copy(deviceName = validatedDeviceType.from("ABCDEFGHIJ").toOption.get)
    val deviceId: DeviceOemId = deviceT.deviceId
    val deviceUuid = createDeviceOk(deviceT)

    val expression1 = GroupExpression
      .from(s"deviceid contains ${deviceId.show
          .substring(0, 3)} and deviceid contains ${deviceId.show.substring(6, 9)}")
      .toOption
      .get
    val groupId1 = createDynamicGroupOk(expression1)
    val expression2 = GroupExpression.from(s"deviceid contains 0empty0").toOption.get
    val _ = createDynamicGroupOk(expression2)

    getGroupsOfDevice(deviceUuid) ~> routes ~> check {
      status shouldBe OK
      val groups = responseAs[PaginationResult[GroupId]]
      groups.total shouldBe 1
      groups.values should contain(groupId1)
    }
  }

  test("getting the groups of a device returns the correct groups (dynamic and static)") {
    val deviceT =
      genDeviceT.sample.get.copy(deviceName = validatedDeviceType.from("ABCDE").toOption.get)
    val deviceUuid = createDeviceOk(deviceT)

    // Add the device to a static group
    val staticGroupId = createStaticGroupOk()
    addDeviceToGroupOk(staticGroupId, deviceUuid)

    // Make the device show up for a dynamic group
    val expression = GroupExpression
      .from(s"deviceid contains ${deviceT.deviceId.show.substring(1, 5)}")
      .toOption
      .get
    val dynamicGroupId = createDynamicGroupOk(expression)

    getGroupsOfDevice(deviceUuid) ~> routes ~> check {
      status shouldBe OK
      val groups = responseAs[PaginationResult[GroupId]]
      groups.total should be(2)
      groups.values should contain(staticGroupId)
      groups.values should contain(dynamicGroupId)
    }
  }

}
