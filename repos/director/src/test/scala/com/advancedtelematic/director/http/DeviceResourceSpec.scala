package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.director.data.AdminDataType
import com.advancedtelematic.director.data.AdminDataType.{EcuInfoResponse, QueueResponse, RegisterDevice}
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DataType._
import com.advancedtelematic.director.data.DeviceRequest.{DeviceManifest, EcuManifest, EcuManifestCustom, OperationResult}
import com.advancedtelematic.director.data.GeneratorOps._
import com.advancedtelematic.director.data.Generators._
import com.advancedtelematic.director.data.Messages.DeviceManifestReported
import com.advancedtelematic.director.data.UptaneDataType.{FileInfo, Hashes, Image}
import com.advancedtelematic.director.db.{AssignmentsRepositorySupport, EcuRepositorySupport}
import com.advancedtelematic.director.util._
import com.advancedtelematic.libats.data.DataType.{ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, InstallationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId._
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceSeen, DeviceUpdateCompleted, _}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, SnapshotRole, TargetsRole, TimestampRole, TufRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import org.scalatest.Inspectors
import org.scalatest.LoneElement._
import org.scalatest.OptionValues._
import io.circe.syntax._

class DeviceResourceSpec extends DirectorSpec
  with RouteResourceSpec with AdminResources with AssignmentResources with EcuRepositorySupport
  with DeviceManifestSpec with RepositorySpec with Inspectors with DeviceResources with AssignmentsRepositorySupport {

  override implicit val msgPub = new MockMessageBus

  def forceRoleExpire[T](deviceId: DeviceId)(implicit tufRole: TufRole[T]): Unit = {
    import slick.jdbc.MySQLProfile.api._
    val sql = sql"update signed_roles set expires_at = '1970-01-01 00:00:00' where device_id = '#${deviceId.show}' and role = '#${tufRole.typeStr}'"
    db.run(sql.asUpdate).futureValue
  }

  testWithNamespace("accepts a device registering ecus") { implicit ns =>
    createRepoOk()
    registerDeviceOk()
  }

  testWithRepo("a device can replace its ecus") { implicit ns =>
    val deviceId = DeviceId.generate()
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

    val ecuReplaced = msgPub.findReceived[EcuReplacement](deviceId.show).value.asInstanceOf[EcuReplaced]
    ecuReplaced.former shouldBe EcuAndHardwareId(primaryEcu, ecus.hardware_identifier.value)
    ecuReplaced.current shouldBe EcuAndHardwareId(primaryEcu2, ecus2.hardware_identifier.value)
  }

  testWithRepo("a device can replace its primary ecu only") { implicit ns =>
    val deviceId = DeviceId.generate()
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

    val ecuReplaced = msgPub.findReceived[EcuReplacement](deviceId.show).value.asInstanceOf[EcuReplaced]
    ecuReplaced.former shouldBe EcuAndHardwareId(primaryEcu, ecus.hardware_identifier.value)
    ecuReplaced.current shouldBe EcuAndHardwareId(primaryEcu2, ecus2.hardware_identifier.value)
  }

  testWithRepo("a device can replace its primary ecu and one secondary ecu") { implicit ns =>
    val deviceId = DeviceId.generate()
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

    val secondaryReplacement :: primaryReplacement :: _ = msgPub.findReceivedAll[EcuReplacement](deviceId.show).map(_.asInstanceOf[EcuReplaced])
    primaryReplacement.former shouldBe EcuAndHardwareId(primary.ecu_serial, primary.hardware_identifier.value)
    primaryReplacement.current shouldBe EcuAndHardwareId(primary2.ecu_serial, primary2.hardware_identifier.value)
    secondaryReplacement.former shouldBe EcuAndHardwareId(secondary.ecu_serial, secondary.hardware_identifier.value)
    secondaryReplacement.current shouldBe EcuAndHardwareId(secondary2.ecu_serial, secondary2.hardware_identifier.value)
  }

  testWithRepo("a device can replace multiple secondary ecus") { implicit ns =>
    val deviceId = DeviceId.generate()
    val primary = GenRegisterEcu.generate
    val (secondary, otherSecondary) = (GenRegisterEcu.generate, GenRegisterEcu.generate)
    val (secondary2, otherSecondary2) = (GenRegisterEcu.generate, GenRegisterEcu.generate)
    val req = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary, otherSecondary))
    val req2 = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary2, otherSecondary2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val secondaryReplacement :: otherSecondaryReplacement :: _ = msgPub.findReceivedAll[EcuReplacement](deviceId.show).map(_.asInstanceOf[EcuReplaced])
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
    val (primary, secondary, secondary2) = (GenRegisterEcu.generate, GenRegisterEcu.generate, GenRegisterEcu.generate)
    val req = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary))
    val req2 = RegisterDevice(deviceId.some, primary.ecu_serial, Seq(primary, secondary, secondary2))

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
    val (primary, secondary, secondary2) = (GenRegisterEcu.generate, GenRegisterEcu.generate, GenRegisterEcu.generate)
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
    lazy val ecuReplacementDisabledRoutes = new DirectorRoutes(keyserverClient, allowEcuReplacement = false).routes

    val deviceId = DeviceId.generate()
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val req = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))
    val ecus2 = GenRegisterEcu.generate
    val req2 = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req).namespaced ~> ecuReplacementDisabledRoutes ~> check {
      status shouldBe StatusCodes.Created
    }

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> ecuReplacementDisabledRoutes ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.EcuReplacementDisabled
    }

    msgPub.findReceived[EcuReplacement](deviceId.show) shouldBe None
  }

  testWithRepo("registering the same device id with different ecus works when using the same primary ecu") { implicit ns =>
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

  testWithRepo("a device can replace a secondary and POST manifests for the new ECUs") { implicit ns =>
    val dev = registerAdminDeviceWithSecondariesOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val secondarySerial = dev.secondaries.keys.head
    val secondaryKey = dev.secondaryKeys(secondarySerial)

    val deviceManifest = buildSecondaryManifest(dev.primary.ecuSerial, dev.primaryKey, secondarySerial, secondaryKey, Map(dev.primary.ecuSerial -> targetUpdate.to, secondarySerial -> targetUpdate.to))
    putManifestOk(dev.deviceId, deviceManifest)

    val (ecus2, ecu2Keys) = GenRegisterEcuKeys.generate
    val regPrimaryEcu = AdminDataType.RegisterEcu(dev.primary.ecuSerial, dev.primary.hardwareId, dev.primaryKey.pubkey)
    val req2 = RegisterDevice(dev.deviceId.some, dev.primary.ecuSerial, Seq(regPrimaryEcu, ecus2))

    Post(apiUri(s"device/${dev.deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    putManifestOk(dev.deviceId, buildSecondaryManifest(dev.primary.ecuSerial, dev.primaryKey, ecus2.ecu_serial, ecu2Keys, Map(dev.primary.ecuSerial -> targetUpdate.to, ecus2.ecu_serial -> targetUpdate.to)))

    Get(apiUri(s"admin/devices/${dev.deviceId.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val info = responseAs[Vector[EcuInfoResponse]]
      info should have size(2)
      info.map(_.id) should contain(dev.primary.ecuSerial)
      info.map(_.id) should contain(ecus2.ecu_serial)
    }
  }

  testWithRepo("replacing ecus fails when device has running assignments") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = DeviceId.generate()
    val ecus = GenRegisterEcu.generate
    val primaryEcu = ecus.ecu_serial
    val regDev = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus))

    Post(apiUri(s"device/${deviceId.show}/ecus"), regDev).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }

    createDeviceAssignmentOk(deviceId, ecus.hardware_identifier, targetUpdate.some)

    val ecus2 = GenRegisterEcu.generate
    val req2 = RegisterDevice(deviceId.some, primaryEcu, Seq(ecus, ecus2))

    Post(apiUri(s"device/${deviceId.show}/ecus"), req2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.PreconditionFailed
      val resp = responseAs[ErrorRepresentation]
      resp.description should include(s"Cannot replace ecus for $deviceId")
      resp.code shouldBe ErrorCodes.ReplaceEcuAssignmentExists
    }

    val replacements = msgPub.findReceivedAll[EcuReplacement](deviceId.show)
    replacements.loneElement.asInstanceOf[EcuReplacementFailed].deviceUuid shouldBe deviceId
  }

  testWithRepo("Previously used *secondary* ecus cannot be reused when replacing ecus") { implicit ns =>
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
      targets.targets(targetUpdate.to.target).hashes.values should contain(targetUpdate.to.checksum.hash)
      targets.targets(targetUpdate.to.target).length shouldBe targetUpdate.to.targetLength
    }
  }

  testWithRepo("device can PUT a valid manifest") { implicit ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey,targetUpdate.to)

    putManifestOk(regDev.deviceId, deviceManifest)
  }

  testWithRepo("device queue is cleared after successful PUT manifest") { implicit ns =>
    val registerDevice = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = registerDevice.deviceId
    createDeviceAssignmentOk(registerDevice.deviceId, registerDevice.primary.hardwareId, targetUpdate.some)

    val deviceManifest = buildPrimaryManifest(registerDevice.primary, registerDevice.primaryKey, targetUpdate.to)

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
    val deviceManifest = buildSecondaryManifest(regDev.primary.ecuSerial, regDev.primaryKey, secondary, secondaryKey, Map(regDev.primary.ecuSerial -> targetUpdate.to, secondary -> targetUpdate.to))

    putManifestOk(regDev.deviceId, deviceManifest)
  }

  testWithRepo("fails when manifest is not properly signed by secondary") { implicit ns =>
    val regDev = registerAdminDeviceWithSecondariesOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val (secondary, realKey) = regDev.secondaryKeys.head
    val secondaryKey = GenTufKeyPair.generate
    val deviceManifest = buildSecondaryManifest(regDev.primary.ecuSerial, regDev.primaryKey, secondary, secondaryKey, Map(regDev.primary.ecuSerial -> targetUpdate.to, secondary -> targetUpdate.to))

    Put(apiUri(s"device/${regDev.deviceId.show}/manifest"), deviceManifest).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.Manifest.SignatureNotValid
      responseAs[ErrorRepresentation].description should include(s"not signed with key ${realKey.pubkey.id}")
    }
  }

  testWithRepo("returns exact same targets.json if assignments did not change") { implicit ns =>
    import com.advancedtelematic.libtuf.crypt.CanonicalJson._
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some)

    val firstTargets = Get(apiUri(s"device/${regDev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]]
    }

    Thread.sleep(1000)

    val secondTargets = Get(apiUri(s"device/${regDev.deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]]
    }

    if(firstTargets.json.canonical != secondTargets.json.canonical)
      fail(s"targets.json $firstTargets is not the same as $secondTargets")
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

  testWithRepo("a refreshed targets returns the same assignments as before, even if they were completed") { implicit ns =>
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

  testWithRepo("moves queue status to inflight = true after device gets targets containing assignment") { implicit ns =>
    val registerDevice = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val deviceId = registerDevice.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(registerDevice.deviceId, registerDevice.primary.hardwareId, targetUpdate.some, correlationId.some)

    getDeviceRoleOk[TargetsRole](deviceId)

    getDeviceAssignment(deviceId) {
      status shouldBe StatusCodes.OK
      val firstQueueItem = responseAs[List[QueueResponse]].head

      firstQueueItem.targets(registerDevice.primary.ecuSerial).image.filepath shouldBe targetUpdate.to.target
      firstQueueItem.inFlight shouldBe true
      firstQueueItem.correlationId shouldBe correlationId
    }
  }

  testWithRepo("correlationId is included in a targets role custom field") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed

      targets.custom.flatMap(_.as[DeviceTargetsCustom].toOption.flatMap(_.correlationId)) should contain(correlationId)
    }
  }

  testWithRepo("ecu custom includes custom metadata") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.retryUntil(_.to.uri.isDefined).generate
    val regDev = registerAdminDeviceOk()
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed
      val item = targets.targets.head._2
      val custom = item.custom.flatMap(_.as[TargetItemCustom].toOption)

      custom.map(_.ecuIdentifiers) should contain(Map(regDev.primary.ecuSerial -> TargetItemCustomEcuData(regDev.primary.hardwareId)))
      custom.flatMap(_.uri) shouldBe targetUpdate.to.uri
    }
  }

  testWithRepo("custom metadata includes targets per ecu when more than one ECU is assigned to the same target") { implicit ns =>
    val targetUpdate = GenTargetUpdateRequest.generate
    val regDev = registerAdminDeviceWithSecondariesOk()
    val (secondaryEcuSerial, secondaryEcu) = (regDev.ecus - regDev.primary.ecuSerial).head
    val deviceId = regDev.deviceId
    val correlationId = GenCorrelationId.generate
    createDeviceAssignmentOk(deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)
    createDeviceAssignmentOk(deviceId, secondaryEcu.hardwareId, targetUpdate.some, correlationId.some)

    Get(apiUri(s"device/${deviceId.show}/targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val targets = responseAs[SignedPayload[TargetsRole]].signed
      val item = targets.targets.head._2
      val custom = item.custom.flatMap(_.as[TargetItemCustom].toOption).map(_.ecuIdentifiers)

      custom.flatMap(_.get(regDev.primary.ecuSerial)) should contain (TargetItemCustomEcuData(regDev.primary.hardwareId))
      custom.flatMap(_.get(secondaryEcuSerial)) should contain (TargetItemCustomEcuData(secondaryEcu.hardwareId))
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

  testWithRepo("publishes DeviceUpdateCompleted message") { implicit  ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate
    val deviceReport = GenInstallReport(regDev.primary.ecuSerial, success = true, correlationId = correlationId.some).generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey,targetUpdate.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifest)

    val reportMsg = msgPub.findReceived[DeviceUpdateEvent] { msg: DeviceUpdateEvent =>
      msg.deviceUuid === regDev.deviceId
    }.map(_.asInstanceOf[DeviceUpdateCompleted])

    reportMsg.map(_.namespace) should contain(ns)

    reportMsg.get.result shouldBe deviceReport.result
    val (ecuReportId, ecuReport) = reportMsg.get.ecuReports.head
    ecuReportId shouldBe regDev.primary.ecuSerial
    ecuReport.result shouldBe deviceReport.items.head.result
    reportMsg.get.correlationId shouldBe correlationId
  }

  testWithRepo("publishes DeviceUpdateCompleted message if device sends no install report, but ecu sends an ecu report") { implicit  ns =>
    val regDev = registerAdminDeviceOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    val image = Image(targetUpdate.to.target, FileInfo(Hashes(targetUpdate.to.checksum), targetUpdate.to.targetLength))
    val ecuInstallResult = OperationResult("0", 0, "some description")
    val ecuManifest = sign(regDev.primaryKey, EcuManifest(image, regDev.primary.ecuSerial, "", custom = EcuManifestCustom(ecuInstallResult).asJson.some))
    val deviceManifest = sign(regDev.primaryKey, DeviceManifest(regDev.primary.ecuSerial, Map(regDev.primary.ecuSerial -> ecuManifest), installation_report = None))

    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)

    putManifestOk(regDev.deviceId, deviceManifest)

    val reportMsg = msgPub.findReceived[DeviceUpdateEvent] { msg: DeviceUpdateEvent =>
      msg.deviceUuid === regDev.deviceId
    }.map(_.asInstanceOf[DeviceUpdateCompleted]).value

    reportMsg.namespace shouldBe ns
    reportMsg.result shouldBe InstallationResult(success = true, ResultCode("0"), ResultDescription("All targeted ECUs were successfully updated"))

    val (ecuReportId, ecuReport) = reportMsg.ecuReports.head
    ecuReportId shouldBe regDev.primary.ecuSerial
    ecuReport.result shouldBe InstallationResult(success = true, ResultCode("0"), ResultDescription("some description"))
  }

  testWithRepo("fails with EcuNotPrimary if device declares wrong primary") { implicit ns =>
    val regDev = registerAdminDeviceWithSecondariesOk()
    val targetUpdate = GenTargetUpdateRequest.generate
    val secondarySerial = regDev.secondaries.keys.head
    val secondaryKey = regDev.secondaryKeys(secondarySerial)
    val deviceManifest = buildSecondaryManifest(secondarySerial, regDev.primaryKey, secondarySerial, secondaryKey, Map(secondarySerial -> targetUpdate.to))

    putManifest(regDev.deviceId, deviceManifest) {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.Manifest.EcuNotPrimary
    }
  }

  testWithRepo("device updates to same target we know it has installed but without install report") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId).signed
    targetsAfter.targets shouldNot be(empty)
    targetsAfter.targets.get(targetUpdate.to.target) shouldBe defined
    targetsAfter.targets.get(targetUpdate.to.target).map(_.length) should contain(targetUpdate.to.targetLength)
  }

  testWithRepo("device updates to same target we know it has installed with install report") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)
    getDeviceRoleOk[TargetsRole](regDev.deviceId).signed.targets shouldNot be(empty)

    val deviceReport = GenInstallReport(regDev.primary.ecuSerial, success = false, correlationId = correlationId.some).generate
    val deviceManifestAfterTrying = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId).signed
    targetsAfter.targets shouldBe empty

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue
    processed.head.result shouldNot be(empty)
    processed.head.canceled  shouldBe false
    processed.head.successful  shouldBe false
  }

  // keep old director behavior
  // Status should not change, since there is no report, nothing should be published
  testWithRepo("device updates to some unknown target without an installation report should be NOOP") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)

    val unknownVersion = GenTargetUpdateRequest.generate

    val deviceManifestAfterTrying = buildPrimaryManifest(regDev.primary, regDev.primaryKey, unknownVersion.to)
    val targetsBefore = getDeviceRoleOk[TargetsRole](regDev.deviceId)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter shouldBe targetsBefore

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue
    processed shouldBe empty
  }

  // Keep old director behavior, clean targets
  testWithRepo("device updates to some unknown target with with a failed installation report empties targets") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)
    val targetsBefore = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val deviceReport = GenInstallReport(regDev.primary.ecuSerial, success = false, correlationId = correlationId.some).generate
    val unknownUpdate = GenTargetUpdateRequest.generate
    val deviceManifestAfterTrying = buildPrimaryManifest(regDev.primary, regDev.primaryKey, unknownUpdate.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter.signed.targets shouldBe empty

    val processed = assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue
    processed.head.result shouldNot be(empty)
  }

  // Keep old behavior, leaving targets/assignments unchanged and publish message if this happens
  testWithRepo("device updates to some unknown target with a success installation report leaves assignments unchanged") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val targetUpdate = GenTargetUpdateRequest.generate
    val correlationId = GenCorrelationId.generate

    createDeviceAssignmentOk(regDev.deviceId, regDev.primary.hardwareId, targetUpdate.some, correlationId.some)
    val targetsBefore = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsBefore.signed.targets shouldNot be(empty)

    val deviceReport = GenInstallReport(regDev.primary.ecuSerial, success = true, correlationId = correlationId.some).generate
    val unknownUpdate = GenTargetUpdateRequest.generate
    val deviceManifestAfterTrying = buildPrimaryManifest(regDev.primary, regDev.primaryKey, unknownUpdate.to, deviceReport.some)

    putManifestOk(regDev.deviceId, deviceManifestAfterTrying)

    val targetsAfter = getDeviceRoleOk[TargetsRole](regDev.deviceId)
    targetsAfter shouldBe targetsBefore

    assignmentsRepository.findProcessed(ns, regDev.deviceId).futureValue shouldBe empty

    val reportMsg = msgPub.findReceived[DeviceUpdateEvent] { msg: DeviceUpdateEvent =>
      msg.deviceUuid === regDev.deviceId
    }.map(_.asInstanceOf[DeviceUpdateCompleted])

    reportMsg.value.result shouldBe deviceReport.result
  }

  testWithRepo("publishes bus message when manifest is received") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val initialVersion = GenTargetUpdateRequest.generate
    val deviceManifest = buildPrimaryManifest(regDev.primary, regDev.primaryKey, initialVersion.to, None)

    putManifestOk(regDev.deviceId, deviceManifest)

    val msg = msgPub.findReceived[DeviceManifestReported](regDev.deviceId.show)

    msg.value.manifest.asJsonSignedPayload shouldBe deviceManifest.asJsonSignedPayload
  }
}
