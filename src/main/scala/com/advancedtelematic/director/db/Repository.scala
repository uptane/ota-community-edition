package com.advancedtelematic.director.db

import java.time.Instant
import java.util.UUID
import cats.Show
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
  ProcessedAssignment
}
import com.advancedtelematic.director.db.ProvisionedDeviceRepository.DeviceCreateResult
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.data.{EcuIdentifier, PaginationResult}
import com.advancedtelematic.libats.http.Errors.{
  EntityAlreadyExists,
  MissingEntity,
  MissingEntityId
}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import cats.syntax.either.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
import slick.jdbc.MySQLProfile.api.*
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.data.DataType.{
  AdminRoleName,
  ScheduledUpdate,
  ScheduledUpdateId,
  StatusInfo
}
import com.advancedtelematic.director.db.AdminRolesRepository.{
  Deleted,
  FindLatestResult,
  NotDeleted
}
import com.advancedtelematic.director.db.Schema.{adminRoles, notDeletedAdminRoles}
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  EcuAndHardwareId,
  EcuReplaced,
  EcuReplacement
}
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.jsonMapper
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  RepoId,
  RoleType,
  TargetFilename,
  TargetName
}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.data.TufSlickMappings.*
import io.circe.{Encoder, Json}
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import io.circe.syntax.EncoderOps
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}
import cats.syntax.show.*

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

  private def existsIO(deviceId: DeviceId): DBIO[Boolean] =
    Schema.allProvisionedDevices.filter(_.id === deviceId).exists.result

  def exists(deviceId: DeviceId): Future[Boolean] =
    db.run(existsIO(deviceId))

  def findAllDeviceIds(ns: Namespace,
                       offset: Long,
                       limit: Long): Future[PaginationResult[DeviceId]] = db.run {
    Schema.activeProvisionedDevices
      .filter(_.namespace === ns)
      .map(d => (d.id, d.createdAt))
      .paginateAndSortResult(_._2, offset = offset, limit = limit)
      .map(_.map(_._1))
  }

  def findDevices(ns: Namespace,
                  hardwareIdentifier: HardwareIdentifier,
                  offset: Long,
                  limit: Long): Future[PaginationResult[(Instant, Device)]] = db.run {
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

  implicit val showHardwareUpdateId: cats.Show[
    (
      com.advancedtelematic.libats.data.DataType.Namespace,
      com.advancedtelematic.libats.messaging_datatype.DataType.UpdateId
    )
  ] = Show.show[(Namespace, UpdateId)] { case (ns, id) =>
    s"($ns, $id)"
  }

  def MissingHardwareUpdate(namespace: Namespace, id: UpdateId) =
    MissingEntityId[(Namespace, UpdateId)](namespace -> id)

}

trait HardwareUpdateRepositorySupport extends DatabaseSupport {
  lazy val hardwareUpdateRepository = new HardwareUpdateRepository()
}

protected class HardwareUpdateRepository()(implicit val db: Database, val ec: ExecutionContext) {
  import HardwareUpdateRepository._

  protected[db] def persistAction(hardwareUpdate: HardwareUpdate): DBIO[Unit] =
    (Schema.hardwareUpdates += hardwareUpdate).map(_ => ())

  def findBy(ns: Namespace, id: UpdateId): Future[Map[HardwareIdentifier, HardwareUpdate]] =
    db.run {
      Schema.hardwareUpdates
        .filter(_.namespace === ns)
        .filter(_.id === id)
        .result
        .failIfEmpty(MissingHardwareUpdate(ns, id))
        .map { hwUpdates =>
          hwUpdates.map(hwUpdate => hwUpdate.hardwareId -> hwUpdate).toMap
        }
    }

  def findUpdateTargets(ns: Namespace,
                        id: UpdateId): Future[Seq[(HardwareUpdate, Option[EcuTarget], EcuTarget)]] =
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
    Schema.ecuTargets
      .filter(_.namespace === ns)
      .filter(_.id.inSet(ids))
      .result
      .map(_.map(e => e.id -> e).toMap)
  }

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

  def existsForDevices(deviceIds: Set[DeviceId]): Future[Map[DeviceId, Boolean]] = db.run {
    Schema.assignments
      .filter(_.deviceId.inSet(deviceIds))
      .map(a => a.deviceId -> true)
      .result
      .map(existing => deviceIds.map(_ -> false).toMap ++ existing.toMap)
  }

  def withAssignments(ids: Set[(DeviceId, EcuIdentifier)]): Future[Set[(DeviceId, EcuIdentifier)]] =
    if (ids.isEmpty) {
      FastFuture.successful(Set.empty)
    } else {
      // raw sql is workaround for https://github.com/slick/slick/pull/995
      implicit val getResult = GetResult { r =>
        DeviceId(UUID.fromString(r.nextString())) -> EcuIdentifier
          .from(r.nextString())
          .valueOr(throw _)
      }

      val elems = ids
        .map { case (d, e) => "('" + d.uuid.toString + "','" + e.value + "')" }
        .mkString("(", ",", ")")

      db.run {
        sql"select device_id, ecu_serial from assignments where (device_id, ecu_serial) in #$elems"
          .as[(DeviceId, EcuIdentifier)]
          .map(_.toSet)
      }
    }

  def markRegenerated(deviceRepository: ProvisionedDeviceRepository)(
    deviceId: DeviceId): Future[Seq[Assignment]] = db.run {
    val deviceAssignments = Schema.assignments.filter(_.deviceId === deviceId)

    val io = for {
      assignments <- deviceAssignments.forUpdate.result
      _ <- deviceAssignments.map(_.inFlight).update(true)
      _ <- deviceRepository.setMetadataOutdatedAction(Set(deviceId), outdated = false)
    } yield assignments

    io.transactionally
  }

  def processDeviceCancellation(deviceRepository: ProvisionedDeviceRepository)(
    ns: Namespace,
    deviceId: DeviceId,
    allowInFlightCancellation: Boolean): Future[List[CorrelationId]] = db.run {
    val assignmentQuery =
      Schema.assignments.filter(_.namespace === ns).filter(_.deviceId === deviceId)

    val action = assignmentQuery.forUpdate.result.flatMap { assignments =>
      if (assignments.isEmpty)
        DBIO.failed(MissingEntity[Assignment]())
      else if (assignments.exists(_.inFlight) && !allowInFlightCancellation)
        DBIO.failed(Errors.AssignmentInFlight(deviceId))
      else
        DBIO
          .seq(
            Schema.processedAssignments ++= assignments
              .map(_.toProcessedAssignment(successful = true, canceled = true)),
            assignmentQuery.delete,
            deviceRepository.setMetadataOutdatedAction(Set(deviceId), outdated = true)
          )
          .map(_ => assignments.map(_.correlationId).toList)
    }

    action.transactionally
  }

  def processCancellation(deviceRepository: ProvisionedDeviceRepository)(
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
      _ <- assignmentQuery.delete
      _ <- deviceRepository.setMetadataOutdatedAction(deviceIds.toSet, outdated = true)
    } yield assignments

    action.transactionally
  }

  def findProcessed(ns: Namespace, deviceId: DeviceId): Future[Seq[ProcessedAssignment]] = db.run {
    Schema.processedAssignments.filter(_.namespace === ns).filter(_.deviceId === deviceId).result
  }

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
                                 offset: Long,
                                 limit: Long): Future[PaginationResult[HardwareIdentifier]] =
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

  def findEcuWithTargets(
    devices: Set[DeviceId],
    hardwareIds: Set[HardwareIdentifier]): Future[Seq[(Ecu, Option[EcuTarget])]] = db.run {
    Schema.activeEcus
      .filter(_.deviceId.inSet(devices))
      .filter(_.hardwareId.inSet(hardwareIds))
      .joinLeft(Schema.ecuTargets)
      .on(_.installedTarget === _.id)
      .result
  }

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

  def findDevicePrimaryIds(ns: Namespace,
                           deviceId: Set[DeviceId]): Future[Map[DeviceId, EcuIdentifier]] = db.run {
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
  }

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
            where ar0.repo_id = '${repoId.show}
            AND deleted = 0'
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

  def find(deviceId: DeviceId): Future[Option[(Json, Instant)]] = db.run {
    Schema.deviceManifests
      .filter(_.deviceId === deviceId)
      .map(r => r.manifest -> r.receivedAt)
      .result
      .headOption
  }

  def findAll(deviceId: DeviceId): Future[Seq[(Json, Instant)]] = db.run {
    Schema.deviceManifests
      .filter(_.deviceId === deviceId)
      .map(r => r.manifest -> r.receivedAt)
      .result
  }

  def createOrUpdate(device: DeviceId, jsonManifest: Json, receivedAt: Instant): Future[Unit] =
    db.run {
      val checksum = Sha256Digest.digest(jsonManifest.canonical.getBytes).hash
      Schema.deviceManifests
        .insertOrUpdate((device, jsonManifest, checksum, receivedAt))
        .map(_ => ())
    }

}

trait ScheduledUpdatesRepositorySupport {

  def scheduledUpdatesRepository(implicit db: Database, ec: ExecutionContext) =
    new ScheduledUpdatesRepository()

}

protected class ScheduledUpdatesRepository()(implicit db: Database, ec: ExecutionContext) {
  import SlickMapping.*

  def persist(scheduledUpdate: ScheduledUpdate): Future[ScheduledUpdateId] = db.run {
    persistAction(scheduledUpdate)
  }

  protected[db] def persistAction(scheduledUpdate: ScheduledUpdate): DBIO[ScheduledUpdateId] =
    (Schema.scheduledUpdates += scheduledUpdate).map(_ => scheduledUpdate.id)

  def findFor(ns: Namespace, device: DeviceId): Future[PaginationResult[ScheduledUpdate]] = db.run {
    Schema.scheduledUpdates
      .filter(_.namespace === ns)
      .filter(_.deviceId === device)
      .result
      .map(seq => PaginationResult(seq, seq.length, 0, seq.length))
  }

  def setStatus[T <: StatusInfo: Encoder](ns: Namespace,
                                          id: ScheduledUpdateId,
                                          newStatus: ScheduledUpdate.Status,
                                          info: Option[T] = None): Future[Unit] = db.run {
    setStatusAction(ns, id, newStatus, info)
  }

  def filterActiveUpdateExists(ns: Namespace, devices: Set[DeviceId]): Future[Set[DeviceId]] =
    db.run {
      Schema.scheduledUpdates
        .filter(_.namespace === ns)
        .filter(_.deviceId.inSet(devices))
        .filterNot(
          _.status.inSet(Set(ScheduledUpdate.Status.Completed, ScheduledUpdate.Status.Cancelled))
        )
        .map(_.deviceId)
        .result
        .map(_.toSet)
    }

  protected[db] def scheduledUpdateExistsFor(ns: Namespace,
                                             deviceId: DeviceId): DBIO[Option[ScheduledUpdateId]] =
    Schema.scheduledUpdates
      .filter(_.namespace === ns)
      .filter(_.deviceId === deviceId)
      .filterNot(
        _.status.inSet(Set(ScheduledUpdate.Status.Completed, ScheduledUpdate.Status.Cancelled))
      )
      .map(_.id)
      .take(1)
      .result
      .headOption

  protected[db] def setStatusAction[T <: StatusInfo: Encoder](ns: Namespace,
                                                              id: ScheduledUpdateId,
                                                              newStatus: ScheduledUpdate.Status,
                                                              info: Option[T] = None): DBIO[Unit] =
    Schema.scheduledUpdates
      .filter(_.namespace === ns)
      .filter(_.id === id)
      .map(r => (r.status, r.statusInfo))
      .update(newStatus -> info.map(_.asJson))
      .handleSingleUpdateError(MissingEntity[ScheduledUpdate]())
      .map(_ => ())

}
