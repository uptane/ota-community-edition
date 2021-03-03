package com.advancedtelematic.director.http

import java.time.Instant

import cats.implicits._
import com.advancedtelematic.director.data.AdminDataType.QueueResponse
import com.advancedtelematic.director.data.DbDataType.Assignment
import com.advancedtelematic.director.data.UptaneDataType.{TargetImage, _}
import com.advancedtelematic.director.db._
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateCanceled, DeviceUpdateEvent}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class DeviceAssignments(implicit val db: Database, val ec: ExecutionContext) extends EcuRepositorySupport
  with HardwareUpdateRepositorySupport with AssignmentsRepositorySupport with EcuTargetsRepositorySupport with DeviceRepositorySupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  import scala.async.Async._

  def findDeviceAssignments(ns: Namespace, deviceId: DeviceId): Future[Vector[QueueResponse]] = async {

    val correlationIdToAssignments = await(assignmentsRepository.findBy(deviceId)).groupBy(_.correlationId)

    val deviceQueues =
      correlationIdToAssignments.map { case (correlationId, assignments) =>
        val images = assignments.map { assignment =>
          ecuTargetsRepository.find(ns, assignment.ecuTargetId).map { target =>
            assignment.ecuId -> TargetImage(Image(target.filename, FileInfo(Hashes(target.sha256), target.length)), target.uri)
          }
        }.toList.sequence

        val queue = images.map(_.toMap).map { images =>
          val inFlight = correlationIdToAssignments.get(correlationId).exists(_.exists(_.inFlight))
          QueueResponse(correlationId, images, inFlight = inFlight)
        }

        queue
      }

    await(Future.sequence(deviceQueues)).toVector
  }

  def findAffectedDevices(ns: Namespace, deviceIds: Seq[DeviceId], mtuId: UpdateId): Future[Seq[DeviceId]] = {
    findAffectedEcus(ns, deviceIds, mtuId).map { _.map(_._1.deviceId) }
  }

  import cats.syntax.option._

  private def findAffectedEcus(ns: Namespace, devices: Seq[DeviceId], mtuId: UpdateId) = async {
    val hardwareUpdates = await(hardwareUpdateRepository.findBy(ns, mtuId))

    val allTargetIds = hardwareUpdates.values.flatMap(v => List(v.toTarget.some, v.fromTarget).flatten)
    val allTargets = await(ecuTargetsRepository.findAll(ns, allTargetIds.toSeq))

    val ecus = await(ecuRepository.findEcuWithTargets(devices.toSet, hardwareUpdates.keys.toSet)).flatMap { case (ecu, installedTarget) =>
      val hwUpdate = hardwareUpdates(ecu.hardwareId)
      val updateFrom = hwUpdate.fromTarget.flatMap(allTargets.get)
      val updateTo = allTargets(hwUpdate.toTarget)

      if (hwUpdate.fromTarget.isEmpty || installedTarget.zip(updateFrom).exists { case (a, b) => a matches b }) {
        if(installedTarget.exists(_.matches(updateTo))) {
          _log.info(s"Ecu ${ecu.deviceId}/${ecu.ecuSerial} not affected for $hwUpdate, installed target is already the target update")
          None
        } else {
          _log.info(s"${ecu.deviceId}/${ecu.ecuSerial} affected for $hwUpdate")
          Some(ecu -> hwUpdate.toTarget)
        }
      } else {
        _log.info(s"ecu ${ecu.deviceId}${ecu.ecuSerial} not affected by $mtuId")
        None
      }
    }

    val ecuIds = ecus.map { case (ecu, _) => ecu.deviceId -> ecu.ecuSerial }.toSet
    val ecusWithAssignments = await(assignmentsRepository.withAssignments(ecuIds))

    ecus.flatMap {
      case (ecu, _) if ecusWithAssignments.contains(ecu.deviceId -> ecu.ecuSerial) =>
        _log.info(s"${ecu.deviceId}/${ecu.ecuSerial} not affected because ecu has a running assignment")
        None
      case other =>
        Some(other)
    }
  }

  def createForDevice(ns: Namespace, correlationId: CorrelationId, deviceId: DeviceId, mtuId: UpdateId): Future[Assignment] = {
    createForDevices(ns, correlationId, List(deviceId), mtuId).map(_.head) // TODO: This HEAD is problematic
  }

  def createForDevices(ns: Namespace, correlationId: CorrelationId, devices: Seq[DeviceId], mtuId: UpdateId): Future[Seq[Assignment]] = async {
    val ecus = await(findAffectedEcus(ns, devices, mtuId))

    _log.debug(s"$ns $correlationId $devices $mtuId")

    if(ecus.isEmpty) {
      _log.warn(s"No devices affected for this assignment: $ns, $correlationId, $devices, $mtuId")
      Seq.empty[Assignment]
    } else {
      val assignments = ecus.foldLeft(List.empty[Assignment]) { case (acc, (ecu, toTargetId)) =>
        Assignment(ns, ecu.deviceId, ecu.ecuSerial, toTargetId, correlationId, inFlight = false) :: acc
      }

      await(assignmentsRepository.persistMany(deviceRepository)(assignments))

      assignments
    }
  }

  def cancel(namespace: Namespace, devices: Seq[DeviceId])(implicit messageBusPublisher: MessageBusPublisher): Future[Seq[Assignment]] = {
    assignmentsRepository.processCancellation(namespace, devices).flatMap { canceledAssignments =>
      Future.traverse(canceledAssignments) { canceledAssignment =>
        val ev: DeviceUpdateEvent =
          DeviceUpdateCanceled(namespace, Instant.now, canceledAssignment.correlationId, canceledAssignment.deviceId)
        messageBusPublisher.publish(ev).map(_ => canceledAssignment)
      }
    }
  }
}
