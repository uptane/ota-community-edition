package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.data.AdminDataType.RegisterDevice
import com.advancedtelematic.director.data.DeviceRequest.DeviceManifest
import com.advancedtelematic.director.data.Generators.GenRegisterEcu
import com.advancedtelematic.director.util.{DirectorSpec, ResourceSpec}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import io.circe.Codec
import org.scalactic.source.Position
import com.advancedtelematic.director.data.GeneratorOps.*
import cats.syntax.show.*
import cats.syntax.option.*
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.libtuf.data.TufCodecs.*

trait ProvisionedDevicesRequests {
  self: DirectorSpec & ResourceSpec =>

  def registerDeviceOk()(implicit namespace: Namespace, pos: Position): DeviceId = {
    val deviceId = DeviceId.generate()
    registerDeviceOk(deviceId)
  }

  def registerDeviceOk(id: DeviceId)(implicit namespace: Namespace, pos: Position): DeviceId = {
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial

    val req = RegisterDevice(id.some, primaryEcu, Seq(ecus))

    Post(apiUri(s"device/${id.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    id
  }

  def getDeviceRole[T](deviceId: DeviceId, version: Option[Int] = None)(
    implicit namespace: Namespace,
    tufRole: TufRole[T]): RouteTestResult = {
    val versionStr = version.map(v => s"$v.").getOrElse("")
    Get(
      apiUri(s"device/${deviceId.show}/$versionStr${tufRole.metaPath.value}")
    ).namespaced ~> routes
  }

  def getDeviceRoleOk[T: Codec](deviceId: DeviceId, version: Option[Int] = None)(
    implicit namespace: Namespace,
    pos: Position,
    tufRole: TufRole[T]): SignedPayload[T] =
    getDeviceRole[T](deviceId, version) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[T]]
    }

  def putManifest[T](deviceId: DeviceId, manifest: SignedPayload[DeviceManifest])(fn: => T)(
    implicit ns: Namespace): T =
    Put(apiUri(s"device/${deviceId.show}/manifest"), manifest).namespaced ~> routes ~> check(fn)

  def putManifestOk(deviceId: DeviceId, manifest: SignedPayload[DeviceManifest])(
    implicit ns: Namespace,
    pos: Position): Unit =
    putManifest(deviceId, manifest) {
      status shouldBe StatusCodes.OK
    }

  def fetchRoleOk[T: Codec](
    deviceId: DeviceId)(implicit ns: Namespace, tufRole: TufRole[T]): SignedPayload[T] =
    Get(apiUri(s"device/${deviceId.show}/${tufRole.metaPath}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[T]]
    }

}
