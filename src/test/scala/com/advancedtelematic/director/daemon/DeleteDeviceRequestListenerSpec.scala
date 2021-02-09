package com.advancedtelematic.director.daemon

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.http.{AdminResources, DeviceResources}
import com.advancedtelematic.director.util.{DirectorSpec, RepositorySpec, RouteResourceSpec}
import com.advancedtelematic.director.data.ClientDataType
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._


class DeleteDeviceRequestListenerSpec extends DirectorSpec
                            with RouteResourceSpec with AdminResources with RepositorySpec with DeviceResources {

  val listener = new DeleteDeviceRequestListener()

  testWithRepo("a device can be (marked) deleted") { implicit ns =>
    val dev = registerAdminDeviceOk()
    val hardwareId = dev.ecus.values.head.hardwareId

    Get(apiUri(s"admin/devices?primaryHardwareId=${hardwareId.value}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val page = responseAs[PaginationResult[ClientDataType.Device]]
      page.total should equal(1)
      page.values.head.id shouldBe dev.deviceId
    }

    listener(DeleteDeviceRequest(ns, dev.deviceId)).futureValue

    Get(apiUri(s"admin/devices?primaryHardwareId=${hardwareId.value}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val page = responseAs[PaginationResult[ClientDataType.Device]]
      page.total should equal(0)
      page.values shouldBe 'empty
    }
  }
}
