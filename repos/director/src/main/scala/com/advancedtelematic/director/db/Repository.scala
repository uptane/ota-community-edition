package com.advancedtelematic.director.db

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.show.*
import com.advancedtelematic.director.data.DataType.{AdminRoleName, TargetSpecId, Update, UpdateId}
import com.advancedtelematic.director.data.DbDataType.{
  Assignment,
  AutoUpdateDefinition,
  AutoUpdateDefinitionId,
  DbAdminRole,
  DbDeviceRole,
  Device,
  Ecu,
  EcuTarget,
  EcuTargetId,
  HardwareUpdate,
  MurmurHash3Checksum,
  ProcessedAssignment,
  ValidMurmurHash3Checksum
}
import com.advancedtelematic.director.db.AdminRolesRepository.{
  Deleted,
  FindLatestResult,
  NotDeleted
}
import com.advancedtelematic.director.db.ProvisionedDeviceRepository.DeviceCreateResult
import com.advancedtelematic.director.db.Schema.{adminRoles, notDeletedAdminRoles, AssignmentsTable}
import com.advancedtelematic.director.db.SlickMapping.*
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.data.PaginationResult.{Limit, LongAsParam, Offset}
import com.advancedtelematic.libats.data.RefinedUtils.*
import com.advancedtelematic.libats.http.Errors.{
  EntityAlreadyExists,
  MissingEntity,
  MissingEntityId
}
import com.advancedtelematic.libats.messaging_datatype.DataType.{
  DeviceId,
  EcuIdentifier,
  ValidEcuIdentifier
}
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  EcuAndHardwareId,
  EcuReplaced,
  EcuReplacement
}
import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.jsonMapper
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickUrnMapper.*
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  RepoId,
  RoleType,
  TargetFilename,
  TargetName
}
import com.advancedtelematic.libtuf_server.data.TufSlickMappings.*
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import org.apache.pekko.http.scaladsl.util.FastFuture
import slick.jdbc.GetResult
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.hashing.MurmurHash3

protected trait DatabaseSupport {
  implicit val ec: ExecutionContext
  implicit val db: Database
}

trait ProvisionedDeviceRepositorySupport extends DatabaseSupport {
  lazy val provisionedDeviceRepository = new ProvisionedDeviceRepository()
}

object ProvisionedDeviceRepository {
  sealed trait DeviceCreateResult
  case object Created extends DeviceCreateResult

  case class Updated(deviceId: DeviceId,
                     replacedPrimary: Option[(EcuAndHardwareId, EcuAndHardwareId)],
                     removedSecondaries: Seq[EcuAndHardwareId],
                     addedSecondaries: Seq[EcuAndHardwareId],
                     when: Instant)
      extends DeviceCreateResult

  implicit class DeviceUpdateResultCreatedOps(updated: Updated) {

    def asEcuReplacedSeq: Seq[EcuReplacement] = {
      val primaryReplacement =
        updated.replacedPrimary.map { case (oldEcu, newEcu) =>
          EcuReplaced(updated.deviceId, oldEcu, newEcu, updated.when)
        }
      // Non-deterministic multiple secondary replacements: if there's more than one replacement, there's no way of knowing which secondary replaced which.
      val secondaryReplacements =
        updated.removedSecondaries.zip(updated.addedSecondaries).map { case (oldEcu, newEcu) =>
          EcuReplaced(updated.deviceId, oldEcu, newEcu, updated.when)
        }
      primaryReplacement.fold(secondaryReplacements)(_ +: secondaryReplacements)
    }

  }

}

protected class ProvisionedDeviceRepository()(implicit val db: Database, val ec: ExecutionContext) {

  def create(ecuRepository: EcuRepository)(ns: Namespace,
                                           deviceId: DeviceId,
                                           primaryEcuId: EcuIdentifier,
                                           ecus: Seq[Ecu]): Future[DeviceCreateResult] = {
    val device =
      Device(ns, deviceId, primaryEcuId, generatedMetadataOutdated = true, deleted = false)

    val io = existsIO(deviceId).flatMap {
      case false => // New Device and Ecus
        (Schema.allEcus ++= ecus)
          .andThen(Schema.allProvisionedDevices += device)
          .map(_ => ProvisionedDeviceRepository.Created)
      case true => // Update Device and Ecus, potentially replace Ecus
        replaceEcusAndIdentifyReplacements(ecuRepository)(ns, device, ecus)
    }

    db.run(io.transactionally)
  }

  protected[db] def ensureExistsIO(device: DeviceId): DBIO[Unit] =
    existsIO(device).flatMap {
      case true  => DBIO.successful(())
      case false => DBIO.failed(MissingEntity[Device]())
    }

  private def existsIO(deviceId: DeviceId): DBIO[Boolean] =
    Schema.allProvisionedDevices.filter(_.id === deviceId).exists.result

  def exists(deviceId: DeviceId): Future[Boolean] =
    db.run(existsIO(deviceId))

  def findAllDeviceIds(ns: Namespace,
                       offset: Offset,
                       limit: Limit): Future[PaginationResult[DeviceId]] = db.run {
    Schema.activeProvisionedDevices
      .filter(_.namespace === ns)
      .map(d => (d.id, d.createdAt))
      .paginateAndSortResult(_._2, offset = offset, limit = limit)
      .map(_.map(_._1))
  }

  def findDevices(ns: Namespace,
                  hardwareIdentifier: HardwareIdentifier,
                  offset: Offset,
                  limit: Limit): Future[PaginationResult[(Instant, Device)]] = db.run {
    Schema.activeProvisionedDevices
      .filter(_.namespace === ns)
      .join(Schema.activeEcus.filter(_.hardwareId === hardwareIdentifier))
      .on { case (d, e) => d.primaryEcu === e.ecuSerial }
      .map(_._1)
      .map(r => r.createdAt -> r)
      .paginateAndSortResult(_._1.desc, offset = offset, limit = limit)
  }

  private def replaceEcusAndIdentifyReplacements(ecuRepository: EcuRepository)(
    ns: Namespace,
    device: Device,
    ecus: Seq[Ecu]): DBIO[ProvisionedDeviceRepository.Updated] = {
    val beforeReplacement = for {
      ecus <- Schema.activeEcus.filter(_.namespace === ns).filter(_.deviceId === device.id).result
      primary <- ecuRepository.findDevicePrimaryAction(ns, device.id)
      secondaries = ecus.filterNot(_.ecuSerial == primary.ecuSerial)
    } yield primary -> secondaries

    val afterReplacement = for {
      ecus <- replaceDeviceEcus(ecuRepository)(ns, device, ecus)
      primary <- ecuRepository.findDevicePrimaryAction(ns, device.id)
      secondaries = ecus.filterNot(_.ecuSerial == primary.ecuSerial)
    } yield primary -> secondaries

    for {
      (beforePrimary, beforeSecondaries) <- beforeReplacement
      (afterPrimary, afterSecondaries) <- afterReplacement
      replacedPrimary =
        if (beforePrimary.ecuSerial == afterPrimary.ecuSerial) None
        else Some(beforePrimary.asEcuAndHardwareId -> afterPrimary.asEcuAndHardwareId)
      removed = beforeSecondaries
        .filterNot(e => afterSecondaries.map(_.ecuSerial).contains(e.ecuSerial))
        .map(_.asEcuAndHardwareId)
      added = afterSecondaries
        .filterNot(e => beforeSecondaries.map(_.ecuSerial).contains(e.ecuSerial))
        .map(_.asEcuAndHardwareId)
    } yield ProvisionedDeviceRepository.Updated(
      device.id,
      replacedPrimary,
      removed,
      added,
      Instant.now
    )
  }

  private def replaceDeviceEcus(
    ecuRepository: EcuRepository)(ns: Namespace, device: Device, ecus: Seq[Ecu]): DBIO[Seq[Ecu]] = {
    // A running assignment is allowed, as long as the new list of ECUs includes all ECUS that have an assignment (OTA-1366)n
    val ensureRunningAssignmentsValid =
      Schema.assignments.filter(_.deviceId === device.id).map(_.ecuId).result.flatMap {
        assignedEcus =>
          if (
            assignedEcus.intersect(ecus.map(_.ecuSerial)) == assignedEcus
          ) // assignedEcus is subset of ecus
            DBIO.successful(true)
          else
            DBIO.failed(Errors.AssignmentExistsError(device.id))
      }

    val ensureNotReplacingDeleted =
      Schema.deletedEcus
        .filter(_.deviceId === device.id)
        .filter(_.ecuSerial.inSet(ecus.map(_.ecuSerial)))
        .exists
        .result
        .flatMap {
          case true  => DBIO.failed(Errors.EcusReuseError(device.id, ecus.map(_.ecuSerial)))
          case false => DBIO.successful(())
        }

    val insertEcus = DBIO.sequence(ecus.map(Schema.allEcus.insertOrUpdate))

    val insertDevice = Schema.allProvisionedDevices.insertOrUpdate(device)

    val setActive = ecuRepository.setActiveEcus(ns, device.id, ecus.map(_.ecuSerial).toSet)

    DBIO
      .seq(
        ensureRunningAssignmentsValid,
        ensureNotReplacingDeleted,
        insertEcus,
        insertDevice,
        setActive
      )
      .map(_ => ecus)
  }

  protected[db] def setMetadataOutdatedAction(deviceIds: Set[DeviceId],
                                              outdated: Boolean): DBIO[Unit] = DBIO.seq {
    Schema.allProvisionedDevices
      .filter(_.id.inSet(deviceIds))
      .map(_.generatedMetadataOutdated)
      .update(outdated)
  }

  def setMetadataOutdated(deviceId: DeviceId, outdated: Boolean): Future[Unit] = db.run {
    setMetadataOutdatedAction(Set(deviceId), outdated)
  }

  def metadataIsOutdated(ns: Namespace, deviceId: DeviceId): Future[Boolean] = db.run {
    Schema.allProvisionedDevices
      .filter(_.namespace === ns)
      .filter(_.id === deviceId)
      .map(_.generatedMetadataOutdated)
      .result
      .headOption
      .map(_.exists(_ == true))
  }

}

trait RepoNamespaceRepositorySupport extends DatabaseSupport {
  lazy val repoNamespaceRepo = new RepoNamespaceRepository()
}

protected[db] class RepoNamespaceRepository()(implicit val db: Database, val ec: ExecutionContext) {
  import Schema.repoNamespaces

  def MissingRepoNamespace(ns: Namespace) =
    MissingEntityId[Namespace](ns)(implicitly, Show.fromToString)

  private val AlreadyExists = EntityAlreadyExists[(RepoId, Namespace)]()

  def persist(repoId: RepoId, namespace: Namespace): Future[Unit] = db.run {
    (repoNamespaces += (repoId -> namespace)).map(_ => ()).handleIntegrityErrors(AlreadyExists)
  }

  def findFor(namespace: Namespace): Future[RepoId] = db.run {
    repoNamespaces
      .filter(_.namespace === namespace)
      .map(_.repoId)
      .result
      .headOption
      .failIfNone(MissingRepoNamespace(namespace))
  }

}

object HardwareUpdateRepository {

  implicit val showHardwareTargetSpecId: cats.Show[(Namespace, TargetSpecId)] =
    Show.show[(Namespace, TargetSpecId)] { case (ns, id) =>
      s"($ns, $id)"
    }

  def MissingHardwareUpdate(namespace: Namespace, id: TargetSpecId) =
    MissingEntityId[(Namespace, TargetSpecId)](namespace -> id)

}

trait HardwareUpdateRepositorySupport extends DatabaseSupport {
  lazy val hardwareUpdateRepository = new HardwareUpdateRepository()
}

protected class HardwareUpdateRepository()(implicit val db: Database, val ec: ExecutionContext) {
  import HardwareUpdateRepository.*

  protected[db] def persistAction(hardwareUpdate: HardwareUpdate): DBIO[Unit] =
    (Schema.hardwareUpdates += hardwareUpdate).map(_ => ())

  def findAll(ns: Namespace): Future[Seq[HardwareUpdate]] = db.run {
    Schema.hardwareUpdates.filter(_.namespace === ns).result
  }

  protected[db] def findByAction(ns: Namespace,
                                 id: TargetSpecId): DBIO[Map[HardwareIdentifier, HardwareUpdate]] =
    Schema.hardwareUpdates
      .filter(_.namespace === ns)
      .filter(_.id === id)
      .result
      .failIfEmpty(MissingHardwareUpdate(ns, id))
      .map { hwUpdates =>
        hwUpdates.map(hwUpdate => hwUpdate.hardwareId -> hwUpdate).toMap
      }

  def findUpdateTargets(
    ns: Namespace,
    id: TargetSpecId): Future[Seq[(HardwareUpdate, Option[EcuTarget], EcuTarget)]] =
    db.run {
      val io = Schema.hardwareUpdates
        .filter(_.namespace === ns)
        .filter(_.id === id)
        .join(Schema.ecuTargets)
        .on { case (hwU, toTarget) => hwU.toTarget === toTarget.id }
        .joinLeft(Schema.ecuTargets)
        .on { case ((hw, _), fromTarget) => hw.fromTarget === fromTarget.id }
        .map { case ((hw, toTarget), fromTarget) =>
          (hw, fromTarget, toTarget)
        }

      io.result.failIfEmpty(MissingHardwareUpdate(ns, id))
    }

}

trait EcuTargetsRepositorySupport extends DatabaseSupport {
  lazy val ecuTargetsRepository = new EcuTargetsRepository()
}

protected class EcuTargetsRepository()(implicit val db: Database, val ec: ExecutionContext) {

  protected[db] def persistAction(ecuTarget: EcuTarget): DBIO[EcuTargetId] =
    (Schema.ecuTargets += ecuTarget).map(_ => ecuTarget.id)

  def find(ns: Namespace, id: EcuTargetId): Future[EcuTarget] = db.run {
    Schema.ecuTargets
      .filter(_.namespace === ns)
      .filter(_.id === id)
      .result
      .failIfNotSingle(MissingEntityId[EcuTargetId](id))
  }

  def findAll(ns: Namespace, ids: Seq[EcuTargetId]): Future[Map[EcuTargetId, EcuTarget]] = db.run {
    findAllAction(ns, ids)
  }

  protected[db] def findAllAction(ns: Namespace,
                                  ids: Seq[EcuTargetId]): DBIO[Map[EcuTargetId, EcuTarget]] =
    Schema.ecuTargets
      .filter(_.namespace === ns)
      .filter(_.id.inSet(ids))
      .result
      .map(_.map(e => e.id -> e).toMap)

}

trait AssignmentsRepositorySupport extends DatabaseSupport {
  lazy val assignmentsRepository = new AssignmentsRepository()
}

protected class AssignmentsRepository()(implicit val db: Database, val ec: ExecutionContext) {

  def persistManyForEcuTarget(ecuTargetsRepository: EcuTargetsRepository,
                              deviceRepository: ProvisionedDeviceRepository)(
    ecuTarget: EcuTarget,
    assignments: Seq[Assignment]): Future[Unit] = db.run {
    ecuTargetsRepository
      .persistAction(ecuTarget)
      .andThen((Schema.assignments ++= assignments).map(_ => ()))
      .andThen {
        deviceRepository.setMetadataOutdatedAction(
          assignments.map(_.deviceId).toSet,
          outdated = true
        )
      }
      .transactionally
  }

  protected[db] def persistManyDBIO(deviceRepository: ProvisionedDeviceRepository)(
    assignments: Seq[Assignment]): DBIO[Unit] =
    (Schema.assignments ++= assignments).andThen {
      deviceRepository.setMetadataOutdatedAction(assignments.map(_.deviceId).toSet, outdated = true)
    }

  def persistMany(deviceRepository: ProvisionedDeviceRepository)(
    assignments: Seq[Assignment]): Future[Unit] =
    db.run(persistManyDBIO(deviceRepository)(assignments).transactionally)

  def findBy(deviceId: DeviceId): Future[Seq[Assignment]] = db.run {
    Schema.assignments.filter(_.deviceId === deviceId).result
  }

  def findMany(devices: Set[DeviceId]): Future[Map[DeviceId, Seq[Assignment]]] =
    db.run(Schema.assignments.filter(_.deviceId.inSet(devices)).result).map { assignments =>
      devices.map { device =>
        device -> assignments.filter(_.deviceId == device)
      }.toMap
    }

  protected[db] def findByCorrelationIdsAction(
    ns: Namespace,
    correlationIds: Set[CorrelationId]): DBIO[Seq[Assignment]] =
    Schema.assignments
      .filter(_.namespace === ns)
      .filter(_.correlationId.inSet(correlationIds))
      .result

  def existsForDevices(deviceIds: Set[DeviceId]): Future[Map[DeviceId, Boolean]] = db.run {
    Schema.assignments
      .filter(_.deviceId.inSet(deviceIds))
      .map(a => a.deviceId -> true)
      .result
      .map(existing => deviceIds.map(_ -> false).toMap ++ existing.toMap)
  }

  protected[db] def withAssignmentsAction(
    ns: Namespace,
    ids: Set[(DeviceId, EcuIdentifier)]): DBIO[Set[(DeviceId, EcuIdentifier)]] =
    if (ids.isEmpty) {
      DBIO.successful(Set.empty)
    } else {
      // raw sql is workaround for https://github.com/slick/slick/pull/995
      implicit val getResult = GetResult { r =>
        DeviceId(UUID.fromString(r.nextString())) ->
          r.nextString().refineTry[ValidEcuIdentifier].get
      }

      val elems = ids
        .map { case (d, e) => "('" + d.uuid.toString + "','" + e.value + "')" }
        .mkString("(", ",", ")")

      sql"select device_id, ecu_serial from assignments where (device_id, ecu_serial) in #$elems AND namespace = ${ns.get}"
        .as[(DeviceId, EcuIdentifier)]
        .map(_.toSet)
    }

  def markRegenerated(deviceRepository: ProvisionedDeviceRepository)(
    deviceId: DeviceId): Future[Seq[Assignment]] = db.run {
    val deviceAssignments = Schema.assignments.filter(_.deviceId === deviceId)

    val io = for {
      assignments <- deviceAssignments.forUpdate.result
      _ <- Schema.updates
        .filter(_.correlationId.inSet(assignments.map(_.correlationId).toSet))
        .filter(_.status === (Update.Status.Assigned: Update.Status))
        .map(_.status)
        .update(Update.Status.Seen)
      _ <- deviceAssignments.map(_.inFlight).update(true)
      _ <- deviceRepository.setMetadataOutdatedAction(Set(deviceId), outdated = false)
    } yield assignments

    io.transactionally
  }

  protected[db] def processDeviceCancellationAction(deviceRepository: ProvisionedDeviceRepository)(
    assignmentsQuery: Query[AssignmentsTable, Assignment, Seq],
    allowInFlightCancellation: Boolean = false): DBIO[Map[CorrelationId, List[DeviceId]]] =
    assignmentsQuery.forUpdate.result.flatMap { assignments =>
      if (assignments.exists(_.inFlight) && !allowInFlightCancellation) {
        // safe because of above `exists`
        val deviceIds =
          NonEmptyList.fromListUnsafe(assignments.filter(_.inFlight).map(_.deviceId).toSet.toList)
        DBIO.failed(Errors.AssignmentInFlight(deviceIds))
      } else
        DBIO
          .seq(
            Schema.processedAssignments ++= assignments
              .map(_.toProcessedAssignment(successful = true, canceled = true)),
            assignmentsQuery.delete,
            deviceRepository
              .setMetadataOutdatedAction(assignments.map(_.deviceId).toSet, outdated = true)
          )
          .map(_ => assignments.map(a => a.correlationId -> a.deviceId).toList)
          .map(_.groupMap(_._1)(_._2))
    }

  def processDeviceCancellation(deviceRepository: ProvisionedDeviceRepository,
                                updatesRepository: UpdatesRepository)(
    ns: Namespace,
    deviceId: DeviceId,
    allowInFlightCancellation: Boolean): Future[List[CorrelationId]] = db.run {
    val assignmentsToCancelQuery = Schema.assignments.filter(_.deviceId === deviceId)

    val io = for {
      ids <- processDeviceCancellationAction(deviceRepository)(
        assignmentsToCancelQuery,
        allowInFlightCancellation
      )
      _ <- updatesRepository.ensureNoUpdateFor(ns, ids.keySet).map(_ => ids.keySet)
    } yield ids.keySet.toList

    io.transactionally
  }

  def processCancellation(deviceRepository: ProvisionedDeviceRepository,
                          updatesRepository: UpdatesRepository)(
    ns: Namespace,
    deviceIds: Seq[DeviceId]): Future[Seq[Assignment]] = db.run {
    val assignmentQuery = Schema.assignments
      .filter(_.namespace === ns)
      .filter(_.deviceId.inSet(deviceIds))
      .filterNot(_.inFlight)

    val action = for {
      assignments <- assignmentQuery.forUpdate.result
      _ <- Schema.processedAssignments ++= assignments.map(
        _.toProcessedAssignment(successful = true, canceled = true)
      )
      correlationIds = assignments.map(_.correlationId).toSet
      _ <- updatesRepository.ensureNoUpdateFor(ns, correlationIds)
      _ <- assignmentQuery.delete
      _ <- deviceRepository.setMetadataOutdatedAction(deviceIds.toSet, outdated = true)
    } yield assignments

    action.transactionally
  }

  def findProcessed(ns: Namespace, deviceId: DeviceId): Future[Seq[ProcessedAssignment]] = db.run {
    Schema.processedAssignments.filter(_.namespace === ns).filter(_.deviceId === deviceId).result
  }

  protected[db] def findAllProcessedByCorrelatioIds(
    ns: Namespace,
    deviceId: DeviceId,
    correlationIds: Set[CorrelationId]): DBIO[Seq[ProcessedAssignment]] =
    Schema.processedAssignments
      .filter(_.namespace === ns)
      .filter(_.deviceId === deviceId)
      .filter(_.correlationId.inSet(correlationIds))
      .result

}

trait EcuRepositorySupport extends DatabaseSupport {
  lazy val ecuRepository = new EcuRepository()
}

protected class EcuRepository()(implicit val db: Database, val ec: ExecutionContext) {

  def findBy(deviceId: DeviceId): Future[Seq[Ecu]] = db.run {
    Schema.activeEcus.filter(_.deviceId === deviceId).result
  }

  def findBySerial(ns: Namespace, deviceId: DeviceId, ecuSerial: EcuIdentifier): Future[Ecu] =
    db.run {
      Schema.activeEcus
        .filter(_.deviceId === deviceId)
        .filter(_.namespace === ns)
        .filter(_.ecuSerial === ecuSerial)
        .result
        .failIfNotSingle(MissingEntity[Ecu]())
    }

  def findTargets(ns: Namespace, deviceId: DeviceId): Future[Seq[(Ecu, EcuTarget)]] = db.run {
    Schema.activeEcus
      .filter(_.namespace === ns)
      .filter(_.deviceId === deviceId)
      .join(Schema.ecuTargets)
      .on(_.installedTarget === _.id)
      .result
  }

  def countEcusWithImages(ns: Namespace,
                          targets: Set[TargetFilename]): Future[Map[TargetFilename, Int]] = db.run {
    Schema.activeEcus
      .filter(_.namespace === ns)
      .join(Schema.ecuTargets.filter(_.filename.inSet(targets)).filter(_.namespace === ns))
      .on(_.installedTarget === _.id)
      .map { case (ecu, ecuTarget) => ecu.ecuSerial -> ecuTarget.filename }
      .result
      .map { targetByEcu =>
        targetByEcu
          .groupBy { case (_, filename) => filename }
          .view
          .mapValues(_.size)
          .toMap
      }
  }

  def findAllHardwareIdentifiers(ns: Namespace,
                                 offset: Offset,
                                 limit: Limit): Future[PaginationResult[HardwareIdentifier]] =
    db.run {
      Schema.activeEcus
        .filter(_.namespace === ns)
        .map(_.hardwareId)
        .distinct
        .paginateResult(offset = offset, limit = limit)
    }

  def findFor(deviceId: DeviceId): Future[Map[EcuIdentifier, Ecu]] = db
    .run {
      Schema.activeEcus.filter(_.deviceId === deviceId).result
    }
    .map(_.map(e => e.ecuSerial -> e).toMap)

  // get ecus for the namespace, the bool means it's a primary ecu
  def findAll(ns: Namespace): Future[Seq[(Ecu, Boolean)]] = db.run {
    Schema.activeEcus
      .filter(_.namespace === ns)
      // in theory, if it's an active ecu, it will always have an active device :/
      .join(Schema.activeProvisionedDevices)
      .on(_.deviceId === _.id)
      .map { case (ecu, device) => (ecu, device.primaryEcu === ecu.ecuSerial) }
      .result
  }

  def findEcuWithTargetsAction(
    devices: Set[DeviceId],
    hardwareIds: Set[HardwareIdentifier]): DBIO[Seq[(Ecu, Option[EcuTarget])]] =
    Schema.activeEcus
      .filter(_.deviceId.inSet(devices))
      .filter(_.hardwareId.inSet(hardwareIds))
      .joinLeft(Schema.ecuTargets)
      .on(_.installedTarget === _.id)
      .result

  private[db] def findDevicePrimaryAction(ns: Namespace, deviceId: DeviceId): DBIO[Ecu] =
    Schema.allProvisionedDevices
      .filter(_.namespace === ns)
      .filter(_.id === deviceId)
      .join(Schema.activeEcus)
      .on { case (d, e) =>
        d.id === e.deviceId && d.primaryEcu === e.ecuSerial && d.namespace === e.namespace
      }
      .map(_._2)
      .resultHead(Errors.DeviceMissingPrimaryEcu(deviceId))

  def findDevicePrimary(ns: Namespace, deviceId: DeviceId): Future[Ecu] =
    db.run(findDevicePrimaryAction(ns, deviceId))

  protected[db] def findDevicePrimaryIdsAction(
    ns: Namespace,
    deviceId: Set[DeviceId]): DBIO[Map[DeviceId, EcuIdentifier]] =
    Schema.allProvisionedDevices
      .filter(_.namespace === ns)
      .filter(_.id.inSet(deviceId))
      .join(Schema.activeEcus)
      .on { case (d, e) =>
        d.id === e.deviceId && d.primaryEcu === e.ecuSerial && d.namespace === e.namespace
      }
      .map { case (_, ecu) => ecu.deviceId -> ecu.ecuSerial }
      .result
      .map(_.toMap)

  protected[db] def setActiveEcus(ns: Namespace,
                                  deviceId: DeviceId,
                                  ecus: Set[EcuIdentifier]): DBIO[Unit] = {
    val ecuQuery = Schema.allEcus.filter(_.namespace === ns).filter(_.deviceId === deviceId)

    val activeIO = ecuQuery
      .filter(_.ecuSerial.inSet(ecus))
      .map(_.deleted)
      .update(false)

    val deleteIO = ecuQuery
      .filterNot(_.ecuSerial.inSet(ecus))
      .map(_.deleted)
      .update(true)

    DBIO.seq(activeIO, deleteIO)
  }

  def deviceEcuInstallInfo(
    ns: Namespace,
    devices: Set[DeviceId]): Future[Seq[(DeviceId, Ecu, Boolean, Option[EcuTarget])]] = {
    val io = Schema.activeEcus
      .filter(_.namespace === ns)
      .filter(_.deviceId.inSet(devices))
      .join(Schema.activeProvisionedDevices)
      .on((ecus, devices) => ecus.deviceId === devices.id)
      .joinLeft(Schema.ecuTargets)
      .on { case ((ecu, _), ecuTargets) => ecu.installedTarget === ecuTargets.id }
      .map { case ((ecu, device), ecuTarget) =>
        (ecu.deviceId, ecu, device.primaryEcu === ecu.ecuSerial, ecuTarget)
      }
      .result

    db.run(io)
  }

}

trait AdminRolesRepositorySupport extends DatabaseSupport {
  lazy val adminRolesRepository = new AdminRolesRepository()
}

object AdminRolesRepository {

  sealed trait FindLatestResult {
    def value: DbAdminRole
  }

  case class Deleted(value: DbAdminRole) extends FindLatestResult
  case class NotDeleted(value: DbAdminRole) extends FindLatestResult
}

protected[db] class AdminRolesRepository()(implicit val db: Database, val ec: ExecutionContext) {

  import SlickMapping.adminRoleNameMapper

  private val AlreadyExists = EntityAlreadyExists[(Namespace, DbAdminRole)]()

  def findLatestOpt(repoId: RepoId,
                    role: RoleType,
                    name: AdminRoleName): Future[Option[FindLatestResult]] = db.run {
    adminRoles
      .filter(_.repoId === repoId)
      .filter(_.name === name)
      .filter(_.role === role)
      .sortBy(_.version.reverse)
      .take(1)
      .map { row =>
        (row, row.deleted)
      }
      .result
      .headOption
      .map {
        case Some((role, deleted)) if deleted =>
          Option(Deleted(role))
        case Some((role, deleted)) if !deleted =>
          Option(NotDeleted(role))
        case _ =>
          None
      }
  }

  def findAll(repoId: RepoId, role: RoleType): Future[Seq[DbAdminRole]] = db.run {
    notDeletedAdminRoles
      .filter(_.repoId === repoId)
      .filter(_.role === role)
      .result
  }

  def findByVersion(repoId: RepoId,
                    role: RoleType,
                    name: AdminRoleName,
                    version: Int): Future[DbAdminRole] = db.run {
    notDeletedAdminRoles
      .filter(_.repoId === repoId)
      .filter(_.role === role)
      .filter(_.name === name)
      .filter(_.version === version)
      .result
      .failIfNotSingle(Errors.MissingAdminRole(repoId, name))
  }

  def findLatest(repoId: RepoId, role: RoleType, name: AdminRoleName): Future[DbAdminRole] =
    findLatestOpt(repoId, role, name).flatMap {
      case Some(NotDeleted(role)) => FastFuture.successful(role)
      case _                      => FastFuture.failed(Errors.MissingAdminRole(repoId, name))
    }

  def findLatestExpireDate(repoId: RepoId): Future[Option[Instant]] = {
    implicit val getInstant = GetResult[Option[Instant]] { pr =>
      Option(pr.rs.getTimestamp(1)).map(_.toInstant)
    }

    val sql =
      sql"""
            select max(ar0.expires_at) from #${Schema.adminRoles.baseTableRow.tableName} ar0 join
            (select max(version) version, repo_id, name from #${Schema.adminRoles.baseTableRow.tableName} group by repo_id, name) ar
            USING (name, version, repo_id)
            where ar0.repo_id = ${repoId.show}
            AND deleted = 0
      """.as[Option[Instant]].headOption

    db.run(sql).map(_.flatten)
  }

  def persistAction(signedRole: DbAdminRole): DBIO[Unit] =
    adminRoles
      .filter(_.repoId === signedRole.repoId)
      .filter(_.name === signedRole.name)
      .filter(_.role === signedRole.role)
      .sortBy(_.version.reverse)
      .forUpdate
      .result
      .headOption
      .flatMap(ensureVersionBumpIsValid(signedRole))
      .flatMap(_ => adminRoles += signedRole)
      .map(_ => ())

  private def ensureVersionBumpIsValid(signedRole: DbAdminRole)(
    oldSignedRole: Option[DbAdminRole]): DBIO[Unit] =
    oldSignedRole match {
      case Some(sr) if sr.version != signedRole.version - 1 =>
        DBIO.failed(Errors.InvalidVersionBumpError(sr.version, signedRole.version, signedRole.role))
      case _ => DBIO.successful(())
    }

  def persistAll(values: DbAdminRole*): Future[Unit] = db.run {
    DBIO
      .sequence(values.map(persistAction))
      .handleIntegrityErrors(AlreadyExists)
      .map(_ => ())
      .transactionally
  }

  def setDeleted(repoId: RepoId,
                 roleType: RoleType,
                 offlineUpdatesName: AdminRoleName,
                 newSnapshotsRole: DbAdminRole): Future[Unit] = db.run {
    DBIO
      .seq(
        notDeletedAdminRoles
          .filter(_.repoId === repoId)
          .filter(_.name === offlineUpdatesName)
          .filter(_.role === roleType)
          .map(_.deleted)
          .update(true)
          .map(_ => ()),
        persistAction(newSnapshotsRole)
      )
      .transactionally
  }

}

trait DbDeviceRoleRepositorySupport extends DatabaseSupport {
  lazy val dbDeviceRoleRepository = new DbDeviceRoleRepository()
}

protected[db] class DbDeviceRoleRepository()(implicit val db: Database, val ec: ExecutionContext) {
  import Schema.deviceRoles

  def persist(signedRole: DbDeviceRole, forceVersion: Boolean = false): Future[DbDeviceRole] =
    db.run(persistAction(signedRole, forceVersion).transactionally)

  protected[db] def persistAction(signedRole: DbDeviceRole,
                                  forceVersion: Boolean): DBIO[DbDeviceRole] =
    deviceRoles
      .filter(_.device === signedRole.device)
      .filter(_.role === signedRole.role)
      .sortBy(_.version.reverse)
      .forUpdate
      .result
      .headOption
      .flatMap { old =>
        if (!forceVersion)
          ensureVersionBumpIsValid(signedRole)(old)
        else
          DBIO.successful(())
      }
      .flatMap(_ => deviceRoles += signedRole)
      .map(_ => signedRole)

  def persistAll(signedRoles: List[DbDeviceRole]): Future[Seq[DbDeviceRole]] = db.run {
    DBIO.sequence(signedRoles.map(sr => persistAction(sr, forceVersion = false))).transactionally
  }

  def findLatest[T](deviceId: DeviceId)(implicit ev: TufRole[T]): Future[DbDeviceRole] =
    db.run {
      deviceRoles
        .filter(_.device === deviceId)
        .filter(_.role === ev.roleType)
        .sortBy(_.version.reverse)
        .resultHead(Errors.SignedRoleNotFound[T](deviceId))
    }

  def findLatestOpt[T](deviceId: DeviceId)(implicit ev: TufRole[T]): Future[Option[DbDeviceRole]] =
    db.run {
      deviceRoles
        .filter(_.device === deviceId)
        .filter(_.role === ev.roleType)
        .sortBy(_.version.reverse)
        .result
        .headOption
    }

  private def ensureVersionBumpIsValid(signedRole: DbDeviceRole)(
    oldSignedRole: Option[DbDeviceRole]): DBIO[Unit] =
    oldSignedRole match {
      case Some(sr) if signedRole.role != RoleType.ROOT && sr.version != signedRole.version - 1 =>
        DBIO.failed(Errors.InvalidVersionBumpError(sr.version, signedRole.version, signedRole.role))
      case _ => DBIO.successful(())
    }

}

trait AutoUpdateDefinitionRepositorySupport {

  def autoUpdateDefinitionRepository(implicit db: Database, ec: ExecutionContext) =
    new AutoUpdateDefinitionRepository()

}

protected class AutoUpdateDefinitionRepository()(implicit db: Database, ec: ExecutionContext) {
  private val nonDeleted = Schema.autoUpdates.filter(_.deleted === false)

  def persist(ns: Namespace,
              deviceId: DeviceId,
              ecuId: EcuIdentifier,
              targetName: TargetName): Future[AutoUpdateDefinitionId] = db.run {
    val id = AutoUpdateDefinitionId.generate()
    (Schema.autoUpdates += AutoUpdateDefinition(id, ns, deviceId, ecuId, targetName)).map(_ => id)
  }

  def remove(ns: Namespace,
             deviceId: DeviceId,
             ecuId: EcuIdentifier,
             targetName: TargetName): Future[Unit] = db.run {
    nonDeleted
      .filter(_.deviceId === deviceId)
      .filter(_.ecuId === ecuId)
      .filter(_.namespace === ns)
      .filter(_.targetName === targetName)
      .map(_.deleted)
      .update(true)
      .handleSingleUpdateError(MissingEntity[AutoUpdateDefinition]())
  }

  def findByName(namespace: Namespace, targetName: TargetName): Future[Seq[AutoUpdateDefinition]] =
    db.run {
      nonDeleted.filter(_.namespace === namespace).filter(_.targetName === targetName).result
    }

  def findOnDevice(ns: Namespace,
                   device: DeviceId,
                   ecuId: EcuIdentifier): Future[Seq[AutoUpdateDefinition]] = db.run {
    nonDeleted
      .filter(_.namespace === ns)
      .filter(_.deviceId === device)
      .filter(_.ecuId === ecuId)
      .result
  }

}

trait DeviceManifestRepositorySupport {

  def deviceManifestRepository(implicit db: Database, ec: ExecutionContext) =
    new DeviceManifestRepository()

}

protected class DeviceManifestRepository()(implicit db: Database, ec: ExecutionContext) {

  def findLatest(deviceId: DeviceId): Future[Option[(Json, Instant)]] = db.run {
    Schema.deviceManifests
      .filter(_.deviceId === deviceId)
      .sortBy(_.receivedAt.desc)
      .take(1)
      .map(r => r.manifest -> r.receivedAt)
      .result
      .headOption
  }

  def findAll(deviceId: DeviceId,
              offset: Offset = 0L.toOffset,
              limit: Limit = 50L.toLimit): Future[PaginationResult[(Json, Instant)]] = db.run {
    Schema.deviceManifests
      .filter(_.deviceId === deviceId)
      .sortBy(_.receivedAt.desc)
      .map(r => r.manifest -> r.receivedAt)
      .paginateResult(offset, limit)
  }

  // Calculate checksum after removing unstable fields like signatures
  private def calculateJsonChecksum(json: Json): MurmurHash3Checksum = {
    def removeUnstableFieldsRecursively(json: Json): Json = json.arrayOrObject(
      json,
      jsonArray = arr => Json.fromValues(arr.map(removeUnstableFieldsRecursively)),
      jsonObject = obj =>
        Json.fromJsonObject(
          obj
            .remove("signatures")
            .remove("report_counter")
            .mapValues(removeUnstableFieldsRecursively)
        )
    )

    f"${MurmurHash3.bytesHash(removeUnstableFieldsRecursively(json).canonical.getBytes)}%08x"
      .refineTry[ValidMurmurHash3Checksum]
      .get
  }

  def createOrUpdate(manifests: Seq[(DeviceId, Json, Instant)]): Future[Unit] = db.run {
    val items = manifests
      .map { case (deviceId, json, receivedAt) =>
        val checksum = calculateJsonChecksum(json)
        (deviceId, json, checksum, receivedAt)
      }
      .sortBy(_._4)

    // Using string concat to set a list but currently mariadb doesn't have native arrays so this is the best we can do
    val deviceIdStr = items.map(_._1).map(d => s"'${d.show}'").toSet.mkString(",")

    val deleteSql =
      sql"""
         DELETE dm1
         FROM device_manifests AS dm1
         JOIN (
           SELECT device_id, checksum
           FROM (
             SELECT device_id, checksum, ROW_NUMBER() OVER (PARTITION BY device_id ORDER BY received_at DESC) as row_num
             FROM device_manifests
             WHERE device_id IN (#$deviceIdStr)
           ) ranked
          WHERE ranked.row_num > 200
        ) AS dm2 USING (device_id, checksum)
        """.asUpdate

    Schema.deviceManifests
      .insertOrUpdateAll(items)
      .andThen(deleteSql)
      .transactionally
      .map(_ => ())
  }

}

protected class UpdatesRepository()(implicit db: Database, ec: ExecutionContext) {

  import PaginationResult.*
  import SlickMapping.*

  protected[db] def findAction(ns: Namespace,
                               deviceId: DeviceId,
                               offset: Offset = 0L.toOffset,
                               limit: Limit = 10L.toLimit): DBIO[PaginationResult[Update]] =
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.deviceId === deviceId)
      .paginateAndSortResult(_.createdAt.desc, offset, limit)

  def persist(update: Update): Future[Unit] = db.run {
    persistMany(Seq(update))
  }

  protected[db] def persistMany(updates: Seq[Update]): DBIO[Unit] =
    (Schema.updates ++= updates).map(_ => ())

  protected[db] def cancelUpdateAction(
    ns: Namespace,
    updateId: UpdateId,
    deviceId: Option[DeviceId],
    allowInFlightCancellation: Boolean): DBIO[NonEmptyList[(CorrelationId, DeviceId)]] =

    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.id === updateId)
      .maybeFilter(_.deviceId === deviceId)
      .forUpdate
      .result
      .flatMap {
        case updates if updates.isEmpty => DBIO.failed(MissingEntity[Update]())
        case updates
            if updates.forall(u =>
              u.status == Update.Status.Assigned || u.status == Update.Status.Scheduled ||
                (u.status == Update.Status.Seen && allowInFlightCancellation)
            ) =>
          DBIO.successful(updates)
        case _ => DBIO.failed(Errors.UpdateCannotBeCancelled(updateId))
      }
      .flatMap { updates =>
        Schema.updates
          .filter(_.namespace === ns)
          .filter(_.id === updateId)
          .maybeFilter(_.deviceId === deviceId)
          .map(r => (r.status, r.completedAt))
          .update((Update.Status.Cancelled, Some(Instant.now)))
          .map(_ => updates.map(u => u.correlationId -> u.deviceId))
      }
      .map { updates =>
        NonEmptyList.fromListUnsafe(updates.toList)
      } // safe because we check if `updates` is empty above

  protected[db] def setStatusAction[T: Encoder](ns: Namespace,
                                                id: UpdateId,
                                                newStatus: Update.Status,
                                                info: Option[T] = None): DBIO[Unit] =
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.id === id)
      .map(r => (r.status, r.statusInfo))
      .update(newStatus -> info.map(_.asJson))
      .handleSingleUpdateError(MissingEntity[Update]())
      .map(_ => ())

  protected[db] def filterActiveUpdateExistsAction(ns: Namespace,
                                                   devices: Set[DeviceId]): DBIO[Set[DeviceId]] =
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.deviceId.inSet(devices))
      .filterNot(_.status.inSet(Set(Update.Status.Completed, Update.Status.Cancelled)))
      .map(_.deviceId)
      .result
      .map(_.toSet)

  def findFor(ns: Namespace, device: DeviceId): Future[Seq[Update]] = db.run {
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.deviceId === device)
      .result
  }

  def ensureNoUpdateFor(ns: Namespace, correlationIds: Set[CorrelationId]): DBIO[Unit] =
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.correlationId.inSet(correlationIds))
      .map(_.correlationId)
      .result
      .flatMap {
        case id +: ids =>
          DBIO.failed(Errors.AssignmentBelongsToUpdate(NonEmptyList(id, ids.toList)))
        case _ => DBIO.successful(())
      }

}

trait UpdatesRepositorySupport {

  def updatesRepository(implicit db: Database, ec: ExecutionContext) =
    new UpdatesRepository()

}
