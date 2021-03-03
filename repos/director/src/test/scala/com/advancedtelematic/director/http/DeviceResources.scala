package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.data.AdminDataType.RegisterDevice
import com.advancedtelematic.director.data.DeviceRequest.DeviceManifest
import com.advancedtelematic.director.data.Generators.GenRegisterEcu
import com.advancedtelematic.director.util.{DirectorSpec, ResourceSpec, RouteResourceSpec}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import io.circe.{Decoder, Encoder}
import org.scalactic.source.Position
import com.advancedtelematic.director.data.Generators._
import com.advancedtelematic.director.data.GeneratorOps._
import cats.syntax.show._
import cats.syntax.option._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DataType._
import com.advancedtelematic.libtuf.data.TufCodecs._

trait DeviceResources {
  self: DirectorSpec with ResourceSpec with RouteResourceSpec =>

  def registerDeviceOk()(implicit namespace: Namespace, pos: Position): DeviceId = {
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial

    val deviceId = DeviceId.generate()
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    deviceId
  }

  def getDeviceRole[T : Encoder : Decoder](deviceId: DeviceId, version: Option[Int] = None)
                                          (implicit namespace: Namespace, pos: Position, tufRole: TufRole[T]): RouteTestResult = {
    val versionStr = version.map(_ + ".").getOrElse("")
    Get(apiUri(s"device/${deviceId.show}/$versionStr${tufRole.metaPath.value}")).namespaced ~> routes
  }

  def getDeviceRoleOk[T : Encoder : Decoder](deviceId: DeviceId, version: Option[Int] = None)(implicit namespace: Namespace, pos: Position, tufRole: TufRole[T]): SignedPayload[T] = {
    getDeviceRole[T](deviceId, version) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[T]]
    }
  }

  def putManifest[T](deviceId: DeviceId, manifest: SignedPayload[DeviceManifest])(fn: => T)(implicit ns: Namespace, pos: Position)  = {
    Put(apiUri(s"device/${deviceId.show}/manifest"), manifest).namespaced ~> routes ~> check(fn)
  }

  def putManifestOk(deviceId: DeviceId, manifest: SignedPayload[DeviceManifest])(implicit ns: Namespace, pos: Position): Unit = {
    putManifest(deviceId, manifest) {
      status shouldBe StatusCodes.OK
    }
  }

  def fetchRoleOk[T : Encoder : Decoder](deviceId: DeviceId)(implicit ns: Namespace, tufRole: TufRole[T]): SignedPayload[T] = {
    Get(apiUri(s"device/${deviceId.show}/${tufRole.metaPath}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[T]]
    }
  }

}
