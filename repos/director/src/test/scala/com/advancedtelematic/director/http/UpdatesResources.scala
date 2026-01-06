package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import cats.syntax.show.*
import com.advancedtelematic.director.data.AdminDataType.TargetUpdateSpec
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.UpdateId
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.genDeviceT
import com.advancedtelematic.director.http.deviceregistry.RegistryDeviceRequests
import com.advancedtelematic.director.util.{DirectorSpec, NamespacedTests, ResourceSpec}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import org.scalactic.source.Position

import java.time.Instant

trait UpdatesResources {
  self: DirectorSpec & ResourceSpec & NamespacedTests & RegistryDeviceRequests =>

  def createManyUpdates[T](createRequest: CreateUpdateRequest)(
    fn: => T)(implicit ns: Namespace, pos: Position): T =
    Post(apiUri("updates/devices"), createRequest).namespaced ~> routes ~> check(fn)

  def createUpdateOk(
    deviceId: DeviceId,
    mtu: TargetUpdateSpec,
    scheduledFor: Option[Instant] = None)(implicit ns: Namespace, pos: Position): UpdateId = {

    val deviceT = genDeviceT.generate.copy(uuid = Some(deviceId))

    createDeviceInNamespaceOk(deviceT, ns)

    val req =
      CreateDeviceUpdateRequest(mtu.targets, scheduledFor)

    Post(apiUri(s"updates/devices/${deviceId.show}"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[UpdateId]
    }
  }

  def listUpdatesOK(
    deviceId: DeviceId)(implicit ns: Namespace, pos: Position): PaginationResult[UpdateResponse] =
    Get(apiUri(s"updates/devices/${deviceId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[PaginationResult[UpdateResponse]]
    }

  def cancelUpdateOK(deviceId: DeviceId,
                     updateId: UpdateId)(implicit ns: Namespace, pos: Position): Unit =
    Patch(
      apiUri(s"updates/devices/${deviceId.show}/${updateId.show}")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

  def getUpdateDetailOK(
    deviceId: DeviceId,
    updateId: UpdateId)(implicit ns: Namespace, pos: Position): UpdateDetailResponse =
    Get(apiUri(s"updates/devices/${deviceId.show}/${updateId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[UpdateDetailResponse]
    }

  def cancelAllUpdatesOK(updateId: UpdateId)(implicit ns: Namespace, pos: Position): Unit = {
    Patch(apiUri(s"updates/${updateId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  def listAllUpdatesOK(limit: Long = 100, offset: Long = 0)
                      (implicit ns: Namespace, pos: Position): PaginationResult[UpdateResponse] =
    Get(apiUri(s"updates?limit=${limit}&offset=${offset}"))
      .namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[PaginationResult[UpdateResponse]]
      }

}
