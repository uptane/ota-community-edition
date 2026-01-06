package com.advancedtelematic.director.db

import com.advancedtelematic.director.daemon.UpdateScheduler
import com.advancedtelematic.director.data.AdminDataType.{
  MultiTargetUpdate,
  RegisterEcu,
  TargetUpdateRequest
}
import com.advancedtelematic.director.data.DataType.{ScheduledUpdate, ScheduledUpdateId}
import com.advancedtelematic.director.data.DbDataType.Ecu
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.data.Generators.*
import com.advancedtelematic.director.db.ProvisionedDeviceRepository.DeviceCreateResult
import com.advancedtelematic.director.db.UpdateSchedulerDBIO.invalidEcuStatusCodec
import com.advancedtelematic.director.db.deviceregistry.DeviceRepository
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.genDeviceT
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus
import com.advancedtelematic.director.http.DeviceAssignments
import com.advancedtelematic.director.util.DirectorSpec
import com.advancedtelematic.libats.data.DataType.{MultiTargetUpdateId, Namespace}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import org.scalatest.LoneElement.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UpdateSchedulerDBIOSpec
    extends DirectorSpec
    with MysqlDatabaseSpec
    with ScheduledUpdatesRepositorySupport
    with AssignmentsRepositorySupport
    with EcuRepositorySupport
    with ProvisionedDeviceRepositorySupport {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private implicit val msgPub: MessageBusPublisher = new MockMessageBus

  val multiTargetUpdates = new MultiTargetUpdates()

  val deviceAssignments = new DeviceAssignments()

  val updateSchedulerIO = new UpdateSchedulerDBIO()

  val updateScheduler = new UpdateScheduler()

  def createScheduledUpdate(device: DeviceId,
                            _mtu: Map[HardwareIdentifier, TargetUpdateRequest],
                            ecuId: EcuIdentifier,
                            registerEcus: RegisterEcu*)(implicit ns: Namespace) = {
    val mtu = MultiTargetUpdate(_mtu)
    val updateId = multiTargetUpdates.create(ns, mtu).futureValue

    createDevice(device, ecuId, registerEcus.map(_.toEcu(ns, device))*).futureValue

    updateScheduler.create(ns, device, updateId, Instant.now).futureValue

    updateId
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

  private def createMtu(hardwareId: HardwareIdentifier)(implicit ns: Namespace): UpdateId = {
    val mtu = MultiTargetUpdate(Map(hardwareId -> GenTargetUpdateRequest.generate))
    multiTargetUpdates.create(ns, mtu).futureValue
  }

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
    assignments.loneElement.correlationId shouldBe MultiTargetUpdateId(updateId.uuid)

    scheduledUpdatesRepository
      .findFor(ns, device)
      .futureValue
      .values
      .loneElement
      .status shouldBe ScheduledUpdate.Status.Assigned
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
    assignments.map(_.correlationId) should contain only MultiTargetUpdateId(updateId.uuid)

    scheduledUpdatesRepository
      .findFor(ns, device)
      .futureValue
      .values
      .loneElement
      .status shouldBe ScheduledUpdate.Status.Assigned
  }

  testWithNamespace("cancels/terminates scheduled update when device not compatible") {
    implicit ns =>
      val primaryRegisterEcu = GenRegisterEcu.generate
      val primaryHardwareId = primaryRegisterEcu.hardware_identifier
      val device = DeviceId.generate()

      val mtu = MultiTargetUpdate(
        Map(
          primaryHardwareId -> GenTargetUpdateRequest.generate
            .copy(from = Some(GenTargetUpdate.generate))
        )
      )
      val updateId = multiTargetUpdates.create(ns, mtu).futureValue

      provisionedDeviceRepository
        .create(ecuRepository)(
          ns,
          device,
          primaryRegisterEcu.ecu_serial,
          Seq(primaryRegisterEcu.toEcu(ns, device))
        )
        .futureValue

      val scheduledUpdate = ScheduledUpdate(
        ns,
        ScheduledUpdateId.generate(),
        device,
        updateId,
        Instant.now(),
        ScheduledUpdate.Status.Scheduled
      )
      scheduledUpdatesRepository.persist(scheduledUpdate).futureValue

      updateSchedulerIO.run().futureValue

      val assignments = assignmentsRepository.findBy(device).futureValue

      assignments shouldBe empty

      val scheduledUpdateAfter =
        scheduledUpdatesRepository.findFor(ns, device).futureValue.values.loneElement
      scheduledUpdateAfter.status shouldBe ScheduledUpdate.Status.Cancelled
  }

  testWithNamespace("cancels terminates when device does not have compatible ecu hardware at all") {
    implicit ns =>
      val primaryRegisterEcu = GenRegisterEcu.generate
      val mtu =
        MultiTargetUpdate(Map(GenHardwareIdentifier.generate -> GenTargetUpdateRequest.generate))
      val updateId = multiTargetUpdates.create(ns, mtu).futureValue
      val device = DeviceId.generate()

      createDevice(
        device,
        primaryRegisterEcu.ecu_serial,
        primaryRegisterEcu.toEcu(ns, device)
      ).futureValue

      val scheduledUpdate = ScheduledUpdate(
        ns,
        ScheduledUpdateId.generate(),
        device,
        updateId,
        Instant.now(),
        ScheduledUpdate.Status.Scheduled
      )
      scheduledUpdatesRepository.persist(scheduledUpdate).futureValue

      updateSchedulerIO.run().futureValue

      val assignments = assignmentsRepository.findBy(device).futureValue

      assignments shouldBe empty

      scheduledUpdatesRepository
        .findFor(ns, device)
        .futureValue
        .values
        .loneElement
        .status shouldBe ScheduledUpdate.Status.Cancelled
  }

  testWithNamespace(
    "cancels/terminates scheduled update when device/ecu has an assignment already"
  ) { implicit ns =>
    val registerEcu = GenRegisterEcu.generate

    val mtu =
      MultiTargetUpdate(Map(registerEcu.hardware_identifier -> GenTargetUpdateRequest.generate))
    val updateId = multiTargetUpdates.create(ns, mtu).futureValue
    val device = DeviceId.generate()

    createDevice(device, registerEcu.ecu_serial, registerEcu.toEcu(ns, device)).futureValue

    val mtuExisting =
      MultiTargetUpdate(Map(registerEcu.hardware_identifier -> GenTargetUpdateRequest.generate))
    val updateIdExisting = multiTargetUpdates.create(ns, mtuExisting).futureValue
    val assignedTo = deviceAssignments
      .createForDevice(ns, MultiTargetUpdateId(updateIdExisting.uuid), device, updateIdExisting)
      .futureValue
    assignedTo shouldBe device

    val scheduledUpdate = ScheduledUpdate(
      ns,
      ScheduledUpdateId.generate(),
      device,
      updateId,
      Instant.now(),
      ScheduledUpdate.Status.Scheduled
    )
    scheduledUpdatesRepository.persist(scheduledUpdate).futureValue

    updateSchedulerIO.run().futureValue

    val assignments = assignmentsRepository.findBy(device).futureValue

    val createdAssignment = assignments.loneElement

    createdAssignment.deviceId shouldBe device
    createdAssignment.ecuId shouldBe registerEcu.ecu_serial
    createdAssignment.correlationId shouldBe MultiTargetUpdateId(updateIdExisting.uuid)

    scheduledUpdatesRepository
      .findFor(ns, device)
      .futureValue
      .values
      .loneElement
      .status shouldBe ScheduledUpdate.Status.Cancelled
  }

  testWithNamespace("creates assignments for future schedules only") { implicit ns =>
    val registerEcu = GenRegisterEcu.generate
    val registerEcu2 = GenRegisterEcu.generate
    val device = DeviceId.generate()
    val device2 = DeviceId.generate()

    val updateId = createMtu(registerEcu.hardware_identifier)
    val updateId2 = createMtu(registerEcu2.hardware_identifier)

    createDevice(device, registerEcu.ecu_serial, registerEcu.toEcu(ns, device)).futureValue

    createDevice(device2, registerEcu2.ecu_serial, registerEcu2.toEcu(ns, device2)).futureValue

    val scheduledUpdateId = updateScheduler.create(ns, device, updateId, Instant.now).futureValue
    val scheduledUpdateId2 =
      updateScheduler.create(ns, device2, updateId2, Instant.now.plusSeconds(360)).futureValue

    updateSchedulerIO.run().futureValue

    val assignment = assignmentsRepository.findBy(device).futureValue.loneElement

    assignment.deviceId shouldBe device
    assignment.correlationId shouldBe MultiTargetUpdateId(updateId.uuid)

    val updatedScheduledUpdates = scheduledUpdatesRepository.findFor(ns, device).futureValue.values
    updatedScheduledUpdates should have size 1

    updatedScheduledUpdates.map(u => u.id -> u.status).toMap shouldBe Map(
      scheduledUpdateId -> ScheduledUpdate.Status.Assigned
    )

    val updatedScheduledUpdates2 =
      scheduledUpdatesRepository.findFor(ns, device2).futureValue.values
    updatedScheduledUpdates2 should have size 1

    updatedScheduledUpdates2.map(u => u.id -> u.status).toMap shouldBe Map(
      scheduledUpdateId2 -> ScheduledUpdate.Status.Scheduled
    )
  }

  testWithNamespace("handles schedules for Scheduled updates only") { implicit ns =>
    val registerEcu = GenRegisterEcu.generate
    val device = DeviceId.generate()

    val updateId = createMtu(registerEcu.hardware_identifier)
    val updateId1 = createMtu(registerEcu.hardware_identifier)

    createDevice(device, registerEcu.ecu_serial, registerEcu.toEcu(ns, device)).futureValue

    val cancelledUpdateId = updateScheduler.create(ns, device, updateId1, Instant.now).futureValue
    db.run(
      scheduledUpdatesRepository
        .setStatusAction(ns, cancelledUpdateId, ScheduledUpdate.Status.Cancelled)
    ).futureValue

    val scheduledUpdateId = updateScheduler.create(ns, device, updateId, Instant.now).futureValue

    updateSchedulerIO.run().futureValue

    val assignment = assignmentsRepository.findBy(device).futureValue.loneElement

    assignment.deviceId shouldBe device
    assignment.correlationId shouldBe MultiTargetUpdateId(updateId.uuid)

    val scheduledUpdates = scheduledUpdatesRepository.findFor(ns, device).futureValue.values
    scheduledUpdates should have size 2

    scheduledUpdates.map(_.id) should contain theSameElementsAs List(
      scheduledUpdateId,
      cancelledUpdateId
    )
    scheduledUpdates.map(_.status) should contain theSameElementsAs List(
      ScheduledUpdate.Status.Assigned,
      ScheduledUpdate.Status.Cancelled
    )
  }

  testWithNamespace("updates device status to UpdateScheduled when assignment is created") {
    implicit ns =>
      val device = DeviceId.generate()
      val mtu = Map(GenHardwareIdentifier.generate -> GenTargetUpdateRequest.generate)
      val registerEcu = GenRegisterEcu.generate.copy(hardware_identifier = mtu.keys.head)

      createScheduledUpdate(device, mtu, registerEcu.ecu_serial, registerEcu)

      updateSchedulerIO.run().futureValue

      val deviceStatus = db.run(DeviceRepository.findByUuid(device)).futureValue.deviceStatus
      deviceStatus shouldBe DeviceStatus.UpdateScheduled

      val assignments = assignmentsRepository.findBy(device).futureValue
      assignments.loneElement.deviceId shouldBe device
  }

  test("works for a larger group of devices")(pending)
}
