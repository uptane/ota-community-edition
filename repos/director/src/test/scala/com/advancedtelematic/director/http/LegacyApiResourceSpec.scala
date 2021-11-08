package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.data.AdminDataType.{MultiTargetUpdate, QueueResponse}
import com.advancedtelematic.director.util.{DirectorSpec, RepositorySpec, RouteResourceSpec}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.director.data.Generators._
import com.advancedtelematic.libats.data.DataType.MultiTargetUpdateId
import com.advancedtelematic.director.data.GeneratorOps._
import com.advancedtelematic.director.data.Codecs._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import cats.syntax.show._
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import org.scalatest.OptionValues._
import com.advancedtelematic.libats.messaging_datatype.Messages._
import org.scalatest.LoneElement._

class LegacyApiResourceSpec extends DirectorSpec
  with RouteResourceSpec
  with AdminResources
  with RepositorySpec
  with AssignmentResources {

  override implicit val msgPub = new MockMessageBus

  testWithRepo("creates an assignment for the given update id for the specified device") { implicit ns =>
    val regDev = registerAdminDeviceWithSecondariesOk()

    val targetUpdate = GenTargetUpdateRequest.generate
    val mtu = MultiTargetUpdate(Map(regDev.primary.hardwareId -> targetUpdate))

    val mtuId = Post(apiUri("multi_target_updates"), mtu).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
      responseAs[UpdateId]
    }

    Put(apiUri(s"admin/devices/${regDev.deviceId.show}/multi_target_update/${mtuId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val queue = Get(apiUri(s"assignments/${regDev.deviceId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[List[QueueResponse]]
    }

    queue.head.correlationId shouldBe MultiTargetUpdateId(mtuId.uuid)
    queue.head.targets.get(regDev.primary.ecuSerial).value.image.filepath shouldBe targetUpdate.to.target
    queue.head.targets.get(regDev.secondaries.keys.head) shouldBe empty

    val msg = msgPub.findReceived[DeviceUpdateEvent] { msg: DeviceUpdateEvent =>
      msg.deviceUuid == regDev.deviceId
    }

    msg.value shouldBe a [DeviceUpdateAssigned]
  }

  testWithRepo("DELETE assignments cancels assigned updates") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId)

    val queue0 = getDeviceAssignmentOk(regDev.deviceId)
    queue0 shouldNot be(empty)

    Delete(apiUri("assignments/" + regDev.deviceId.show)).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Seq[DeviceId]]
    }

    val queue = getDeviceAssignmentOk(regDev.deviceId)
    queue shouldBe empty

    val msg = msgPub.findReceived[DeviceUpdateEvent] { msg: DeviceUpdateEvent =>
      msg.deviceUuid == regDev.deviceId
    }

    msg shouldBe defined
    msg.get shouldBe a [DeviceUpdateCanceled]
  }

  testWithRepo("get admin devices") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    Get(apiUri("admin/devices")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val devices = responseAs[PaginationResult[DeviceId]]
      devices.total shouldBe 1
      devices.offset shouldBe 0
      devices.limit shouldBe 50
      devices.values.loneElement shouldBe regDev.deviceId
    }
  }
}
