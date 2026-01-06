package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.director.data.AdminDataType.{TargetUpdateRequest, TargetUpdateSpec}
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.{TargetItemCustom, Update, UpdateId}
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.data.Generators.*
import com.advancedtelematic.director.db.{
  DbDeviceRoleRepositorySupport,
  HardwareUpdateRepositorySupport,
  RepoNamespaceRepositorySupport
}
import com.advancedtelematic.director.deviceregistry.daemon.{
  DeviceEventListener,
  DeviceUpdateEventListener
}
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.genDeviceT
import com.advancedtelematic.director.http.deviceregistry.RegistryDeviceRequests
import com.advancedtelematic.director.util.{
  DeviceManifestSpec,
  DirectorSpec,
  RepositorySpec,
  ResourceSpec
}
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, Event, EventType}
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeviceEventMessage,
  DeviceUpdateAssigned,
  DeviceUpdateCanceled,
  DeviceUpdateCompleted,
  DeviceUpdateEvent
}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.Json
import io.circe.syntax.*
import org.scalatest.EitherValues.*
import org.scalatest.LoneElement.*
import org.scalatest.OptionValues.*
import org.scalatest.concurrent.Eventually

import java.time.Instant
import java.time.temporal.ChronoUnit

class UpdateResourceSpec
    extends DirectorSpec
    with ResourceSpec
    with RepoNamespaceRepositorySupport
    with DbDeviceRoleRepositorySupport
    with HardwareUpdateRepositorySupport
    with AdminResources
    with ProvisionedDevicesRequests
    with DeviceManifestSpec
    with AssignmentResources
    with RepositorySpec
    with RegistryDeviceRequests
    with UpdatesResources
    with Eventually {

  override implicit val msgPub: MockMessageBus = new MockMessageBus()

  testWithRepo("fetching updates for non-existent device returns empty") { implicit ns =>
    val deviceId = DeviceId.generate()

    val updates = listUpdatesOK(deviceId)
    updates.values shouldBe empty
  }

  testWithRepo("can create an update for a device") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)
    val createRequest = CreateUpdateRequest(
      targets = Map(hardwareId -> targetRequest),
      devices = Seq(device.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      result.affected should contain(device.deviceId)
      result.notAffected shouldBe empty
    }

    val updates = listUpdatesOK(device.deviceId)
    val update = updates.values.loneElement

    update.packages should contain(device.primary.hardwareId -> targetUpdate.target)
    update.status shouldBe Update.Status.Assigned
  }

  testWithRepo("device can see the update in targets.json") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)
    val createRequest = CreateUpdateRequest(
      targets = Map(hardwareId -> targetRequest),
      devices = Seq(device.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
    }

    val targets = getTargetsOk(device.deviceId)
    targets.signed.targets should not be empty

    val targetItemCustom = targets.signed.targets.headOption.value._2.customParsed[TargetItemCustom]
    targetItemCustom.get.ecuIdentifiers.keys.head shouldBe device.ecus.keys.head
  }

  testWithRepo("update moves to Seen when devices sees the update in targets.json") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)
    val createRequest = CreateUpdateRequest(
      targets = Map(hardwareId -> targetRequest),
      devices = Seq(device.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
    }

    val targets = getTargetsOk(device.deviceId)
    targets.signed.targets should not be empty

    val updates = listUpdatesOK(device.deviceId)
    val update = updates.values.loneElement

    update.status shouldBe Update.Status.Seen
  }


  testWithRepo("update is marked as completed when device reports successful installation") {
    implicit ns =>
      val device = registerAdminDeviceOk()
      val hardwareId = device.primary.hardwareId
      val targetUpdate = GenTargetUpdateRequest.generate
      val createRequest = CreateUpdateRequest(
        targets = Map(hardwareId -> targetUpdate),
        devices = Seq(device.deviceId)
      )

      createManyUpdates(createRequest) {
        status shouldBe StatusCodes.OK
        val result = responseAs[CreateUpdateResult]
        result.affected should contain(device.deviceId)
      }

      val updates = listUpdatesOK(device.deviceId)
      val updateId = updates.values.loneElement.updateId

      getTargetsOk(device.deviceId)

      val manifest = buildPrimaryManifest(device.primary, device.primaryKey, targetUpdate.to)
      putManifestOk(device.deviceId, manifest)

      val completedUpdates = listUpdatesOK(device.deviceId)
      completedUpdates.values should have size 1
      val completedUpdate = completedUpdates.values.head
      completedUpdate.status shouldBe Update.Status.Completed

      val msg = msgPub
        .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
          msg.deviceUuid == device.deviceId && msg.isInstanceOf[DeviceUpdateCompleted]
        }
        .map(_.asInstanceOf[DeviceUpdateCompleted])
        .value
      msg.correlationId shouldBe updateId.toCorrelationId
  }

  testWithRepo("can get update details by ID") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdateRequest.generate
    val createRequest =
      CreateUpdateRequest(targets = Map(hardwareId -> targetUpdate), devices = Seq(device.deviceId))

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      result.affected should contain(device.deviceId)
    }

    val updates = listUpdatesOK(device.deviceId)
    val updateId = updates.values.loneElement.updateId

    val updateDetail = getUpdateDetailOK(device.deviceId, updateId)
    updateDetail.updateId shouldBe updateId
    updateDetail.status shouldBe Update.Status.Assigned
    updateDetail.packages should contain(device.primary.hardwareId -> targetUpdate.to.target)
    updateDetail.ecuResults shouldBe empty
  }

  testWithRepo("update details include results when update is completed") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdateRequest.generate
    val createRequest =
      CreateUpdateRequest(targets = Map(hardwareId -> targetUpdate), devices = Seq(device.deviceId))

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
    }

    val updates = listUpdatesOK(device.deviceId)
    val updateId = updates.values.loneElement.updateId

    getTargetsOk(device.deviceId)

    val installationReport = GenInstallReport(
      device.primary.ecuSerial,
      success = true,
      correlationId = updateId.toCorrelationId.some
    ).generate

    val manifest = buildPrimaryManifest(
      device.primary,
      device.primaryKey,
      targetUpdate.to,
      installationReport.some
    )
    putManifestOk(device.deviceId, manifest)

    // Process all messages
    msgPub.findReceivedAll((_: DeviceUpdateEvent) => true).foreach { msg =>
      new DeviceUpdateEventListener(msgPub).apply(msg).futureValue
    }

    val updateDetail = getUpdateDetailOK(device.deviceId, updateId)
    updateDetail.updateId shouldBe updateId
    updateDetail.status shouldBe Update.Status.Completed
    updateDetail.completedAt shouldNot be(empty)

    val resultForPrimaryEcu = updateDetail.ecuResults.get(device.primary.ecuSerial).value
    resultForPrimaryEcu.success shouldBe true
    resultForPrimaryEcu.hardwareId shouldBe hardwareId
    resultForPrimaryEcu.id shouldBe targetUpdate.to.target

    val ecuReport = resultForPrimaryEcu.result.value
    ecuReport.success shouldBe true
    ecuReport.description shouldBe defined
    ecuReport.description.value shouldBe installationReport.result.description.value
    ecuReport.resultCode shouldBe installationReport.result.code
  }

  testWithRepo("update details include results for partially completed updates") { implicit ns =>
    val device = registerAdminDeviceWithSecondariesOk()
    val primaryHwId = device.primary.hardwareId
    val secondaryHwId = device.secondaries.values.head.hardwareId
    val primaryTarget = GenTargetUpdateRequest.generate
    val secondaryTarget = GenTargetUpdateRequest.generate

    val createRequest = CreateUpdateRequest(
      targets = Map(primaryHwId -> primaryTarget, secondaryHwId -> secondaryTarget),
      devices = Seq(device.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
    }

    val updates = listUpdatesOK(device.deviceId)
    val updateId = updates.values.loneElement.updateId

    getTargetsOk(device.deviceId)

    val installationReport = GenInstallReport(
      device.primary.ecuSerial,
      success = true,
      correlationId = updateId.toCorrelationId.some
    ).generate

    val manifest = buildPrimaryManifest(
      device.primary,
      device.primaryKey,
      primaryTarget.to,
      installationReport.some
    )
    putManifestOk(device.deviceId, manifest)

    // Process all messages
    msgPub.findReceivedAll((_: DeviceUpdateEvent) => true).foreach { msg =>
      new DeviceUpdateEventListener(msgPub).apply(msg).futureValue
    }

    val updateDetail = getUpdateDetailOK(device.deviceId, updateId)
    updateDetail.updateId shouldBe updateId
    updateDetail.status shouldBe Update.Status.PartiallyCompleted

    val resultForPrimaryEcu = updateDetail.ecuResults.get(device.primary.ecuSerial).value
    resultForPrimaryEcu.success shouldBe true
    resultForPrimaryEcu.hardwareId shouldBe primaryHwId
    resultForPrimaryEcu.id shouldBe primaryTarget.to.target

    val ecuReport = resultForPrimaryEcu.result.value
    ecuReport.success shouldBe true
    ecuReport.resultCode shouldBe installationReport.result.code
    ecuReport.description.value shouldBe installationReport.result.description.value

    // Secondary ECU should not have a result yet
    (updateDetail.ecuResults shouldNot contain).key(device.secondaries.keys.head)
  }

  testWithRepo("returns 404 when update ID doesn't exist") { implicit ns =>
    val device = registerAdminDeviceOk()
    val nonExistentUpdateId = UpdateId.generate()

    Get(
      apiUri(s"updates/devices/${device.deviceId.show}/${nonExistentUpdateId.show}")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  testWithRepo("update is marked as partially completed when only some ECUs report success") {
    implicit ns =>
      val device = registerAdminDeviceWithSecondariesOk()
      val primaryHwId = device.primary.hardwareId
      val secondaryHwId = device.secondaries.values.head.hardwareId
      val primaryTarget = GenTargetUpdateRequest.generate
      val secondaryTarget = GenTargetUpdateRequest.generate

      val createRequest = CreateUpdateRequest(
        targets = Map(primaryHwId -> primaryTarget, secondaryHwId -> secondaryTarget),
        devices = Seq(device.deviceId)
      )

      createManyUpdates(createRequest) {
        status shouldBe StatusCodes.OK
        val result = responseAs[CreateUpdateResult]
        result.affected should contain(device.deviceId)
      }

      getTargetsOk(device.deviceId)

      val manifest = buildPrimaryManifest(device.primary, device.primaryKey, primaryTarget.to)
      putManifestOk(device.deviceId, manifest)

      val partialUpdates = listUpdatesOK(device.deviceId)
      partialUpdates.values should have size 1
      val partialUpdate = partialUpdates.values.head
      partialUpdate.status shouldBe Update.Status.PartiallyCompleted
  }

  testWithRepo("can create an update for multiple devices") { implicit ns =>
    val device1 = registerAdminDeviceOk()
    val device2 = registerAdminDeviceOk(hardwareIdentifier = device1.primary.hardwareId.some)
    val hardwareId = device1.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)
    val createRequest = CreateUpdateRequest(
      targets = Map(hardwareId -> targetRequest),
      devices = Seq(device1.deviceId, device2.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      (result.affected should contain).allOf(device1.deviceId, device2.deviceId)
      result.notAffected shouldBe empty
    }

    val targets1 = getTargetsOk(device1.deviceId)
    targets1.signed.targets should not be empty

    val targets2 = getTargetsOk(device2.deviceId)
    targets2.signed.targets should not be empty
  }

  testWithRepo("returns notAffected for devices with incompatible hardware") { implicit ns =>
    val device1 = registerAdminDeviceOk()
    val device2 = registerAdminDeviceOk(Some(GenHardwareIdentifier.generate))
    val hardwareId = device1.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)
    val createRequest = CreateUpdateRequest(
      targets = Map(hardwareId -> targetRequest),
      devices = Seq(device1.deviceId, device2.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      result.affected should contain only device1.deviceId
      (result.notAffected should contain).key(device2.deviceId)
    }

    // Only device1 should see the update
    val targets1 = getTargetsOk(device1.deviceId)
    targets1.signed.targets should not be empty

    val targets2 = getTargetsOk(device2.deviceId)
    targets2.signed.targets shouldBe empty
  }

  testWithRepo(
    "when creating an update for a device, if the device is not affected, no TargetUpdateSpec is created"
  ) { implicit ns =>
    val device = registerAdminDeviceOk()

    val differentHardwareId = GenHardwareIdentifier.generate
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)

    val req = CreateDeviceUpdateRequest(
      targets = Map(differentHardwareId -> targetRequest),
      scheduledFor = None
    )

    Post(apiUri(s"updates/devices/${device.deviceId.show}"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      val error = responseAs[ErrorRepresentation]
      error.code shouldBe ErrorCodes.DeviceCannotBeUpdated

      val deviceError = error.cause.value.as[Map[String, ErrorRepresentation]].value.values.head
      deviceError.code shouldBe ErrorCodes.DeviceNoCompatibleHardware
    }

    val updates = listUpdatesOK(device.deviceId)
    updates.values shouldBe empty

    val targetSpecIds = hardwareUpdateRepository.findAll(ns).futureValue
    targetSpecIds shouldBe empty
  }

  testWithRepo("does not accept empty targets") { implicit ns =>
    val device = registerAdminDeviceOk()
    val createRequest = CreateUpdateRequest(targets = Map.empty, devices = Seq(device.deviceId))

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidMtu
    }
  }

  testWithRepo("accepts user defined json") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val userDefinedCustom = Json.obj("some" -> Json.fromString("val"))
    val toUpdate = GenTargetUpdate.generate.copy(userDefinedCustom = Some(userDefinedCustom))
    val toUpdateReq = TargetUpdateRequest(None, toUpdate)
    val createRequest =
      CreateUpdateRequest(targets = Map(hardwareId -> toUpdateReq), devices = Seq(device.deviceId))

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      result.affected should contain(device.deviceId)
    }

    // Device fetches targets.json
    val targets = getTargetsOk(device.deviceId)
    targets.signed.targets should not be empty

    // Verify the target contains the user-defined custom JSON
    val targetItemCustom = targets.signed.targets.headOption.value._2.customParsed[TargetItemCustom]
    targetItemCustom.get.userDefinedCustom.value shouldBe userDefinedCustom
  }

  testWithRepo("can cancel an update for a device") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)
    val createRequest = CreateUpdateRequest(
      targets = Map(hardwareId -> targetRequest),
      devices = Seq(device.deviceId)
    )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      result.affected should contain(device.deviceId)
    }

    val updates = listUpdatesOK(device.deviceId)
    val update = updates.values.loneElement
    update.status shouldBe Update.Status.Assigned
    val updateId = update.updateId

    cancelUpdateOK(device.deviceId, updateId)

    val cancelledUpdates = listUpdatesOK(device.deviceId)
    cancelledUpdates.values should have size 1
    cancelledUpdates.values.head.status shouldBe Update.Status.Cancelled

    val msg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == device.deviceId && msg.correlationId == updateId.toCorrelationId &&
        msg.isInstanceOf[DeviceUpdateCanceled]
      }

    msg shouldNot be(empty)
  }

  testWithRepo("can cancel a scheduled update") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)

    createUpdateOk(
      device.deviceId,
      TargetUpdateSpec(Map(hardwareId -> targetRequest)),
      Instant.now.minusSeconds(60).some
    )

    val updates = listUpdatesOK(device.deviceId)
    val update = updates.values.loneElement
    update.status shouldBe Update.Status.Scheduled
    val updateId = update.updateId

    cancelUpdateOK(device.deviceId, updateId)

    val cancelledUpdates = listUpdatesOK(device.deviceId)
    cancelledUpdates.values should have size 1
    cancelledUpdates.values.head.status shouldBe Update.Status.Cancelled

    val msg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == device.deviceId && msg.correlationId == updateId.toCorrelationId &&
        msg.isInstanceOf[DeviceUpdateCanceled]
      }

    msg shouldNot be(empty)
  }

  testWithRepo("cannot cancel an update that is not in Assigned status") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdateReq = GenTargetUpdateRequest.generate
    val createRequest =
      CreateUpdateRequest(
        targets = Map(hardwareId -> targetUpdateReq),
        devices = Seq(device.deviceId)
      )

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
    }

    val updates = listUpdatesOK(device.deviceId)
    updates.values should have size 1
    val updateId = updates.values.head.updateId

    val manifest = buildPrimaryManifest(device.primary, device.primaryKey, targetUpdateReq.to)
    putManifestOk(device.deviceId, manifest)

    val completedUpdates = listUpdatesOK(device.deviceId)
    completedUpdates.values.head.status shouldBe Update.Status.Completed

    Patch(
      apiUri(s"updates/devices/${device.deviceId.show}/${updateId.show}")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Conflict
      val error = responseAs[ErrorRepresentation]
      error.code shouldBe ErrorCodes.UpdateCannotBeCancelled
    }
  }

  testWithRepo("can cancel an update when it is InFlight if force header is provided") {
    implicit ns =>
      val device = registerAdminDeviceOk()
      val hardwareId = device.primary.hardwareId
      val targetUpdate = GenTargetUpdate.generate
      val targetRequest = TargetUpdateRequest(None, targetUpdate)
      val createRequest = CreateUpdateRequest(
        targets = Map(hardwareId -> targetRequest),
        devices = Seq(device.deviceId)
      )

      createManyUpdates(createRequest) {
        status shouldBe StatusCodes.OK
        val result = responseAs[CreateUpdateResult]
        result.affected should contain(device.deviceId)
      }

      val updates = listUpdatesOK(device.deviceId)
      val update = updates.values.loneElement
      update.status shouldBe Update.Status.Assigned
      val updateId = update.updateId

      getTargetsOk(device.deviceId)

      Patch(
        apiUri(s"updates/devices/${device.deviceId.show}/${updateId.show}")
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.Conflict
        val error = responseAs[ErrorRepresentation]
        error.code.code shouldBe "update_cannot_be_cancelled"
      }

      Patch(apiUri(s"updates/devices/${device.deviceId.show}/${updateId.show}"))
        .addHeader(new ForceHeader(true))
        .namespaced ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
      }

      val cancelledUpdates = listUpdatesOK(device.deviceId)
      cancelledUpdates.values should have size 1
      cancelledUpdates.values.head.status shouldBe Update.Status.Cancelled

      val msg = msgPub
        .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
          msg.deviceUuid == device.deviceId && msg.correlationId == updateId.toCorrelationId &&
          msg.isInstanceOf[DeviceUpdateCanceled]
        }

      msg shouldNot be(empty)
  }

  testWithRepo("sends DeviceUpdateAssigned message when update is created with scheduledFor") {
    implicit ns =>
      val device = registerAdminDeviceOk()
      val hardwareId = device.primary.hardwareId

      val scheduledFor = Instant.now().plusSeconds(3600)
      val mtu = TargetUpdateSpec(Map(hardwareId -> GenTargetUpdateRequest.generate))
      createUpdateOk(device.deviceId, mtu, scheduledFor.some)

      val msg = msgPub
        .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
          msg.deviceUuid == device.deviceId && msg.isInstanceOf[DeviceUpdateAssigned]
        }
        .map(_.asInstanceOf[DeviceUpdateAssigned])
        .value

      msg.scheduledFor should contain(scheduledFor.truncatedTo(ChronoUnit.SECONDS))
  }

  testWithRepo("creating a scheduled update sets status to Scheduled") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId

    val scheduledFor = Instant.now().plusSeconds(3600)
    val targetUpdateRequest = GenTargetUpdateRequest.generate
    val mtu = TargetUpdateSpec(Map(hardwareId -> targetUpdateRequest))
    createUpdateOk(device.deviceId, mtu, scheduledFor.some)

    val scheduledUpdates = listUpdatesOK(device.deviceId)
    val update = scheduledUpdates.values.loneElement

    update.packages should contain(device.primary.hardwareId -> targetUpdateRequest.to.target)
    update.scheduledFor shouldBe Some(scheduledFor.truncatedTo(ChronoUnit.SECONDS))
    update.status shouldBe Update.Status.Scheduled
  }

  testWithRepo("returns error if device already has a scheduled update") { implicit ns =>
    val regDev = registerAdminDeviceOk()

    val scheduledFor = Instant.now()
    val mtu = TargetUpdateSpec(Map(regDev.primary.hardwareId -> GenTargetUpdateRequest.generate))
    createUpdateOk(regDev.deviceId, mtu, scheduledFor.some)

    val req = CreateDeviceUpdateRequest(
      targets = Map(regDev.primary.hardwareId -> GenTargetUpdateRequest.generate),
      scheduledFor = scheduledFor.some
    )

    Post(apiUri(s"updates/devices/${regDev.deviceId.show}"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      val error = responseAs[ErrorRepresentation]
      error.code shouldBe ErrorCodes.DeviceCannotBeUpdated

      val code =
        error.cause.value.as[Map[String, ErrorRepresentation]].value.values.loneElement.code
      code shouldBe ErrorCodes.DeviceHasActiveUpdate
    }
  }

  testWithRepo("can get update events by ID") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdateRequest.generate
    val createRequest =
      CreateUpdateRequest(targets = Map(hardwareId -> targetUpdate), devices = Seq(device.deviceId))

    val deviceT = genDeviceT.generate.copy(uuid = Some(device.deviceId))
    createDeviceInNamespaceOk(deviceT, ns)

    createManyUpdates(createRequest) {
      status shouldBe StatusCodes.OK
      val result = responseAs[CreateUpdateResult]
      result.affected should contain(device.deviceId)
    }

    val updates = listUpdatesOK(device.deviceId)
    val updateId = updates.values.loneElement.updateId

    val msg = DeviceEventMessage(
      ns,
      Event(
        device.deviceId,
        "someevent",
        EventType("InstallationComplete", 0),
        Instant.now.truncatedTo(ChronoUnit.SECONDS),
        Instant.now.truncatedTo(ChronoUnit.SECONDS),
        None,
        Json.obj("correlationId" -> updateId.toCorrelationId.toString.asJson)
      )
    )
    new DeviceEventListener().apply(msg).futureValue

    Get(
      apiUri(s"updates/devices/${device.deviceId.show}/${updateId.show}/events")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val event = responseAs[Seq[UpdateEventResponse]].loneElement
      event.receivedAt shouldBe msg.event.receivedAt
      event.ecu shouldBe msg.event.ecu
      event.eventType shouldBe msg.event.eventType
    }
  }

  testWithRepo("returns empty list when no events exist for update") { implicit ns =>
    val device = registerAdminDeviceOk()
    val newUpdate = GenTargetUpdateRequest.generate
    val mtu = TargetUpdateSpec(Map(device.primary.hardwareId -> newUpdate))

    createUpdateOk(device.deviceId, mtu)

    val updates = listUpdatesOK(device.deviceId)
    val updateId = updates.values.loneElement.updateId

    Get(
      apiUri(s"updates/devices/${device.deviceId.show}/${updateId.show}/events")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val events = responseAs[Seq[UpdateEventResponse]]
      events shouldBe empty
    }
  }

  testWithRepo("returns 404 when getting events for non-existent update") { implicit ns =>
    val device = registerAdminDeviceOk()
    val nonExistentUpdateId = UpdateId.generate()

    Get(
      apiUri(s"updates/devices/${device.deviceId.show}/${nonExistentUpdateId.show}/events")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }


  testWithRepo("can cancel all updates and assignments for all devices with a given update") {
    implicit ns =>
      val device1 = registerAdminDeviceOk()
      val device2 = registerAdminDeviceOk(hardwareIdentifier = device1.primary.hardwareId.some)

      val targetUpdate = GenTargetUpdate.generate
      val targetRequest = TargetUpdateRequest(None, targetUpdate)
      val createRequest = CreateUpdateRequest(
        targets = Map(device1.primary.hardwareId -> targetRequest),
        devices = Seq(device1.deviceId, device2.deviceId)
      )

      createManyUpdates(createRequest) {
        status shouldBe StatusCodes.OK
        val result = responseAs[CreateUpdateResult]
        (result.affected should contain).allOf(device1.deviceId, device2.deviceId)
      }

      val update1 = listUpdatesOK(device1.deviceId).values.loneElement
      update1.status shouldBe Update.Status.Assigned

      val update2 = listUpdatesOK(device2.deviceId).values.loneElement
      update2.status shouldBe Update.Status.Assigned

      update1.updateId shouldBe update2.updateId

      cancelAllUpdatesOK(update1.updateId)

      val cancelledUpdates1 = listUpdatesOK(device1.deviceId)
      cancelledUpdates1.values.loneElement.status shouldBe Update.Status.Cancelled

      val cancelledUpdates2 = listUpdatesOK(device2.deviceId)
      cancelledUpdates2.values.loneElement.status shouldBe Update.Status.Cancelled
  }

  testWithRepo("cancelling scheduled updates sends messages when the update is not yet assigned") { implicit ns =>
    val device = registerAdminDeviceOk()
    val hardwareId = device.primary.hardwareId
    val targetUpdate = GenTargetUpdate.generate
    val targetRequest = TargetUpdateRequest(None, targetUpdate)

    createUpdateOk(
      device.deviceId,
      TargetUpdateSpec(Map(hardwareId -> targetRequest)),
      Instant.now.minusSeconds(60).some
    )

    val updates = listUpdatesOK(device.deviceId)
    val update = updates.values.loneElement
    update.status shouldBe Update.Status.Scheduled
    val updateId = update.updateId

    cancelAllUpdatesOK(updateId)

    val cancelledUpdates = listUpdatesOK(device.deviceId)
    cancelledUpdates.values should have size 1
    cancelledUpdates.values.head.status shouldBe Update.Status.Cancelled

    val msg = msgPub
      .findReceived[DeviceUpdateEvent] { (msg: DeviceUpdateEvent) =>
        msg.deviceUuid == device.deviceId && msg.correlationId == updateId.toCorrelationId &&
          msg.isInstanceOf[DeviceUpdateCanceled]
      }

    msg shouldNot be(empty)
  }

}
