package com.advancedtelematic.director.db

import cats.implicits.catsSyntaxOptionId
import com.advancedtelematic.director.data.AdminDataType.{
  RegisterEcu,
  TargetUpdateRequest,
  TargetUpdateSpec
}
import com.advancedtelematic.director.data.DataType.{Update, UpdateId}
import com.advancedtelematic.director.data.DbDataType.Ecu
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.data.Generators.*
import com.advancedtelematic.director.db.ProvisionedDeviceRepository.DeviceCreateResult
import com.advancedtelematic.director.db.deviceregistry.DeviceRepository
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.genDeviceT
import com.advancedtelematic.director.http.DeviceAssignments
import com.advancedtelematic.director.util.DirectorSpec
import com.advancedtelematic.libats.data.DataType.{MultiTargetUpdateCorrelationId, Namespace}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier}
import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import io.circe.Json
import org.scalatest.LoneElement.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UpdateSchedulerDBIOSpec
    extends DirectorSpec
    with MysqlDatabaseSpec
    with UpdatesRepositorySupport
    with AssignmentsRepositorySupport
    with EcuRepositorySupport
    with ProvisionedDeviceRepositorySupport {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  val targetUpdateSpecs = new TargetUpdateSpecs()

  val deviceAssignments = new DeviceAssignments()

  val updateSchedulerIO = new UpdateSchedulerDBIO()

  val updatesDBIO = new UpdatesDBIO()

  def createScheduledUpdate(device: DeviceId,
                            mtu: Map[HardwareIdentifier, TargetUpdateRequest],
                            ecuId: EcuIdentifier,
                            registerEcus: RegisterEcu*)(implicit ns: Namespace): UpdateId = {
    createDevice(device, ecuId, registerEcus.map(_.toEcu(ns, device))*).futureValue

    updatesDBIO.createFor(ns, device, TargetUpdateSpec(mtu), Instant.now.some).futureValue
  }

  private def createDevice(deviceId: DeviceId, ecuId: EcuIdentifier, ecus: Ecu*)(
    implicit ns: Namespace): Future[DeviceCreateResult] = {
    val deviceT = genDeviceT.generate.copy(
      uuid = Option(deviceId),
      deviceId = DeviceOemId(s"oemid-${deviceId.uuid}")
    )
    db.run(DeviceRepository.create(ns, deviceT)).futureValue

    provisionedDeviceRepository
      .create(ecuRepository)(ns, deviceId, ecuId, ecus)
  }

  private def buildMtu(hardwareId: HardwareIdentifier): TargetUpdateSpec =
    TargetUpdateSpec(Map(hardwareId -> GenTargetUpdateRequest.generate))

  testWithNamespace("creates assignment for a device") { implicit ns =>
    val device = DeviceId.generate()
    val mtu = Map(GenHardwareIdentifier.generate -> GenTargetUpdateRequest.generate)
    val registerEcu = GenRegisterEcu.generate.copy(hardware_identifier = mtu.keys.head)

    val updateId = createScheduledUpdate(device, mtu, registerEcu.ecu_serial, registerEcu)

    updateSchedulerIO.run().futureValue

    val assignments = assignmentsRepository.findBy(device).futureValue

    assignments shouldNot be(empty)

    assignments.loneElement.deviceId shouldBe device
    assignments.loneElement.ecuId shouldBe registerEcu.ecu_serial
    assignments.loneElement.correlationId shouldBe updateId.toCorrelationId

    updatesRepository
      .findFor(ns, device)
      .futureValue
      .loneElement
      .status shouldBe Update.Status.Assigned
  }

  testWithNamespace("creates assignment for all ecus in an MTU") { implicit ns =>
    val primaryRegisterEcu = GenRegisterEcu.generate
    val secondaryRegisterEcu = GenRegisterEcu.generate
    val primaryHardwareId = primaryRegisterEcu.hardware_identifier
    val secondaryHardwareId = secondaryRegisterEcu.hardware_identifier

    val mtu = Map(
      secondaryHardwareId -> GenTargetUpdateRequest.generate,
      primaryHardwareId -> GenTargetUpdateRequest.generate
    )
    val device = DeviceId.generate()
    val updateId = createScheduledUpdate(
      device,
      mtu,
      primaryRegisterEcu.ecu_serial,
      primaryRegisterEcu,
      secondaryRegisterEcu
    )

    updateSchedulerIO.run().futureValue

    val assignments = assignmentsRepository.findBy(device).futureValue

    assignments should have size 2

    assignments.map(_.deviceId) should contain only device
    assignments.map(_.ecuId) should contain theSameElementsAs Seq(
      primaryRegisterEcu.ecu_serial,
      secondaryRegisterEcu.ecu_serial
    )
    assignments.map(_.correlationId) should contain only updateId.toCorrelationId

    updatesRepository
      .findFor(ns, device)
      .futureValue
      .loneElement
      .status shouldBe Update.Status.Assigned
  }

  testWithNamespace("cancels/terminates scheduled update when device not compatible") {
    implicit ns =>
      val primaryRegisterEcu = GenRegisterEcu.generate
      val primaryHardwareId = primaryRegisterEcu.hardware_identifier
      val device = DeviceId.generate()

      val mtu = TargetUpdateSpec(
        Map(
          primaryHardwareId -> GenTargetUpdateRequest.generate
            .copy(from = Some(GenTargetUpdate.generate))
        )
      )
      val targetSpecId = targetUpdateSpecs.create(ns, mtu).futureValue

      provisionedDeviceRepository
        .create(ecuRepository)(
          ns,
          device,
          primaryRegisterEcu.ecu_serial,
          Seq(primaryRegisterEcu.toEcu(ns, device))
        )
        .futureValue

      val id = UpdateId.generate()

      val scheduledUpdate = Update(
        ns,
        id,
        device,
        id.toCorrelationId,
        targetSpecId,
        Instant.now(),
        Instant.now().some,
        Update.Status.Scheduled
      )
      updatesRepository.persist(scheduledUpdate).futureValue

      updateSchedulerIO.run().futureValue

      val assignments = assignmentsRepository.findBy(device).futureValue

      assignments shouldBe empty

      val scheduledUpdateAfter =
        updatesRepository.findFor(ns, device).futureValue.loneElement
      scheduledUpdateAfter.status shouldBe Update.Status.Cancelled
  }

  testWithNamespace("cancels terminates when device does not have compatible ecu hardware at all") {
    implicit ns =>
      val primaryRegisterEcu = GenRegisterEcu.generate
      val mtu =
        TargetUpdateSpec(Map(GenHardwareIdentifier.generate -> GenTargetUpdateRequest.generate))
      val targetSpecId = targetUpdateSpecs.create(ns, mtu).futureValue
      val device = DeviceId.generate()

      createDevice(
        device,
        primaryRegisterEcu.ecu_serial,
        primaryRegisterEcu.toEcu(ns, device)
      ).futureValue

      val id = UpdateId.generate()

      val scheduledUpdate = Update(
        ns,
        id,
        device,
        id.toCorrelationId,
        targetSpecId,
        Instant.now,
        Instant.now().some,
        Update.Status.Scheduled
      )
      updatesRepository.persist(scheduledUpdate).futureValue

      updateSchedulerIO.run().futureValue

      val assignments = assignmentsRepository.findBy(device).futureValue

      assignments shouldBe empty

      updatesRepository
        .findFor(ns, device)
        .futureValue
        .loneElement
        .status shouldBe Update.Status.Cancelled
  }

  testWithNamespace(
    "cancels/terminates scheduled update when device/ecu has an assignment already"
  ) { implicit ns =>
    val registerEcu = GenRegisterEcu.generate

    val mtu =
      TargetUpdateSpec(Map(registerEcu.hardware_identifier -> GenTargetUpdateRequest.generate))
    val targetSpecId = targetUpdateSpecs.create(ns, mtu).futureValue
    val device = DeviceId.generate()

    createDevice(device, registerEcu.ecu_serial, registerEcu.toEcu(ns, device)).futureValue

    val mtuExisting =
      TargetUpdateSpec(Map(registerEcu.hardware_identifier -> GenTargetUpdateRequest.generate))
    val TargetSpecIdExisting = targetUpdateSpecs.create(ns, mtuExisting).futureValue
    val assignedTo = deviceAssignments
      .createForDevice(
        ns,
        MultiTargetUpdateCorrelationId(TargetSpecIdExisting.uuid),
        device,
        TargetSpecIdExisting
      )
      .futureValue
    assignedTo shouldBe device

    val id = UpdateId.generate()

    val scheduledUpdate = Update(
      ns,
      id,
      device,
      id.toCorrelationId,
      targetSpecId,
      Instant.now,
      Instant.now().some,
      Update.Status.Scheduled
    )
    updatesRepository.persist(scheduledUpdate).futureValue

    updateSchedulerIO.run().futureValue

    val assignments = assignmentsRepository.findBy(device).futureValue

    val createdAssignment = assignments.loneElement

    createdAssignment.deviceId shouldBe device
    createdAssignment.ecuId shouldBe registerEcu.ecu_serial
    createdAssignment.correlationId shouldBe MultiTargetUpdateCorrelationId(
      TargetSpecIdExisting.uuid
    )

    updatesRepository
      .findFor(ns, device)
      .futureValue
      .loneElement
      .status shouldBe Update.Status.Cancelled
  }

  testWithNamespace("creates assignments for future schedules only") { implicit ns =>
    val registerEcu = GenRegisterEcu.generate
    val registerEcu2 = GenRegisterEcu.generate
    val device = DeviceId.generate()
    val device2 = DeviceId.generate()

    val mtu1 = buildMtu(registerEcu.hardware_identifier)
    val mtu2 = buildMtu(registerEcu2.hardware_identifier)

    createDevice(device, registerEcu.ecu_serial, registerEcu.toEcu(ns, device)).futureValue

    createDevice(device2, registerEcu2.ecu_serial, registerEcu2.toEcu(ns, device2)).futureValue

    val scheduledUpdateId =
      updatesDBIO.createFor(ns, device, mtu1, Instant.now.some).futureValue
    val scheduledUpdateId2 =
      updatesDBIO.createFor(ns, device2, mtu2, Instant.now.plusSeconds(360).some).futureValue

    updateSchedulerIO.run().futureValue

    val assignment = assignmentsRepository.findBy(device).futureValue.loneElement

    assignment.deviceId shouldBe device
    assignment.correlationId shouldBe scheduledUpdateId.toCorrelationId

    val updatedScheduledUpdates = updatesRepository.findFor(ns, device).futureValue
    updatedScheduledUpdates should have size 1

    updatedScheduledUpdates.map(u => u.id -> u.status).toMap shouldBe Map(
      scheduledUpdateId -> Update.Status.Assigned
    )

    val updatedScheduledUpdates2 =
      updatesRepository.findFor(ns, device2).futureValue
    updatedScheduledUpdates2 should have size 1

    updatedScheduledUpdates2.map(u => u.id -> u.status).toMap shouldBe Map(
      scheduledUpdateId2 -> Update.Status.Scheduled
    )
  }

  testWithNamespace("handles schedules for Scheduled updates only") { implicit ns =>
    val registerEcu = GenRegisterEcu.generate
    val device = DeviceId.generate()

    val mtu = buildMtu(registerEcu.hardware_identifier)
    val mtu1 = buildMtu(registerEcu.hardware_identifier)

    createDevice(device, registerEcu.ecu_serial, registerEcu.toEcu(ns, device)).futureValue

    val cancelledTargetSpecId =
      updatesDBIO.createFor(ns, device, mtu1, Instant.now.some).futureValue
    db.run(
      updatesRepository
        .setStatusAction[Json](ns, cancelledTargetSpecId, Update.Status.Cancelled)
    ).futureValue

    val scheduledUpdateId =
      updatesDBIO.createFor(ns, device, mtu, Instant.now.some).futureValue

    updateSchedulerIO.run().futureValue

    val assignment = assignmentsRepository.findBy(device).futureValue.loneElement

    assignment.deviceId shouldBe device
    assignment.correlationId shouldBe scheduledUpdateId.toCorrelationId

    val scheduledUpdates = updatesRepository.findFor(ns, device).futureValue
    scheduledUpdates should have size 2

    scheduledUpdates.map(_.id) should contain theSameElementsAs List(
      scheduledUpdateId,
      cancelledTargetSpecId
    )
    scheduledUpdates.map(_.status) should contain theSameElementsAs List(
      Update.Status.Assigned,
      Update.Status.Cancelled
    )
  }

  test("works for a larger group of devices")(pending)
}
