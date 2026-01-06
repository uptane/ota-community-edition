package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.util.ByteString
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.director.daemon.UpdateSchedulerDaemon
import com.advancedtelematic.director.data.AdminDataType
import com.advancedtelematic.director.data.AdminDataType.{EcuInfoResponse, QueueResponse, RegisterDevice, TargetUpdate, TargetUpdateSpec}
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.*
import com.advancedtelematic.director.data.DeviceRequest.{DeviceManifest, EcuManifest, EcuManifestCustom, InstallationReport, InstallationReportEntity, MissingInstallationReport, OperationResult}
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.data.Generators.*
import com.advancedtelematic.director.data.Messages.DeviceManifestReported
import com.advancedtelematic.director.data.UptaneDataType.{FileInfo, Hashes, Image}
import com.advancedtelematic.director.db.deviceregistry.{DeviceRepository, EcuReplacementRepository}
import com.advancedtelematic.director.db.{AssignmentsRepositorySupport, EcuRepositorySupport, UpdateSchedulerDBIO}
import com.advancedtelematic.director.deviceregistry.data.{DeviceGenerators, DeviceStatus}
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.genDeviceT
import com.advancedtelematic.director.http.deviceregistry.RegistryDeviceRequests
import com.advancedtelematic.director.manifest.ResultCodes
import com.advancedtelematic.director.util.*
import com.advancedtelematic.libats.data.DataType.{CorrelationId, HashMethod, MultiTargetUpdateCorrelationId, Namespace, ResultCode, ResultDescription, UpdateCorrelationId}
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, InstallationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId.*
import com.advancedtelematic.libats.messaging_datatype.Messages.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{RemoteSessionsPayload, RootRole, SnapshotRole, SshSessionProperties, TargetsRole, TimestampRole, TufRole}
import com.advancedtelematic.director.http.RemoteSessionRequest
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.Json
import io.circe.parser.parse
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import org.scalatest.Inspectors
import org.scalatest.LoneElement.*
import org.scalatest.OptionValues.*
import io.circe.syntax.*
import org.scalatest.EitherValues.*

import java.time.Instant
import scala.concurrent.Future

class DeviceResourceSpec
    extends DirectorSpec
    with ResourceSpec
    with AdminResources
    with AssignmentResources
    with EcuRepositorySupport
    with DeviceManifestSpec
    with RepositorySpec
    with Inspectors
    with RegistryDeviceRequests
    with ProvisionedDevicesRequests
    with AssignmentsRepositorySupport
    with UpdatesResources {

  override implicit val msgPub: MockMessageBus = new MockMessageBus()

  val updateSchedulerIO = new UpdateSchedulerDBIO()

  def forceRoleExpire[T](deviceId: DeviceId)(implicit tufRole: TufRole[T]): Unit = {
    import slick.jdbc.MySQLProfile.api.*
    val sql =
      sql"update device_roles set expires_at = '1970-01-01 00:00:00' where device_id = '#${deviceId.show}' and role = '#${tufRole.roleType.toString}'"
    db.run(sql.asUpdate).futureValue
  }

  private def findReplacedEcus(id: DeviceId): Future[Seq[EcuReplacement]] =
    db.run(EcuReplacementRepository.fetchForDevice(id)).map(_.sortBy(_.eventTime).reverse.toList)

  testWithNamespace("accepts a device registering ecus") { implicit ns =>
    createRepoOk()
    registerDeviceOk()
  }

  testWithRepo("a device can replace its ecus") { implicit ns =>
    val deviceId = createDeviceInNamespaceOk(DeviceGenerators.genDeviceT.generate, ns)
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))

    val ecus2 = GenRegisterEcu.generate
    val primaryEcu2 = ecus2.ecu_serial
    val req2 = RegisterDevice(deviceId.some, primaryEcu2, Seq(ecus2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val ecuReplaced = findReplacedEcus(deviceId).futureValue.loneElement.asInstanceOf[EcuReplaced]
    ecuReplaced.former shouldBe EcuAndHardwareId(primaryEcu, ecus.hardware_identifier.value)
    ecuReplaced.current shouldBe EcuAndHardwareId(primaryEcu2, ecus2.hardware_identifier.value)
  }

  testWithRepo("a device can replace its primary ecu only") { implicit ns =>
    val deviceT = DeviceGenerators.genDeviceT.generate
    val deviceId = createDeviceInNamespaceOk(deviceT, ns)
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val secondaryEcu = GenRegisterEcu.generate
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus, secondaryEcu))

    val ecus2 = GenRegisterEcu.generate
    val primaryEcu2 = ecus2.ecu_serial
    val req2 = RegisterDevice(deviceId.some, primaryEcu2, Seq(ecus2, secondaryEcu))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val ecuReplaced = findReplacedEcus(deviceId).futureValue.map(_.asInstanceOf[EcuReplaced]).head
    ecuReplaced.former shouldBe EcuAndHardwareId(primaryEcu, ecus.hardware_identifier.value)
    ecuReplaced.current shouldBe EcuAndHardwareId(primaryEcu2, ecus2.hardware_identifier.value)
  }

  testWithRepo("a device can replace its primary ecu and one secondary ecu") { implicit ns =>
    val deviceId = createDeviceInNamespaceOk(genDeviceT.generate, ns)
    val (primary, secondary) = (GenRegisterEcu.generate, GenRegisterEcu.generate)
    val (primary2, secondary2) = (GenRegisterEcu.generate, GenRegisterEcu.generate)
    val req = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary))
    val req2 = RegisterDevice(deviceId.some, primary2.ecu_serial, Seq(primary2, secondary2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val secondaryReplacement :: primaryReplacement :: _ =
      findReplacedEcus(deviceId).futureValue.map(_.asInstanceOf[EcuReplaced])

    primaryReplacement.former shouldBe EcuAndHardwareId(
      primary.ecu_serial,
      primary.hardware_identifier.value
    )
    primaryReplacement.current shouldBe EcuAndHardwareId(
      primary2.ecu_serial,
      primary2.hardware_identifier.value
    )
    secondaryReplacement.former shouldBe EcuAndHardwareId(
      secondary.ecu_serial,
      secondary.hardware_identifier.value
    )
    secondaryReplacement.current shouldBe EcuAndHardwareId(
      secondary2.ecu_serial,
      secondary2.hardware_identifier.value
    )
  }

  testWithRepo("a device can replace multiple secondary ecus") { implicit ns =>
    val deviceId = createDeviceInNamespaceOk(genDeviceT.generate, ns)
    val primary = GenRegisterEcu.generate
    val (secondary, otherSecondary) = (GenRegisterEcu.generate, GenRegisterEcu.generate)
    val (secondary2, otherSecondary2) = (GenRegisterEcu.generate, GenRegisterEcu.generate)
    val req =
      RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary, otherSecondary))
    val req2 =
      RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary2, otherSecondary2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val secondaryReplacement :: otherSecondaryReplacement :: _ =
      findReplacedEcus(deviceId).futureValue.map(_.asInstanceOf[EcuReplaced])

    Seq(secondaryReplacement.former, otherSecondaryReplacement.former) should contain only (
      EcuAndHardwareId(secondary.ecu_serial, secondary.hardware_identifier.value),
      EcuAndHardwareId(otherSecondary.ecu_serial, otherSecondary.hardware_identifier.value)
    )
    Seq(secondaryReplacement.current, otherSecondaryReplacement.current) should contain only (
      EcuAndHardwareId(secondary2.ecu_serial, secondary2.hardware_identifier.value),
      EcuAndHardwareId(otherSecondary2.ecu_serial, otherSecondary2.hardware_identifier.value)
    )
  }

  testWithRepo("*only* adding ecus registers no replacement") { implicit ns =>
    val deviceId = DeviceId.generate()
    val (primary, secondary, secondary2) =
      (GenRegisterEcu.generate, GenRegisterEcu.generate, GenRegisterEcu.generate)
    val req = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary))
    val req2 =
      RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary, secondary2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    msgPub.findReceived[EcuReplacement](deviceId.show) shouldBe None
  }

  testWithRepo("*only* removing ecus registers no replacement") { implicit ns =>
    val deviceId = DeviceId.generate()
    val (primary, secondary, secondary2) =
      (GenRegisterEcu.generate, GenRegisterEcu.generate, GenRegisterEcu.generate)
    val req = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary, secondary2))
    val req2 = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    msgPub.findReceived[EcuReplacement](deviceId.show) shouldBe None
  }

  testWithRepo("a device ecu replacement is rejected if disabled") { implicit ns =>
    lazy val ecuReplacementDisabledRoutes =
      new DirectorRoutes(keyserverClient, allowEcuReplacement = false).routes

    val deviceId = DeviceId.generate()
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))
    val ecus2 = GenRegisterEcu.generate
    val req2 = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus2))

    Post(
      apiUri(s"device/${deviceId.show}/ecus"),
      req
    ).namespaced ~> ecuReplacementDisabledRoutes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(
      apiUri(s"device/${deviceId.show}/ecus"),
      req2
    ).namespaced ~> ecuReplacementDisabledRoutes ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.EcuReplacementDisabled
    }

    msgPub.findReceived[EcuReplacement](deviceId.show) shouldBe None
  }

  testWithRepo(
    "registering the same device id with different ecus works when using the same primary ecu"
  ) { implicit ns =>
    val deviceId = DeviceId.generate()
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))

    val ecus2 = GenRegisterEcu.generate
    val req2 = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus, ecus2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    msgPub.findReceived[EcuReplacement](deviceId.show) shouldBe None
  }

  testWithRepo(
    "A device can replace ECUs if an assignment exists, if the new ECU list contains all ecus with assignments"
  ) { implicit ns =>
    val deviceId = DeviceId.generate()
    val ((primary, primaryKeys), secondary) = (GenRegisterEcuKeys.generate, GenRegisterEcu.generate)
    val (secondary2, secondary2Keys) = GenRegisterEcuKeys.generate
    val targetUpdate = GenTargetUpdateRequest.generate
    val req = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary))
    val req2 = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    createDeviceAssignmentOk(deviceId, primary.hardware_identifier, targetUpdate.some)

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val deviceManifest = buildSecondaryManifest(
      primary.ecu_serial,
      primaryKeys,
      secondary2.ecu_serial,
      secondary2Keys,
      Map(primary.ecu_serial -> targetUpdate.to, secondary2.ecu_serial -> targetUpdate.to)
    )
    putManifestOk(deviceId, deviceManifest)

    Get(apiUri(s"admin/devices/${deviceId.show}/ecus")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[Vector[EcuInfoResponse]]

      resp.size shouldBe 2
      resp.find(_.primary).map(_.id) should contain(primary.ecu_serial)
      resp.find(_.primary == false).map(_.id) should contain(secondary2.ecu_serial)
    }
  }

  testWithRepo("a device can replace a secondary and POST manifests for the new ECUs") {
    implicit ns =>
      val dev = registerAdminDeviceWithSecondariesOk()
      val targetUpdate = GenTargetUpdateRequest.generate
      val secondarySerial = dev.secondaries.keys.head
      val secondaryKey = dev.secondaryKeys(secondarySerial)

      val deviceManifest = buildSecondaryManifest(
        dev.primary.ecuSerial,
        dev.primaryKey,
        secondarySerial,
        secondaryKey,
        Map(dev.primary.ecuSerial -> targetUpdate.to, secondarySerial -> targetUpdate.to)
      )
      putManifestOk(dev.deviceId, deviceManifest)

      val (ecus2, ecu2Keys) = GenRegisterEcuKeys.generate
      val regPrimaryEcu = AdminDataType.RegisterEcu(
        dev.primary.ecuSerial,
        dev.primary.hardwareId,
        dev.primaryKey.pubkey
      )
      val req2 = RegisterDevice(dev.deviceId.some, dev.primary.ecuSerial, Seq(regPrimaryEcu, ecus2))

      Post(apiUri(s"device/${dev.deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      putManifestOk(
        dev.deviceId,
        buildSecondaryManifest(
          dev.primary.ecuSerial,
          dev.primaryKey,
          ecus2.ecu_serial,
          ecu2Keys,
          Map(dev.primary.ecuSerial -> targetUpdate.to, ecus2.ecu_serial -> targetUpdate.to)
        )
      )

      Get(apiUri(s"admin/devices/${dev.deviceId.show}")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val info = responseAs[Vector[EcuInfoResponse]]
        info should have size 2
        info.map(_.id) should contain(dev.primary.ecuSerial)
        info.map(_.id) should contain(ecus2.ecu_serial)
      }
  }

  testWithRepo(
    "replacing ecus fails when device has running assignments and new ecu list does not include all ECUs with assignments"
  ) { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = createDeviceInNamespaceOk(genDeviceT.generate, ns)
    val ecus = GenRegisterEcu.generate
    val ecus2 = GenRegisterEcu.generate
    val ecus3 = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val regDev = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus, ecus2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), regDev).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    createDeviceAssignmentOk(deviceId, ecus2.hardware_identifier, targetUpdate.some)

    val req2 = RegisterDevice(
      deviceId.some,
      primaryEcu,
      Seq(ecus, ecus3)
    ) // Remove ecu2 from list of ecus, add ecu3

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.PreconditionFailed
      val resp = responseAs[ErrorRepresentation]
      resp.description should include(s"Cannot replace ecus for $deviceId")
      resp.code shouldBe ErrorCodes.ReplaceEcuAssignmentExists
    }

    db.run(DeviceRepository.findByUuid(deviceId)).futureValue.deviceStatus shouldBe DeviceStatus.Error
  }

  testWithRepo("Previously used *secondary* ecus cannot be reused when replacing ecus") {
    implicit ns =>
      val deviceId = DeviceId.generate()
      val registerEcu = GenRegisterEcu.generate
      val registerEcu2 = GenRegisterEcu.generate
      val primaryEcu = registerEcu.ecu_serial
      val regDev = RegisterDevice(deviceId.some, primaryEcu, Seq(registerEcu, registerEcu2))

      Post(apiUri(s"device/${deviceId.show}/ecus"), regDev).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }

      val req2 = RegisterDevice(deviceId.some, primaryEcu, Seq(registerEcu))
      Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      val req3 = RegisterDevice(deviceId.some, primaryEcu, Seq(registerEcu, registerEcu2))
      Post(apiUri(s"device/${deviceId.show}/ecus"), req3).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[ErrorRepresentation].code shouldBe ErrorCodes.EcuReuseError
      }

      val deviceEcus = ecuRepository.findBy(deviceId).futureValue

      deviceEcus.map(_.ecuSerial) should contain only primaryEcu
  }

  testWithRepo("fails when primary ecu is not defined in ecus") { implicit ns =>
    val ecus = GenRegisterEcu.generate
    val primaryEcu = GenEcuIdentifier.generate
    val deviceId = DeviceId.generate()
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.PrimaryIsNotListedForDevice.code
    }

    deviceId
  }

  testWithRepo("targets.json is empty after register") { implicit ns =>
    val deviceId = registerDeviceOk()

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signedPayload = responseAs[SignedPayload[TargetsRole]].signed
      signedPayload.targets shouldBe empty
    }
  }

  testWithRepo("fetches a root.json for a device") { implicit ns =>
    val deviceId = registerDeviceOk()

    Get(apiUri(s"device/${deviceId.show}/1.root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]].signed shouldBe a[RootRole]
    }
  }

  testWithRepo("can GET root.json without specifying version") { implicit ns =>
    val deviceId = registerDeviceOk()

    Get(apiUri(s"device/${deviceId.show}/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RootRole]].signed shouldBe a[RootRole]
    }
  }

  testWithRepo("can GET timestamp") { implicit ns =>
    val deviceId = registerDeviceOk()

    Get(apiUri(s"device/${deviceId.show}/timestamp.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TimestampRole]].signed shouldBe a[TimestampRole]
    }
  }

  testWithRepo("can get snapshots") { implicit ns =>
    val deviceId = registerDeviceOk()

    Get(apiUri(s"device/${deviceId.show}/snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[SnapshotRole]].signed shouldBe a[SnapshotRole]
    }
  }

  testWithRepo("GET on targets.json contains target after assignment") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId
    createDeviceAssignmentOk(deviceId, regDev.primary.hardwareId, targetUpdate.some)

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed
      targets.version shouldBe 2
      targets.targets.keys should contain(targetUpdate.to.target)
      targets.targets(targetUpdate.to.target).hashes.values should contain(
        targetUpdate.to.checksum.hash
      )
      targets.targets(targetUpdate.to.target).length shouldBe targetUpdate.to.targetLength
    }
  }

  testWithRepo("GET on targets.json contains user defined json") { implicit ns =>
    val userCustom = Json.obj("mydata" -> Json.fromInt(1))
    val toTarget = GenTargetUpdate.generate.copy(userDefinedCustom = Some(userCustom))
    val targetUpdateReq = GenTargetUpdateRequest.generate.copy(to = toTarget)
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId

    createDeviceAssignmentOk(deviceId, regDev.primary.hardwareId, targetUpdateReq.some)

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed
      targets.version shouldBe 2

      val target = targets.targets.head._2

      target.custom.value.hcursor
        .get[Json]("userDefinedCustom")
        .value shouldBe userCustom
    }
  }

  testWithRepo(
    "user defined metadata is an array when more than one ECU is assigned to the same target"
  ) { implicit ns =>
    val userCustom = Json.obj("mydata" -> Json.fromInt(1))
    val toTarget = GenTargetUpdate.generate.copy(userDefinedCustom = Some(userCustom))
    val targetUpdateReq = GenTargetUpdateRequest.generate.copy(to = toTarget)

    val regDev = registerAdminDeviceWithSecondariesOk()
    val (_, secondaryEcu) = (regDev.ecus - regDev.primary.ecuSerial).head
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      deviceId,
      regDev.primary.hardwareId,
      targetUpdateReq.some,
      correlationId.some
    )
    createDeviceAssignmentOk(
      deviceId,
      secondaryEcu.hardwareId,
      targetUpdateReq.some,
      correlationId.some
    )

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed

      val target = targets.targets.head._2

      target.custom.value.hcursor
        .get[Json]("userDefinedCustom")
        .value shouldBe userCustom
    }
  }

  testWithRepo("device can PUT a valid manifest") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to)

    putManifestOk(regDev.deviceId, deviceManifest)
  }

  testWithRepo("device queue is cleared after successful PUT manifest") { implicit ns =>
    val registerDevice = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = registerDevice.deviceId
    createDeviceAssignmentOk(
      registerDevice.deviceId,
      registerDevice.primary.hardwareId,
      targetUpdate.some
    )

    val deviceManifest =
      buildPrimaryManifest(registerDevice.primary, registerDevice.primaryKey, targetUpdate.to)

    putManifestOk(deviceId, deviceManifest)

    Get(apiUri(s"assignments/${deviceId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[List[Json]] shouldBe empty
    }
  }

  testWithRepo("fails when manifest is not properly signed by primary") { implicit ns =>
    val registerDevice = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = registerDevice.deviceId
    val key = GenKeyType.generate.crypto.generateKeyPair()

    val deviceManifest = buildPrimaryManifest(registerDevice.primary, key, targetUpdate.to)

    Put(apiUri(s"device/${deviceId.show}/manifest"), deviceManifest).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.Manifest.SignatureNotValid
    }
  }

  testWithRepo("accepts manifest signed by secondary and primary") { implicit ns =>
    val regDev = registerAdminDeviceWithSecondariesOk()
    val (secondary, secondaryKey) = regDev.secondaryKeys.head
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceManifest = buildSecondaryManifest(
      regDev.primary.ecuSerial,
      regDev.primaryKey,
      secondary,
      secondaryKey,
      Map(regDev.primary.ecuSerial -> targetUpdate.to, secondary -> targetUpdate.to)
    )

    putManifestOk(regDev.deviceId, deviceManifest)
  }

  testWithRepo("fails when manifest is not properly signed by secondary") { implicit ns =>
    val regDev = registerAdminDeviceWithSecondariesOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val (secondary, realKey) = regDev.secondaryKeys.head
    val secondaryKey = GenTufKeyPair.generate
    val deviceManifest = buildSecondaryManifest(
      regDev.primary.ecuSerial,
      regDev.primaryKey,
      secondary,
      secondaryKey,
      Map(regDev.primary.ecuSerial -> targetUpdate.to, secondary -> targetUpdate.to)
    )

    Put(
      apiUri(s"device/${regDev.deviceId.show}/manifest"),
      deviceManifest
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.Manifest.SignatureNotValid
      responseAs[ErrorRepresentation].description should include(
        s"not signed with key ${realKey.pubkey.id}"
      )
    }
  }

  testWithRepo("returns exact same targets.json if assignments did not change") { implicit ns =>
    import com.advancedtelematic.libtuf.crypt.CanonicalJson._
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some)

    val firstTargets =
      Get(apiUri(s"device/${regDev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }

    Thread.sleep(1000)

    val secondTargets =
      Get(apiUri(s"device/${regDev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }

    if (firstTargets.json.canonical != secondTargets.json.canonical)
      fail(s"targets.json $firstTargets is not the same as $secondTargets")
  }

  testWithRepo("returns canonicalized targets.json") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some)

    val responseBody =
      Get(apiUri(s"device/${regDev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[ByteString].utf8String
      }

    val parsedJson = parse(responseBody).fold(
      error => fail(s"Failed to parse targets.json response: ${error.getMessage}"),
      identity
    )

    val canonicalForm = parsedJson.canonical
    responseBody shouldBe canonicalForm
  }

  testWithRepo("returns canonicalized root.json") { implicit ns =>
    val deviceId = registerDeviceOk()

    val responseBody =
      Get(apiUri(s"device/${deviceId.show}/root.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[ByteString].utf8String
      }

    val parsedJson = parse(responseBody).fold(
      error => fail(s"Failed to parse root.json response: ${error.getMessage}"),
      identity
    )

    val canonicalForm = parsedJson.canonical
    responseBody shouldBe canonicalForm
  }

  testWithRepo("returns canonicalized n.root.json") { implicit ns =>
    val deviceId = registerDeviceOk()

    val responseBody =
      Get(apiUri(s"device/${deviceId.show}/1.root.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[ByteString].utf8String
      }

    val parsedJson = parse(responseBody).fold(
      error => fail(s"Failed to parse 1.root.json response: ${error.getMessage}"),
      identity
    )

    val canonicalForm = parsedJson.canonical
    responseBody shouldBe canonicalForm
  }

  testWithRepo("returns canonicalized remote-sessions.json") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId

    // Create a remote session first so the endpoint has something to return
    val session = RemoteSessionsPayload(
      SshSessionProperties("someapiversion", Map.empty, Vector.empty, Vector.empty),
      "someapiversion"
    )
    val body = RemoteSessionRequest(session)
    Post(apiUri(s"admin/remote-sessions"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val responseBody =
      Get(apiUri(s"device/${deviceId.show}/remote-sessions.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[ByteString].utf8String
      }

    val parsedJson = parse(responseBody).fold(
      error => fail(s"Failed to parse remote-sessions.json response: ${error.getMessage}"),
      identity
    )

    val canonicalForm = parsedJson.canonical
    responseBody shouldBe canonicalForm
  }

  testWithRepo("returns a refreshed version of targets if it expires") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val firstTargets = fetchRoleOk[TargetsRole](regDev.deviceId)

    forceRoleExpire[TargetsRole](regDev.deviceId)

    val secondTargets = fetchRoleOk[TargetsRole](regDev.deviceId)

    secondTargets.signed.expires.isAfter(firstTargets.signed.expires)
    firstTargets.signed.version shouldBe 1
    secondTargets.signed.version shouldBe 2
  }

  testWithRepo(
    "a refreshed targets returns the same assignments as before, even if they were completed"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val targetUpdate = GenTargetUpdateRequest.generate
    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some)

    val firstTargets = fetchRoleOk[TargetsRole](regDev.deviceId)

    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to)

    putManifestOk(regDev.deviceId, deviceManifest)

    forceRoleExpire[TargetsRole](regDev.deviceId)

    val secondTargets = fetchRoleOk[TargetsRole](regDev.deviceId)

    secondTargets.signed.expires.isAfter(firstTargets.signed.expires)
    secondTargets.signed.targets shouldBe firstTargets.signed.targets
  }

  testWithRepo("returns a refreshed version of snapshots if it expires") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val first = fetchRoleOk[SnapshotRole](regDev.deviceId)

    forceRoleExpire[SnapshotRole](regDev.deviceId)

    val second = fetchRoleOk[SnapshotRole](regDev.deviceId)

    second.signed.expires.isAfter(first.signed.expires)
    first.signed.version shouldBe 1
    second.signed.version shouldBe 2
  }

  testWithRepo("returns a refreshed version of timestamps if it expires") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val first = fetchRoleOk[TimestampRole](regDev.deviceId)

    forceRoleExpire[TimestampRole](regDev.deviceId)

    val second = fetchRoleOk[TimestampRole](regDev.deviceId)

    second.signed.expires.isAfter(first.signed.expires)
    first.signed.version shouldBe 1
    second.signed.version shouldBe 2
  }

  testWithRepo(
    "moves queue status to inflight = true after device gets targets containing assignment"
  ) { implicit ns =>
    val registerDevice = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = registerDevice.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(
      registerDevice.deviceId,
      registerDevice.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    getDeviceRoleOk[TargetsRole](deviceId)

    getDeviceAssignment(deviceId) {
      status shouldBe StatusCodes.OK
      val firstQueueItem = responseAs[List[QueueResponse]].head

      firstQueueItem
        .targets(registerDevice.primary.ecuSerial)
        .image
        .filepath shouldBe targetUpdate.to.target
      firstQueueItem.inFlight shouldBe true
      firstQueueItem.correlationId shouldBe correlationId
    }
  }

  testWithRepo("correlationId is included in a targets role custom field") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(
      deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed

      targets.custom.flatMap(
        _.as[DeviceTargetsCustom].toOption.flatMap(_.correlationId)
      ) should contain(correlationId)
    }
  }

  testWithRepo("ecu custom includes custom metadata") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.retryUntil(_.to.uri.isDefined).generate
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(
      deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed
      val item = targets.targets.head._2
      val custom = item.custom.flatMap(_.as[TargetItemCustom].toOption)

      custom.map(_.ecuIdentifiers) should contain(
        Map(regDev.primary.ecuSerial -> TargetItemCustomEcuData(regDev.primary.hardwareId))
      )
      custom.flatMap(_.uri) shouldBe targetUpdate.to.uri
    }
  }

  testWithRepo(
    "custom metadata includes targets per ecu when more than one ECU is assigned to the same target"
  ) { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val regDev = registerAdminDeviceWithSecondariesOk()
    val (secondaryEcuSerial, secondaryEcu) = (regDev.ecus - regDev.primary.ecuSerial).head
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(
      deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )
    createDeviceAssignmentOk(
      deviceId,
      secondaryEcu.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed
      val item = targets.targets.head._2
      val custom = item.custom.flatMap(_.as[TargetItemCustom].toOption).map(_.ecuIdentifiers)

      custom.flatMap(_.get(regDev.primary.ecuSerial)) should contain(
        TargetItemCustomEcuData(regDev.primary.hardwareId)
      )
      custom.flatMap(_.get(secondaryEcuSerial)) should contain(
        TargetItemCustomEcuData(secondaryEcu.hardwareId)
      )
    }
  }

  testWithRepo("device gets logged when fetching root") { implicit ns =>
    val deviceId = registerDeviceOk()

    getDeviceRoleOk[RootRole](deviceId)

    val deviceSeenMsg = msgPub.findReceived[DeviceSeen](deviceId.toString)
    deviceSeenMsg.map(_.namespace) should contain(ns)
  }

  testWithRepo("device gets logged when fetching root with version") { implicit ns =>
    val deviceId = registerDeviceOk()

    getDeviceRoleOk[RootRole](deviceId, version = 1.some)

    val deviceSeenMsg = msgPub.findReceived[DeviceSeen](deviceId.toString)
    deviceSeenMsg.map(_.namespace) should contain(ns)
  }

  testWithRepo("publishes DeviceUpdateInFlight message") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    fetchRoleOk[TargetsRole](regDev.deviceId)

    val reportMsg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid === regDev.deviceId
      }
      .map(_.asInstanceOf[DeviceUpdateInFlight])
      .value

    reportMsg.namespace shouldBe ns
    reportMsg.correlationId shouldBe correlationId
  }

  testWithRepo("publishes DeviceUpdateCompleted message") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate
    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = true,
      correlationId = correlationId.some
    ).generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to, deviceReport.some)

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    putManifestOk(regDev.deviceId, deviceManifest)

    val reportMsg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid === regDev.deviceId
      }
      .map(_.asInstanceOf[DeviceUpdateCompleted])

    reportMsg.map(_.namespace) should contain(ns)

    reportMsg.get.result shouldBe deviceReport.result
    val (ecuReportId, ecuReport) = reportMsg.get.ecuReports.head
    ecuReportId shouldBe regDev.primary.ecuSerial
    ecuReport.result shouldBe deviceReport.items.head.result
    reportMsg.get.correlationId shouldBe correlationId
  }

  testWithRepo(
    "publishes DeviceUpdateCompleted message if device sends no install report, but ecu sends an ecu report"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    val image = Image(
      targetUpdate.to.target,
      FileInfo(Hashes(targetUpdate.to.checksum), targetUpdate.to.targetLength)
    )
    val ecuInstallResult = OperationResult("0", 0, "some description")
    val ecuManifest = sign(
      regDev.primaryKey,
      EcuManifest(
        image,
        regDev.primary.ecuSerial,
        "",
        custom = EcuManifestCustom(ecuInstallResult).asJson.some
      )
    )
    val deviceManifest = sign(
      regDev.primaryKey,
      DeviceManifest(
        regDev.primary.ecuSerial,
        Map(regDev.primary.ecuSerial -> ecuManifest),
        installation_report = Left(MissingInstallationReport)
      )
    )

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    putManifestOk(regDev.deviceId, deviceManifest)

    val reportMsg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid === regDev.deviceId
      }
      .map(_.asInstanceOf[DeviceUpdateCompleted])
      .value

    reportMsg.namespace shouldBe ns
    reportMsg.result shouldBe InstallationResult(
      success = true,
      ResultCode("0"),
      ResultDescription("All targeted ECUs were successfully updated")
    )

    val (ecuReportId, ecuReport) = reportMsg.ecuReports.head
    ecuReportId shouldBe regDev.primary.ecuSerial
    ecuReport.result shouldBe InstallationResult(
      success = true,
      ResultCode("0"),
      ResultDescription("some description")
    )
  }

  testWithRepo("fails with EcuNotPrimary if device declares wrong primary") { implicit ns =>
    val regDev = registerAdminDeviceWithSecondariesOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val secondarySerial = regDev.secondaries.keys.head
    val secondaryKey = regDev.secondaryKeys(secondarySerial)
    val deviceManifest = buildSecondaryManifest(
      secondarySerial,
      regDev.primaryKey,
      secondarySerial,
      secondaryKey,
      Map(secondarySerial -> targetUpdate.to)
    )

    putManifest(regDev.deviceId, deviceManifest) {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.Manifest.EcuNotPrimary
    }
  }

  testWithRepo(
    "device updates to same target we know it has installed but without install report"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId).signed
    targetsAfter.targets shouldNot be(empty)
    targetsAfter.targets.get(targetUpdate.to.target) shouldBe defined
    targetsAfter.targets.get(targetUpdate.to.target).map(_.length) should contain(
      targetUpdate.to.targetLength
    )
  }

  testWithRepo(
    "device updates to same target we know it has installed with failed install report"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )
    getDeviceRoleOk[TargetsRole](regDev.deviceId).signed.targets shouldNot be(empty)

    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = false,
      correlationId = correlationId.some
    ).generate
    val deviceManifestAfterTrying =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId).signed
    targetsAfter.targets shouldBe empty

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue
    processed.head.result shouldNot be(empty)
    processed.head.canceled shouldBe true
    processed.head.successful shouldBe false
  }

  // keep old director behavior
  // Status should not change, since there is no report, nothing should be published
  testWithRepo(
    "device updates to some unknown target without an installation report should be NOOP"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    val unknownVersion = GenTargetUpdateRequest.generate

    val deviceManifestAfterTrying =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, unknownVersion.to)
    val targetsBefore = getDeviceRoleOk[TargetsRole](regDev.deviceId)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter shouldBe targetsBefore

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue
    processed shouldBe empty
  }

  // Keep old director behavior, clean targets
  testWithRepo(
    "device updates to some unknown target with with a failed installation report empties targets"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )
    val targetsBefore = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = false,
      correlationId = correlationId.some
    ).generate
    val unknownUpdate = GenTargetUpdateRequest.generate
    val deviceManifestAfterTrying =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, unknownUpdate.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter.signed.targets shouldBe empty

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue
    processed.head.result shouldNot be(empty)
  }

  testWithRepo(
    "device updates to an unknown target with a success installation report, cancels existing assignments"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )
    val targetsBefore = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val unknownCorrelationId = GenCorrelationId.generate
    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = true,
      correlationId = unknownCorrelationId.some
    ).generate
    val unknownUpdate = GenTargetUpdateRequest.generate
    val deviceManifestAfterTrying =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, unknownUpdate.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter.signed.targets shouldBe empty

    val processed =
      assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue.headOption.value
    processed.correlationId shouldBe correlationId
    processed.canceled shouldBe true
    processed.successful shouldBe false

    val allMessages = msgPub.findReceivedAll { (msg: DeviceUpdateEvent) =>
      msg.deviceUuid === regDev.deviceId
    }

    // message for known/assigned update
    forExactly(1, allMessages) { msg =>
      msg shouldBe a[DeviceUpdateCompleted]
      msg.correlationId shouldBe correlationId
      msg
        .asInstanceOf[DeviceUpdateCompleted]
        .result
        .code shouldBe ResultCodes.DirectorCancelledAssignment
    }

    // also gets a message for unknown update
    forExactly(1, allMessages) { msg =>
      msg shouldBe a[DeviceUpdateCompleted]
      msg.correlationId shouldBe unknownCorrelationId
      msg.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
    }
  }

  testWithRepo("publishes bus message when manifest is received") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val msg = msgPub.findReceived[DeviceManifestReported](regDev.deviceId.show)

    msg.value.manifest.asJsonSignedPayload shouldBe deviceManifest.asJsonSignedPayload
  }

  testWithRepo(
    "assignments are completed if installation report is invalid, but assigned update is reported as the current installed version"
  ) { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )

    val deviceReport =
      GenInstallReport(regDev.primary.ecuSerial, success = true, correlationId = None).generate

    val ecuManifest =
      sign(regDev.primaryKey, buildEcuManifest(regDev.primary.ecuSerial, targetUpdate.to))
    val report = InstallationReportEntity("mock-content-type", deviceReport)

    val manifestJson = DeviceManifest(
      regDev.primary.ecuSerial,
      Map(regDev.primary.ecuSerial -> ecuManifest),
      installation_report = Right(report)
    ).asJson

    val invalidManifest = manifestJson.hcursor
      .downField("installation_report")
      .downField("report")
      .downField("correlation_id")
      .withFocus(_.mapString(_ => "invalid-correlation-id"))
      .top

    val newManifest = sign(regDev.primaryKey, invalidManifest)

    Put(
      apiUri(s"device/${regDev.deviceId.show}/manifest"),
      newManifest
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter.signed.targets should be(empty)

    assignmentsRepository.findBy(regDev.deviceId).futureValue shouldBe empty
    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue.loneElement
    processed.correlationId shouldBe correlationId
    processed.canceled shouldBe false
    processed.successful shouldBe false

    val reportMsg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid === regDev.deviceId
      }
      .map(_.asInstanceOf[DeviceUpdateCompleted])

    reportMsg.value.result.success shouldBe false
    reportMsg.value.result.code shouldBe ResultCodes.DeviceSentInvalidInstallationReport
  }

  testWithRepo("targets.json has the same targets when an update is successful") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      correlationId.some
    )
    val targetsBefore = getTargetsOk(regDev.deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success = true,
      correlationId = correlationId.some
    ).generate
    val deviceManifestAfterTrying =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getTargetsOk(regDev.deviceId)
    val target = targetsAfter.signed.targets.get(targetUpdate.to.target).value

    targetsAfter.signed shouldBe targetsAfter.signed

    target.hashes.loneElement shouldBe (HashMethod.SHA256, targetUpdate.to.checksum.hash)
    target.length shouldBe targetUpdate.to.targetLength

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue.loneElement
    processed.correlationId shouldBe correlationId
    processed.canceled shouldBe false
    processed.successful shouldBe true

    val reportMsg = msgPub
      .findReceived { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == regDev.deviceId
      }
      .map(_.asInstanceOf[DeviceUpdateCompleted])

    reportMsg.value.correlationId shouldBe correlationId
    val ecuReport = reportMsg.value.ecuReports.get(regDev.primary.ecuSerial).value
    ecuReport.result shouldBe deviceReport.result
    ecuReport.target.loneElement shouldBe targetUpdate.to.target.value
  }

  def putManifestOfflineUpdateInstalled(success: Boolean)(
    implicit ns: Namespace): (DeviceId, CorrelationId, CorrelationId, InstallationReport) = {
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = regDev.deviceId
    val existingCorrelationId = GenCorrelationId.generate
    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      existingCorrelationId.some
    )

    val targetsBefore = getTargetsOk(deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val offlineTargetUpdate = GenTargetUpdateRequest.generate
    val offlineUpdateCorrelationId = GenOffLineCorrelationId.generate
    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success,
      correlationId = offlineUpdateCorrelationId.some
    ).generate
    val deviceManifest = buildPrimaryManifest(
      regDev.primary,
      regDev.primaryKey,
      offlineTargetUpdate.to,
      deviceReport.some
    )

    putManifestOk(deviceId, deviceManifest)

    (regDev.deviceId, existingCorrelationId, offlineUpdateCorrelationId, deviceReport)
  }

  def putManifestCurrentAssignmentWithOfflineCorrelationId(success: Boolean)(
    implicit
    ns: Namespace): (DeviceId, CorrelationId, CorrelationId, InstallationReport, TargetUpdate) = {
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = regDev.deviceId
    val existingCorrelationId = GenCorrelationId.generate
    createDeviceAssignmentOk(
      regDev.deviceId,
      regDev.primary.hardwareId,
      targetUpdate.some,
      existingCorrelationId.some
    )

    val targetsBefore = getTargetsOk(regDev.deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val offlineUpdateCorrelationId = GenOffLineCorrelationId.generate
    val deviceReport = GenInstallReport(
      regDev.primary.ecuSerial,
      success,
      correlationId = offlineUpdateCorrelationId.some
    ).generate
    val deviceManifest =
      buildPrimaryManifest(regDev.primary, regDev.primaryKey, targetUpdate.to, deviceReport.some)

    putManifestOk(deviceId, deviceManifest)

    (
      regDev.deviceId,
      existingCorrelationId,
      offlineUpdateCorrelationId,
      deviceReport,
      targetUpdate.to
    )
  }

  testWithRepo("clears assignments when device reports an installed offline update") {
    implicit ns =>
      val (deviceId, existingCorrelationId, offlineUpdateCorrelationId, deviceReport) =
        putManifestOfflineUpdateInstalled(success = true)

      val targetsBefore = getTargetsOk(deviceId)
      targetsBefore.signed.targets shouldBe empty

      val inFlightMsg = msgPub.findReceived { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == deviceId && msg.isInstanceOf[DeviceUpdateInFlight]
      }

      inFlightMsg.value
        .asInstanceOf[DeviceUpdateInFlight]
        .correlationId shouldBe existingCorrelationId

      val targets = getTargetsOk(deviceId)
      targets.signed.targets shouldBe empty

      getDeviceAssignmentOk(deviceId) shouldBe empty

      val allMessages = msgPub.findReceivedAll { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == deviceId
      }

      forExactly(1, allMessages) { report =>
        report.correlationId shouldBe existingCorrelationId
        report shouldBe a[DeviceUpdateCompleted]
        report
          .asInstanceOf[DeviceUpdateCompleted]
          .result
          .code shouldBe ResultCodes.DirectorCancelledAssignment
      }

      forExactly(1, allMessages) { report =>
        report.correlationId shouldBe offlineUpdateCorrelationId
        report shouldBe a[DeviceUpdateCompleted]
        report.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
        report.asInstanceOf[DeviceUpdateCompleted].result.success shouldBe true
      }

  }

  testWithRepo("device sends offline update installation report success = false") { implicit ns =>
    val (deviceId, existingCorrelationId, offlineUpdateCorrelationId, deviceReport) =
      putManifestOfflineUpdateInstalled(success = false)

    val targets = getTargetsOk(deviceId)
    targets.signed.targets shouldBe empty

    getDeviceAssignmentOk(deviceId) shouldBe empty

    val allMessages = msgPub.findReceivedAll { (msg: DeviceUpdateEvent) =>
      msg.deviceUuid == deviceId
    }

    forExactly(1, allMessages) { report =>
      report.correlationId shouldBe existingCorrelationId
      report shouldBe a[DeviceUpdateCompleted]
      report
        .asInstanceOf[DeviceUpdateCompleted]
        .result
        .code shouldBe ResultCodes.DirectorCancelledAssignment
    }

    forExactly(1, allMessages) { report =>
      report.correlationId shouldBe offlineUpdateCorrelationId
      report shouldBe a[DeviceUpdateCompleted]
      report.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
      report.asInstanceOf[DeviceUpdateCompleted].result.success shouldBe false
    }
  }

  testWithRepo("report is for offline update and installed update matches current assignment") {
    implicit ns =>
      val (
        deviceId,
        existingCorrelationId,
        offlineUpdateCorrelationId,
        deviceReport,
        targetUpdate
      ) = putManifestCurrentAssignmentWithOfflineCorrelationId(success = true)

      val targets = getTargetsOk(deviceId)
      targets.signed.targets shouldNot be(empty)
      targets.signed.targets
        .get(targetUpdate.target)
        .value
        .hashes
        .loneElement
        ._2 shouldBe targetUpdate.checksum.hash

      getDeviceAssignmentOk(deviceId) shouldBe empty

      val allMessages = msgPub.findReceivedAll { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == deviceId
      }

      forExactly(1, allMessages) { report =>
        report.correlationId shouldBe existingCorrelationId
        report shouldBe a[DeviceUpdateCompleted]
        report.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
      }

      forExactly(1, allMessages) { report =>
        report.correlationId shouldBe offlineUpdateCorrelationId
        report shouldBe a[DeviceUpdateCompleted]
        report.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
        report.asInstanceOf[DeviceUpdateCompleted].result.success shouldBe true
      }
  }

  testWithRepo("offline update in correlation id, but assigned update success = false") {
    implicit ns =>
      val (deviceId, existingCorrelationId, offlineUpdateCorrelationId, deviceReport, _) =
        putManifestCurrentAssignmentWithOfflineCorrelationId(success = false)

      val targets = getTargetsOk(deviceId)
      targets.signed.targets shouldBe empty

      val allMessages = msgPub.findReceivedAll { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == deviceId
      }

      forExactly(1, allMessages) { report =>
        report.correlationId shouldBe existingCorrelationId
        report shouldBe a[DeviceUpdateCompleted]
        report.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
        report.asInstanceOf[DeviceUpdateCompleted].result.success shouldBe false
      }

      forExactly(1, allMessages) { report =>
        report.correlationId shouldBe offlineUpdateCorrelationId
        report shouldBe a[DeviceUpdateCompleted]
        report.asInstanceOf[DeviceUpdateCompleted].result.code shouldBe deviceReport.result.code
        report.asInstanceOf[DeviceUpdateCompleted].result.success shouldBe false
      }
  }

  testWithRepo(
    "installing assignments created via a scheduled update updates update status"
  ) { implicit ns =>
    val dev = registerAdminDeviceWithSecondariesOk()
    val currentUpdate = GenTargetUpdateRequest.generate
    val newUpdate = GenTargetUpdateRequest.generate
    val secondarySerial = dev.secondaries.keys.head
    val secondaryKey = dev.secondaryKeys(secondarySerial)

    val deviceManifest = buildSecondaryManifest(
      dev.primary.ecuSerial,
      dev.primaryKey,
      secondarySerial,
      secondaryKey,
      Map(dev.primary.ecuSerial -> currentUpdate.to, secondarySerial -> currentUpdate.to)
    )
    putManifestOk(dev.deviceId, deviceManifest)

    val mtu = TargetUpdateSpec(
      Map(dev.secondaries.values.head.hardwareId -> newUpdate, dev.primary.hardwareId -> newUpdate)
    )

    createUpdateOk(dev.deviceId, mtu)

    updateSchedulerIO.run().futureValue

    val newManifest = buildSecondaryManifest(
      dev.primary.ecuSerial,
      dev.primaryKey,
      secondarySerial,
      secondaryKey,
      Map(dev.primary.ecuSerial -> newUpdate.to, secondarySerial -> newUpdate.to)
    )

    putManifestOk(dev.deviceId, newManifest)

    val scheduledUpdate = listUpdatesOK(dev.deviceId).values.loneElement
    scheduledUpdate.status shouldBe Update.Status.Completed
  }

  testWithRepo(
    "a failed installation report originating from a scheduled update concludes the scheduled update"
  ) { implicit ns =>
    val dev = registerAdminDeviceWithSecondariesOk()
    val currentUpdate = GenTargetUpdateRequest.generate
    val newUpdate = GenTargetUpdateRequest.generate

    val deviceManifest = buildPrimaryManifest(dev.primary, dev.primaryKey, currentUpdate.to)
    putManifestOk(dev.deviceId, deviceManifest)

    val mtu = TargetUpdateSpec(Map(dev.primary.hardwareId -> newUpdate))

    createUpdateOk(dev.deviceId, mtu, Instant.now().some)

    val scheduledUpdate = updateSchedulerIO.run().futureValue.loneElement

    val newManifest = buildPrimaryManifest(
      dev.primary,
      dev.primaryKey,
      currentUpdate.to,
      InstallationReport(
        scheduledUpdate.id.toCorrelationId,
        InstallationResult(
          success = false,
          code = ResultCode("download_failed"),
          ResultDescription("download failed")
        ),
        Seq.empty,
        None
      ).some
    )

    putManifestOk(dev.deviceId, newManifest)

    val apiScheduledUpdate = listUpdatesOK(dev.deviceId).values.loneElement
    apiScheduledUpdate.status shouldBe Update.Status.Completed
  }

  testWithRepo("partially installing a scheduled update sets status to PartiallyCompleted") {
    implicit ns =>
      val dev = registerAdminDeviceWithSecondariesOk()
      val currentUpdate = GenTargetUpdateRequest.generate
      val newUpdatePrimary = GenTargetUpdateRequest.generate
      val newUpdateSecondary = GenTargetUpdateRequest.generate
      val secondarySerial = dev.secondaries.keys.head
      val secondaryKey = dev.secondaryKeys(secondarySerial)

      val deviceManifest = buildSecondaryManifest(
        dev.primary.ecuSerial,
        dev.primaryKey,
        secondarySerial,
        secondaryKey,
        Map(dev.primary.ecuSerial -> currentUpdate.to, secondarySerial -> currentUpdate.to)
      )
      putManifestOk(dev.deviceId, deviceManifest)

      val mtu = TargetUpdateSpec(
        Map(
          dev.secondaries.values.head.hardwareId -> newUpdatePrimary,
          dev.primary.hardwareId -> newUpdateSecondary
        )
      )

      createUpdateOk(dev.deviceId, mtu)

      updateSchedulerIO.run().futureValue

      val newManifest = buildSecondaryManifest(
        dev.primary.ecuSerial,
        dev.primaryKey,
        secondarySerial,
        secondaryKey,
        Map(dev.primary.ecuSerial -> currentUpdate.to, secondarySerial -> newUpdateSecondary.to)
      )

      putManifestOk(dev.deviceId, newManifest)

      val scheduledUpdate = listUpdatesOK(dev.deviceId).values.loneElement
      scheduledUpdate.status shouldBe Update.Status.PartiallyCompleted

      val newManifest2 = buildSecondaryManifest(
        dev.primary.ecuSerial,
        dev.primaryKey,
        secondarySerial,
        secondaryKey,
        Map(dev.primary.ecuSerial -> newUpdatePrimary.to, secondarySerial -> newUpdateSecondary.to)
      )

      putManifestOk(dev.deviceId, newManifest2)

      val scheduledUpdate2 = listUpdatesOK(dev.deviceId).values.loneElement
      scheduledUpdate2.status shouldBe Update.Status.Completed
  }

  testWithRepo(
    "scheduled update gets assigned to the device if targets.json is up to date before the scheduled time"
  ) { implicit ns =>
    val dev = registerAdminDeviceWithSecondariesOk()
    val currentUpdate = GenTargetUpdateRequest.generate
    val newUpdate = GenTargetUpdateRequest.generate

    val deviceManifest = buildPrimaryManifest(dev.primary, dev.primaryKey, currentUpdate.to)
    putManifestOk(dev.deviceId, deviceManifest)

    val previousTargets = getTargetsOk(dev.deviceId).signed
    previousTargets.targets shouldBe empty

    val mtu = TargetUpdateSpec(Map(dev.primary.hardwareId -> newUpdate))

    createUpdateOk(dev.deviceId, mtu)

    updateSchedulerIO.run().futureValue

    val targets = getTargetsOk(dev.deviceId).signed

    targets.targets.keys should contain(newUpdate.to.target)
  }

  testWithRepo("a bus message gets published when scheduled update gets assigned") { implicit ns =>
    val dev = registerAdminDeviceWithSecondariesOk()
    val currentUpdate = GenTargetUpdateRequest.generate
    val newUpdate = GenTargetUpdateRequest.generate

    val deviceManifest = buildPrimaryManifest(dev.primary, dev.primaryKey, currentUpdate.to)
    putManifestOk(dev.deviceId, deviceManifest)

    val previousTargets = getTargetsOk(dev.deviceId).signed
    previousTargets.targets shouldBe empty

    val mtu = TargetUpdateSpec(Map(dev.primary.hardwareId -> newUpdate))

    createUpdateOk(dev.deviceId, mtu)

    val updateId = listUpdatesOK(dev.deviceId).values.loneElement.updateId

    // TODO: runs full daemon instead of IO
    val daemon = new UpdateSchedulerDaemon()
    daemon.runOnce().futureValue

    val targets = getTargetsOk(dev.deviceId).signed
    targets.targets.keys should contain(newUpdate.to.target)

    val assignedMsg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == dev.deviceId && msg.isInstanceOf[DeviceUpdateAssigned]
      }
      .map(_.asInstanceOf[DeviceUpdateAssigned])
      .value

    assignedMsg.deviceUuid shouldBe dev.deviceId
    assignedMsg.correlationId shouldBe updateId.toCorrelationId
  }


  testWithRepo(
    "reporting an update that was present in a cancelled scheduled update keeps scheduled update untouched"
  ) { implicit ns =>
    val dev = registerAdminDeviceWithSecondariesOk()
    val currentUpdate = GenTargetUpdateRequest.generate
    val newUpdate = GenTargetUpdateRequest.generate
    val secondarySerial = dev.secondaries.keys.head
    val secondaryKey = dev.secondaryKeys(secondarySerial)

    val deviceManifest = buildSecondaryManifest(
      dev.primary.ecuSerial,
      dev.primaryKey,
      secondarySerial,
      secondaryKey,
      Map(dev.primary.ecuSerial -> currentUpdate.to, secondarySerial -> currentUpdate.to)
    )
    putManifestOk(dev.deviceId, deviceManifest)

    val mtu = TargetUpdateSpec(
      Map(dev.secondaries.values.head.hardwareId -> newUpdate, dev.primary.hardwareId -> newUpdate)
    )

    val id = createUpdateOk(dev.deviceId, mtu)

    cancelUpdateOK(dev.deviceId, id)

    val scheduledUpdate0 = listUpdatesOK(dev.deviceId).values.loneElement
    scheduledUpdate0.status shouldBe Update.Status.Cancelled

    val newManifest = buildSecondaryManifest(
      dev.primary.ecuSerial,
      dev.primaryKey,
      secondarySerial,
      secondaryKey,
      Map(dev.primary.ecuSerial -> newUpdate.to, secondarySerial -> newUpdate.to)
    )

    putManifestOk(dev.deviceId, newManifest)

    val scheduledUpdate = listUpdatesOK(dev.deviceId).values.loneElement
    scheduledUpdate.status shouldBe Update.Status.Cancelled
  }
}
