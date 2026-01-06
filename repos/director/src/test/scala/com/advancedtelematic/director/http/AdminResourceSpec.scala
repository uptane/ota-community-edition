package com.advancedtelematic.director.http

import com.advancedtelematic.libats.data.PaginationResult.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.apache.pekko.http.scaladsl.model.StatusCodes
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.director.data.ClientDataType
import com.advancedtelematic.director.data.AdminDataType.{EcuInfoResponse, FindImageCount, RegisterDevice, TargetUpdateSpec}
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.TargetSpecId
import com.advancedtelematic.director.data.DbDataType.Ecu
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.data.Generators.*
import com.advancedtelematic.director.db.{DbDeviceRoleRepositorySupport, RepoNamespaceRepositorySupport}
import com.advancedtelematic.director.http.AdminResources.RegisterDeviceResult
import com.advancedtelematic.director.util.*
import com.advancedtelematic.libats.codecs.CirceCodecs.*
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier}
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TargetsRole}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, SignedPayload, TargetFilename, TufKey, TufKeyPair}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import org.scalactic.source.Position
import org.scalatest.Assertion
import io.circe.syntax.*

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
  self: DirectorSpec & ResourceSpec & NamespacedTests =>

  def registerAdminDeviceWithSecondariesOk()(
    implicit ns: Namespace,
    pos: Position): RegisterDeviceResult = {
    val device = DeviceId.generate()
    val (regPrimaryEcu, primaryEcuKey) = GenRegisterEcuKeys.generate
    val (regSecondaryEcu, secondaryEcuKey) = GenRegisterEcuKeys.generate

    val regDev =
      RegisterDevice(device.some, regPrimaryEcu.ecu_serial, List(regPrimaryEcu, regSecondaryEcu))

    Post(apiUri("admin/devices"), regDev).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    val ecus = regDev.ecus.map(e => e.ecu_serial -> e.toEcu(ns, regDev.deviceId.get)).toMap
    val primary = ecus(regDev.primary_ecu_serial)

    RegisterDeviceResult(
      regDev.deviceId.get,
      primary,
      primaryEcuKey,
      ecus,
      Map(primary.ecuSerial -> primaryEcuKey, regSecondaryEcu.ecu_serial -> secondaryEcuKey)
    )
  }

  def registerAdminDeviceOk(hardwareIdentifier: Option[HardwareIdentifier] = None,
                            deviceId: DeviceId = DeviceId.generate())(
    implicit ns: Namespace,
    pos: Position): RegisterDeviceResult = {
    val (regEcu, ecuKey) = GenRegisterEcuKeys.generate

    val hwId = hardwareIdentifier.getOrElse(regEcu.hardware_identifier)
    val regDev =
      RegisterDevice(
        deviceId.some,
        regEcu.ecu_serial,
        List(regEcu.copy(hardware_identifier = hwId))
      )

    Post(apiUri("admin/devices"), regDev).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    val ecus = regDev.ecus.map(e => e.ecu_serial -> e.toEcu(ns, regDev.deviceId.get)).toMap
    val primary = ecus(regDev.primary_ecu_serial)

    RegisterDeviceResult(
      regDev.deviceId.get,
      primary,
      ecuKey,
      ecus,
      Map(primary.ecuSerial -> ecuKey)
    )
  }

  def createRepoOk()(implicit ns: Namespace, pos: Position): Assertion =
    Post(apiUri("admin/repo")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

  def createMtu(mtu: TargetUpdateSpec)(implicit ns: Namespace): RouteTestResult =
    Post(apiUri("multi_target_updates"), mtu).namespaced ~> routes

  def createMtuOk()(implicit ns: Namespace, pos: Position): TargetSpecId = {
    val mtu = GenMultiTargetUpdateRequest.generate

    createMtu(mtu) ~> check {
      status shouldBe StatusCodes.Created
      responseAs[TargetSpecId]
    }
  }

}

class AdminResourceSpec
    extends DirectorSpec
    with ResourceSpec
    with RepoNamespaceRepositorySupport
    with DbDeviceRoleRepositorySupport
    with AdminResources
    with RepositorySpec
    with ProvisionedDevicesRequests
    with DeviceManifestSpec {

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

  testWithRepo("images/installed_count returns the count of ECUs a given image is installed on") {
    implicit ns =>
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

    Get(
      apiUri(s"admin/devices/${dev.deviceId.show}/ecus/${dev.primary.ecuSerial.value}/public_key")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[TufKey] shouldBe dev.primaryKey.pubkey
    }
  }

  testWithRepo("devices/ecus gives a list of devices and it's ecus when single ecu") {
    implicit ns =>
      val dev = registerAdminDeviceOk()
      val targetUpdate = GenTargetUpdate.generate

      putManifestOk(dev.deviceId, buildPrimaryManifest(dev.primary, dev.primaryKey, targetUpdate))

      Get(apiUri(s"admin/devices/ecus")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val resp = responseAs[PaginationResult[Map[DeviceId, Seq[ClientDataType.Ecu]]]]
        resp.total shouldBe 1
        resp.values should have size 1
        resp.values.head(dev.deviceId).size shouldBe 1
        resp.values.head(dev.deviceId).head.hardwareId shouldBe dev.primary.hardwareId
        resp.values.head(dev.deviceId).head.ecuSerial shouldBe dev.primary.ecuSerial
        resp.values.head(dev.deviceId).head.primary shouldBe true
        resp.values.head(dev.deviceId).head.installedTarget.nonEmpty shouldBe true
      }
  }

  testWithRepo("devices/ecus gives a list of devices and it's ecus when multiple ecus") {
    implicit ns =>
      val dev = registerAdminDeviceWithSecondariesOk()
      val targetUpdate = GenTargetUpdate.generate

      putManifestOk(dev.deviceId, buildPrimaryManifest(dev.primary, dev.primaryKey, targetUpdate))

      Get(apiUri(s"admin/devices/ecus")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val resp = responseAs[PaginationResult[Map[DeviceId, Seq[ClientDataType.Ecu]]]]
        resp.total shouldBe 1
        resp.values should have size 1
        resp.values.head(dev.deviceId).size shouldBe 2
        val primaryEcu = resp.values.head(dev.deviceId).filter(_.primary).head
        primaryEcu.hardwareId shouldBe dev.primary.hardwareId
        primaryEcu.ecuSerial shouldBe dev.primary.ecuSerial
        primaryEcu.primary shouldBe true
        primaryEcu.installedTarget.nonEmpty shouldBe true

        val secondaryEcu = resp.values.head(dev.deviceId).filterNot(_.primary).head
        secondaryEcu.hardwareId shouldBe secondaryEcu.hardwareId
        secondaryEcu.ecuSerial shouldBe secondaryEcu.ecuSerial
        secondaryEcu.primary shouldBe false
        secondaryEcu.installedTarget.nonEmpty shouldBe false
      }
  }

  testWithRepo("devices/id gives a list of ecu responses") { implicit ns =>
    val dev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdate.generate

    putManifestOk(dev.deviceId, buildPrimaryManifest(dev.primary, dev.primaryKey, targetUpdate))

    Get(apiUri(s"admin/devices/${dev.deviceId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[Vector[EcuInfoResponse]]
      resp should have size 1

      resp.head.hardwareId shouldBe dev.primary.hardwareId
      resp.head.id shouldBe dev.primary.ecuSerial
      resp.head.primary shouldBe true
      resp.head.image.filepath shouldBe targetUpdate.target
    }
  }

  testWithRepo("PUT devices/id/targets.json forces refresh of devices targets.json") {
    implicit ns =>
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
      page shouldBe PaginationResult(Seq.empty, 0, 0L.toOffset, 50L.toLimit)
    }
  }

  testWithRepo("only returns devices where the primary ECU has the given hardware ID") {
    implicit ns =>
      val dev = registerAdminDeviceOk()
      val hardwareId = dev.ecus.values.head.hardwareId
      registerAdminDeviceOk()

      Get(
        apiUri(s"admin/devices?primaryHardwareId=${hardwareId.value}")
      ).namespaced ~> routes ~> check {
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

    Get(
      apiUri(s"admin/devices?primaryHardwareId=${regDev0.primary.hardwareId.value}")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val page = responseAs[PaginationResult[ClientDataType.Device]]
      page.values.length shouldBe 2
      page.values.head.id shouldBe regDev1.deviceId
    }
  }

  testWithRepo("GET returns object containing unknown devices") { implicit ns =>
    val deviceId = DeviceId.generate()
    val body = List(deviceId)

    Post(apiUri("admin/devices/list-installed-targets"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val expected = Map(deviceId -> List.empty)
      responseAs[ClientDataType.DevicesCurrentTarget].values shouldBe expected
    }
  }

  testWithRepo("GET devices returns objects for registered devices when target is unknown") {
    implicit ns =>
      val regDev = registerAdminDeviceOk()

      val body = List(regDev.deviceId)

      Post(apiUri("admin/devices/list-installed-targets"), body).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK

        val expected = Map(regDev.deviceId -> List.empty)

        responseAs[ClientDataType.DevicesCurrentTarget].values shouldBe expected
      }
  }

  testWithRepo(
    "POST to list-installed-targets returns object containing current targets for devices with known targets"
  ) { implicit ns =>
    val hwId = GenHardwareIdentifier.generate
    val regDev = registerAdminDeviceOk(Some(hwId))
    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate
    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = true,
      correlationId = correlationId.some
    ).generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to, deviceReport.some)

    val body = List(regDev.deviceId)

    Post(apiUri("admin/devices/list-installed-targets"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val expected = Map(regDev.deviceId -> List.empty)

      responseAs[ClientDataType.DevicesCurrentTarget].values shouldBe expected
    }

    putManifestOk(regDev.deviceId, deviceManifest)

    Post(apiUri("admin/devices/list-installed-targets"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val expected = Map(
        regDev.deviceId -> List(
          ClientDataType.EcuTarget(
            regDev.primary.ecuSerial,
            targetUpdate.to.checksum,
            targetUpdate.to.target,
            hwId,
            true
          )
        )
      )
      responseAs[ClientDataType.DevicesCurrentTarget].values shouldBe expected
    }
  }

  testWithRepo("POST to list-installed-targets returns primary and secondary ecus") { implicit ns =>
//    val hwId = GenHardwareIdentifier.generate
    val regDev = registerAdminDeviceWithSecondariesOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val secondaryUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate
    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = true,
      correlationId = correlationId.some
    ).generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to, deviceReport.some)
    val secondaryManifest = buildSecondaryManifest(
      regDev.primary.ecuSerial,
      regDev.primaryKey,
      regDev.secondaries.head._1,
      regDev.secondaryKeys.head._2,
      Map(
        regDev.primary.ecuSerial -> targetUpdate.to,
        regDev.secondaries.head._1 -> secondaryUpdate.to
      )
    )

    val body = List(regDev.deviceId)

    Post(apiUri("admin/devices/list-installed-targets"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val expected = Map(regDev.deviceId -> List.empty)

      responseAs[ClientDataType.DevicesCurrentTarget].values shouldBe expected
    }

    putManifestOk(regDev.deviceId, deviceManifest)
    putManifestOk(regDev.deviceId, secondaryManifest)

    Post(apiUri("admin/devices/list-installed-targets"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val expectedPrimary =
        ClientDataType.EcuTarget(
          regDev.primary.ecuSerial,
          targetUpdate.to.checksum,
          targetUpdate.to.target,
          regDev.primary.hardwareId,
          true
        )
      val expectedSecondary =
        ClientDataType.EcuTarget(
          regDev.secondaries.head._2.ecuSerial,
          secondaryUpdate.to.checksum,
          secondaryUpdate.to.target,
          regDev.secondaries.head._2.hardwareId,
          false
        )
      responseAs[ClientDataType.DevicesCurrentTarget].values(regDev.deviceId) should contain(
        expectedPrimary
      )
      responseAs[ClientDataType.DevicesCurrentTarget].values(regDev.deviceId) should contain(
        expectedSecondary
      )
    }
  }

}
