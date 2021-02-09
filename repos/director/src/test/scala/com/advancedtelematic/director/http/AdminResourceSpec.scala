package com.advancedtelematic.director.http

import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.http.scaladsl.model.StatusCodes
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.director.data.ClientDataType
import com.advancedtelematic.director.data.AdminDataType.{EcuInfoResponse, FindImageCount, RegisterDevice}
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DbDataType.Ecu
import com.advancedtelematic.director.data.GeneratorOps._
import com.advancedtelematic.director.data.Generators._
import com.advancedtelematic.director.db.{DbSignedRoleRepositorySupport, RepoNamespaceRepositorySupport}
import com.advancedtelematic.director.http.AdminResources.RegisterDeviceResult
import com.advancedtelematic.director.util._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.{EcuIdentifier, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TargetsRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, SignedPayload, TargetFilename, TufKey, TufKeyPair}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.scalactic.source.Position
import org.scalatest.Assertion
import io.circe.syntax._

object AdminResources {
  case class RegisterDeviceResult(deviceId: DeviceId,
                                  primary: Ecu,
                                  primaryKey: TufKeyPair,
                                  ecus: Map[EcuIdentifier, Ecu],
                                  keys: Map[EcuIdentifier, TufKeyPair]) {
    def secondaries: Map[EcuIdentifier, Ecu] = ecus - primary.ecuSerial
    def secondaryKeys: Map[EcuIdentifier, TufKeyPair] = keys - primary.ecuSerial
  }
}

trait AdminResources {
  self: DirectorSpec with RouteResourceSpec with NamespacedTests =>

  def registerAdminDeviceWithSecondariesOk()(implicit ns: Namespace, pos: Position): RegisterDeviceResult = {
    val device = DeviceId.generate
    val (regPrimaryEcu, primaryEcuKey) = GenRegisterEcuKeys.generate
    val (regSecondaryEcu, secondaryEcuKey) = GenRegisterEcuKeys.generate


    val regDev = RegisterDevice(device.some, regPrimaryEcu.ecu_serial, List(regPrimaryEcu, regSecondaryEcu))

    Post(apiUri("admin/devices"), regDev).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    val ecus = regDev.ecus.map { e => e.ecu_serial -> e.toEcu(ns, regDev.deviceId.get) }.toMap
    val primary = ecus(regDev.primary_ecu_serial)

    RegisterDeviceResult(regDev.deviceId.get, primary, primaryEcuKey, ecus, Map(primary.ecuSerial -> primaryEcuKey, regSecondaryEcu.ecu_serial -> secondaryEcuKey))
  }

  def registerAdminDeviceOk(hardwareIdentifier: Option[HardwareIdentifier] = None)(implicit ns: Namespace, pos: Position): RegisterDeviceResult = {
    val device = DeviceId.generate
    val (regEcu, ecuKey) = GenRegisterEcuKeys.generate

    val hwId = hardwareIdentifier.getOrElse(regEcu.hardware_identifier)
    val regDev = RegisterDevice(device.some, regEcu.ecu_serial, List(regEcu.copy(hardware_identifier = hwId)))

    Post(apiUri("admin/devices"), regDev).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    val ecus = regDev.ecus.map { e => e.ecu_serial -> e.toEcu(ns, regDev.deviceId.get) }.toMap
    val primary = ecus(regDev.primary_ecu_serial)

    RegisterDeviceResult(regDev.deviceId.get, primary, ecuKey, ecus, Map(primary.ecuSerial -> ecuKey))
  }

  def createRepoOk()(implicit ns: Namespace, pos: Position): Assertion = {
    Post(apiUri("admin/repo")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }
  }

  def createMtuOk()(implicit ns: Namespace, pos: Position): UpdateId = {
    val mtu = GenMultiTargetUpdateRequest.generate

    Post(apiUri("multi_target_updates"), mtu).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
      responseAs[UpdateId]
    }
  }
}


class AdminResourceSpec extends DirectorSpec
  with RouteResourceSpec
  with RepoNamespaceRepositorySupport
  with DbSignedRoleRepositorySupport with AdminResources with RepositorySpec with DeviceResources with DeviceManifestSpec {

  testWithNamespace("can register a device") { implicit ns =>
    createRepoOk()

    registerAdminDeviceOk()
  }

  testWithNamespace("can fetch root for a namespace") { implicit ns =>
    createRepoOk()
    registerAdminDeviceOk()

    Get(apiUri("admin/repo/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]].signed shouldBe a[RootRole]
    }
  }

  testWithRepo("images/installed_count returns the count of ECUs a given image is installed on") { implicit ns =>
    val dev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdate.generate

    putManifestOk(dev.deviceId, buildPrimaryManifest(dev.primary, dev.primaryKey, targetUpdate))

    val req = FindImageCount(List(targetUpdate.target))

    Post(apiUri(s"admin/images/installed_count"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[Map[TargetFilename, Int]]
      resp(targetUpdate.target) shouldBe 1
    }
  }

  testWithRepo("devices/hardware_identifiers returns all hardware_ids") { implicit ns =>
    val dev = registerAdminDeviceOk()

    Get(apiUri(s"admin/devices/hardware_identifiers")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[PaginationResult[HardwareIdentifier]].values should contain(dev.primary.hardwareId)
    }
  }

  testWithRepo("devices/id/ecus/public_key can get public key") { implicit ns =>
    val dev = registerAdminDeviceOk()

    Get(apiUri(s"admin/devices/${dev.deviceId.show}/ecus/${dev.primary.ecuSerial.value}/public_key")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[TufKey] shouldBe dev.primaryKey.pubkey
    }
  }

  testWithRepo("devices/id gives a list of ecu responses") { implicit ns =>
    val dev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdate.generate

    putManifestOk(dev.deviceId, buildPrimaryManifest(dev.primary, dev.primaryKey, targetUpdate))

    Get(apiUri(s"admin/devices/${dev.deviceId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[Vector[EcuInfoResponse]]
      resp should have size(1)

      resp.head.hardwareId shouldBe dev.primary.hardwareId
      resp.head.id shouldBe dev.primary.ecuSerial
      resp.head.primary shouldBe true
      resp.head.image.filepath shouldBe targetUpdate.target
    }
  }

  testWithRepo("PUT devices/id/targets.json forces refresh of devices targets.json") { implicit ns =>
    val dev = registerAdminDeviceOk()

    Get(apiUri(s"device/${dev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].signed.version shouldBe 1
    }

    Put(apiUri(s"admin/devices/${dev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Accepted
    }

    Get(apiUri(s"device/${dev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].signed.version shouldBe 2
    }
  }

  testWithNamespace("delegates root upload to keyserver") { implicit ns =>
    createRepoOk()

    val oldRoot = Get(apiUri("admin/repo/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]]
    }

    val root = RootRole(Map.empty, Map.empty, 2, Instant.now().truncatedTo(ChronoUnit.SECONDS))
    val signedRoot = SignedPayload(Seq.empty, root, root.asJson)

    Put(apiUri("admin/repo/root"), signedRoot).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val savedRoot = Get(apiUri("admin/repo/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]]
    }

    savedRoot.signed shouldNot be(oldRoot.signed)
    savedRoot.signed shouldBe signedRoot.signed
  }

  testWithRepo("delegates to keyserver to fetch key pair") { implicit ns =>
    val oldRoot = Get(apiUri("admin/repo/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]]
    }

    val keyId = oldRoot.signed.keys.keys.head

    Get(apiUri("admin/repo/private_keys/" + keyId.value)).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val keyPair = responseAs[TufKeyPair]
      keyPair.pubkey.id shouldBe keyId
    }
  }

  testWithRepo("delegates delete key pair to keyserver") { implicit ns =>
    val oldRoot = Get(apiUri("admin/repo/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]]
    }

    val keyId = oldRoot.signed.keys.keys.head

    Delete(apiUri("admin/repo/private_keys/" + keyId.value)).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/private_keys/" + keyId.value)).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  testWithRepo("return empty list for non-existing hardware ID") { implicit ns =>
    Get(apiUri(s"admin/devices?primaryHardwareId=foo")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val page = responseAs[PaginationResult[DeviceId]]
      page shouldBe PaginationResult(Seq.empty, 0, 0, 50)
    }
  }

  testWithRepo("only returns devices where the primary ECU has the given hardware ID") { implicit ns =>
    val dev = registerAdminDeviceOk()
    val hardwareId = dev.ecus.values.head.hardwareId
    registerAdminDeviceOk()

    Get(apiUri(s"admin/devices?primaryHardwareId=${hardwareId.value}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val page = responseAs[PaginationResult[ClientDataType.Device]]
      page.total shouldBe 1
      page.values.length shouldBe 1
      page.values.head.id shouldBe dev.deviceId
    }
  }

  testWithRepo("search by hardwareId returns devices latest first") { implicit ns =>
    val regDev0 = registerAdminDeviceOk()
    Thread.sleep(1000)
    val regDev1 = registerAdminDeviceOk(regDev0.primary.hardwareId.some)

    Get(apiUri(s"admin/devices?primaryHardwareId=${regDev0.primary.hardwareId.value}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val page = responseAs[PaginationResult[ClientDataType.Device]]
      page.values.length shouldBe 2
      page.values.head.id shouldBe regDev1.deviceId
    }
  }
}
