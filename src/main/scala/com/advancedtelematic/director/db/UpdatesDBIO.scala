package com.advancedtelematic.director.db

import cats.Traverse
import cats.data.NonEmptyList
import cats.syntax.functor.*
import cats.syntax.foldable.*
import com.advancedtelematic.libats.data.PaginationResult.*
import cats.implicits.catsSyntaxOptionId
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.deviceUpdateCompletedCodec
import com.advancedtelematic.libats.codecs.CirceCodecs.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.director.data.AdminDataType.TargetUpdateSpec
import com.advancedtelematic.director.data.DataType.{TargetSpecId, Update, UpdateId}
import com.advancedtelematic.director.data.DbDataType.{Assignment, Ecu, EcuTarget, EcuTargetId}
import com.advancedtelematic.director.db.deviceregistry.{EventJournal, InstallationReportRepository}
import com.advancedtelematic.director.http.DeviceAssignments.AssignmentCreateResult
import com.advancedtelematic.director.http.{
  Errors,
  UpdateDetailResponse,
  UpdateEventResponse,
  UpdateReportedResult,
  UpdateResponse,
  UpdateResultResponse
}
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, UpdateCorrelationId}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.http.Errors.MissingEntity
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceUpdateCompleted
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.TransactionIsolation

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class UpdatesDBIO()(implicit val db: Database, val ec: ExecutionContext)
    extends AssignmentsRepositorySupport
    with UpdatesRepositorySupport
    with ProvisionedDeviceRepositorySupport {

  private val targetUpdateSpecs = new TargetUpdateSpecs()
  private val affectedEcusDBIO = new AffectedEcusDBIO()
  private val eventJournal = new EventJournal()

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import com.advancedtelematic.libats.slick.db.SlickUrnMapper.*

  def cancel(ns: Namespace,
             updateId: UpdateId,
             deviceId: DeviceId,
             allowInFlightCancellation: Boolean): Future[CorrelationId] = {
    val io = for {
      ids <- updatesRepository.cancelUpdateAction(
        ns,
        updateId,
        Option(deviceId),
        allowInFlightCancellation
      )
      assignmentsToCancelQuery = Schema.assignments
        .filter(_.deviceId === deviceId)
        .filter(_.correlationId.inSet(ids.map(_._1).toList.toSet))
      _ <- assignmentsRepository
        .processDeviceCancellationAction(provisionedDeviceRepository)(
          assignmentsToCancelQuery,
          allowInFlightCancellation
        )
    } yield ids.head._1

    db.run(io.withTransactionIsolation(TransactionIsolation.Serializable).transactionally)
  }

  def createFor(ns: Namespace,
                deviceId: DeviceId,
                targetsSpec: TargetUpdateSpec,
                scheduledFor: Option[Instant]): Future[UpdateId] = {
    val io = for {
      _ <- provisionedDeviceRepository.ensureExistsIO(deviceId)
      targetSpecId <- targetUpdateSpecs.createAction(ns, targetsSpec)
      affectedResult <- affectedEcusDBIO.findAffectedEcusAction(ns, Seq(deviceId), targetSpecId)
      _ <- affectedResult.notAffected.get(deviceId) match {
        case Some(errorMap) =>
          DBIO.failed(Errors.DeviceCannotBeUpdated(deviceId, errorMap))
        case None =>
          DBIO.successful(())
      }
      status =
        if (scheduledFor.isDefined)
          Update.Status.Scheduled
        else
          Update.Status.Assigned
      updateId <- updatesDBIO(
        ns,
        targetSpecId,
        affectedResult.affected.map(_._1.deviceId).toSet,
        status,
        scheduledFor
      )
      _ <-
        if (status == Update.Status.Assigned)
          assignmentsDBIO(ns, updateId.toCorrelationId, affectedResult.affected)
        else
          DBIO.successful(())
    } yield updateId

    // we could use FOR UPDATE on the devices table to lock the rows
    // (like an advisory lock) instead of using SERIALIZABLE if performance becomes an issue
    db.run(io.withTransactionIsolation(TransactionIsolation.Serializable).transactionally)
  }

  def createMany(ns: Namespace,
                 targetsSpec: TargetUpdateSpec,
                 devices: Seq[DeviceId]): Future[(UpdateId, AssignmentCreateResult)] = {
    val io = for {
      targetSpecId <- targetUpdateSpecs.createAction(ns, targetsSpec)
      affectedResult <- affectedEcusDBIO.findAffectedEcusAction(ns, devices, targetSpecId)
      _ = if (affectedResult.affected.isEmpty) {
        log.warn(s"no devices affected by this assignment")
      }
      updateId <- updatesDBIO(
        ns,
        targetSpecId,
        affectedResult.affected.map(_._1.deviceId).toSet,
        Update.Status.Assigned,
        None
      )
      devices <- assignmentsDBIO(ns, updateId.toCorrelationId, affectedResult.affected)
    } yield (updateId, AssignmentCreateResult(devices, affectedResult.notAffectedSerializable))

    db.run(io.withTransactionIsolation(TransactionIsolation.Serializable).transactionally)
  }

  def cancelAll(ns: Namespace,
                updateId: UpdateId): Future[NonEmptyList[(CorrelationId, DeviceId)]] = {
    val io = for {
      updatesCorrelationIds <- updatesRepository.cancelUpdateAction(
        ns,
        updateId,
        deviceId = None,
        allowInFlightCancellation = false
      )
      query = Schema.assignments
        .filter(_.deviceId.inSet(updatesCorrelationIds.map(_._2).toList.toSet))
        .filter(_.correlationId.inSet(updatesCorrelationIds.map(_._1).toList.toSet))
      _ <- assignmentsRepository
        .processDeviceCancellationAction(provisionedDeviceRepository)(
          query,
          allowInFlightCancellation = false
        )
    } yield updatesCorrelationIds

    db.run(io.withTransactionIsolation(TransactionIsolation.Serializable).transactionally)
  }

  private def updatesDBIO(ns: Namespace,
                          targetSpecId: TargetSpecId,
                          devices: Set[DeviceId],
                          status: Update.Status,
                          scheduledFor: Option[Instant]): DBIO[UpdateId] = {

    val id = UpdateId.generate()

    val updates = devices.map { deviceId =>
      Update(ns, id, deviceId, id.toCorrelationId, targetSpecId, Instant.now, scheduledFor, status)
    }

    updatesRepository.persistMany(updates.toSeq).map(_ => id)
  }

  private def assignmentsDBIO(ns: Namespace,
                              correlationId: UpdateCorrelationId,
                              ecus: Seq[(Ecu, EcuTargetId)]): DBIO[Seq[DeviceId]] = {
    val assignments = ecus.map { case (ecu, toTargetId) =>
      Assignment(
        ns,
        ecu.deviceId,
        ecu.ecuSerial,
        toTargetId,
        correlationId,
        inFlight = false,
        createdAt = Instant.now
      )
    }

    assignmentsRepository.persistManyDBIO(provisionedDeviceRepository)(assignments).map { _ =>
      assignments.map(_.deviceId).distinct
    }
  }

  def find(ns: Namespace, updateId: UpdateId, deviceId: DeviceId): Future[UpdateDetailResponse] = {
    val io = for {
      (update, targets) <- findSingleUpdateEcuTargets(ns, deviceId, updateId)
      _ <- assignmentsRepository.findByCorrelationIdsAction(ns, Set(update.correlationId))
      processedAssignments <- assignmentsRepository.findAllProcessedByCorrelatioIds(
        ns,
        deviceId,
        Set(update.correlationId)
      )
      ecuReports <- InstallationReportRepository.fetchEcuInstallationReport(
        deviceId,
        update.correlationId
      )
      deviceInstallationReport <- InstallationReportRepository
        .fetchDeviceInstallationResultByCorrelationId(deviceId, update.correlationId)
    } yield UpdateDetailResponse(
      update.id,
      update.status,
      update.createdAt,
      scheduledFor = update.scheduledFor,
      packages = targets.view.mapValues(_.filename).toMap,
      completedAt = update.completedAt,
      deviceResult = deviceInstallationReport.map { r =>
        UpdateReportedResult(r.resultCode, r.success, r.description)
      },
      ecuResults = targets.flatMap { case (hwId, ecuTarget) =>
        val processedAssignment = processedAssignments.find(a =>
          a.correlationId == update.correlationId && a.ecuTargetId == ecuTarget.id
        )

        // `.description` is only in ecuReport for newer updates, so we need to fetch it from
        // deviceInstallationReport.installationReport.ecuReports[ecuId].result.description
        // until we migrate them to EcuInstallationReport
        val ecuReportsOnDeviceReport = deviceInstallationReport
          .flatMap { report =>
            report.installationReport.as[DeviceUpdateCompleted].toOption.map { msg =>
              msg.ecuReports
            }
          }
          .getOrElse(Map.empty)

        processedAssignment.map { pa =>
          val updateEcuReport = ecuReports
            .get(pa.ecuId)
            .map { er =>
              val ecuReportOnDeviceReport =
                ecuReportsOnDeviceReport.get(er.ecuId).map(_.result.description.value)
              val desc = er.description.orElse(ecuReportOnDeviceReport)
              UpdateReportedResult(er.resultCode, er.success, desc)
            }

          pa.ecuId -> UpdateResultResponse(
            hwId,
            ecuTarget.filename,
            pa.successful,
            pa.result.getOrElse(""),
            updateEcuReport
          )
        }
      }
    )

    db.run(io.transactionally)
  }

  import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
  import com.advancedtelematic.libats.slick.db.SlickExtensions.*

  private def findSingleUpdateEcuTargets(
    ns: Namespace,
    deviceId: DeviceId,
    updateId: UpdateId): DBIO[(Update, Map[HardwareIdentifier, EcuTarget])] =
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.deviceId === deviceId)
      .filter(_.id === updateId)
      .resultHead(MissingEntity[Update]())
      .flatMap { update =>
        Schema.hardwareUpdates
          .filter(_.id === update.targetSpecId)
          .join(Schema.ecuTargets)
          .on { case (hwUpdates, ecuTargets) => hwUpdates.toTarget === ecuTargets.id }
          .result
          .map {
            _.map { case (hwUpdates, ecuTarget) =>
              hwUpdates.hardwareId -> ecuTarget
            }.toMap
          }
          .map { ecuTargets =>
            update -> ecuTargets
          }
      }

  private def findUpdateEcuTargets[S[_]: Traverse](
    updates: S[Update]): DBIO[S[(Update, Map[HardwareIdentifier, EcuTarget])]] = {
    val io =
      Schema.hardwareUpdates
        .filter(_.id.inSet(updates.map(_.targetSpecId).toList.toSet))
        .join(Schema.ecuTargets)
        .on { case (hwUpdates, ecuTargets) => ecuTargets.id === hwUpdates.toTarget }
        .result
        .map {
          _.foldLeft(Map.empty[TargetSpecId, Map[HardwareIdentifier, EcuTarget]]) {
            case (acc, (hwUpdate, ecuTarget)) =>
              val existing = acc.getOrElse(hwUpdate.id, Map.empty)
              acc + (hwUpdate.id -> (existing + (hwUpdate.hardwareId -> ecuTarget)))
          }
        }
        .map { ecuTargets =>
          updates.map { update =>
            val targets = ecuTargets.getOrElse(update.targetSpecId, Map.empty)
            (update, targets)
          }
        }

    io
  }

  def findAll(ns: Namespace,
              offset: Offset,
              limit: Limit): Future[PaginationResult[UpdateResponse]] = {
    val io = for {
      updates <- Schema.updates
        .filter(_.namespace === ns)
        .distinctOn(_.id)
        .paginateResult(offset, limit)
      ids = updates.values.map(u => u.deviceId -> u.correlationId).toSet
      deviceResults <- InstallationReportRepository.fetchManyDevicesInstallationResults(ids)
      updateTargets <- findUpdateEcuTargets(updates)
    } yield updateTargets.map { case (update, targets) =>
      UpdateResponse(
        update.id,
        update.status,
        update.createdAt,
        scheduledFor = update.scheduledFor,
        packages = targets.view.mapValues(_.filename).toMap,
        completedAt = update.completedAt,
        deviceResult = deviceResults
          .find(r => r.deviceId == update.deviceId && r.correlationId == update.correlationId)
          .map(r => UpdateReportedResult(r.resultCode, r.success, r.description))
      )
    }

    db.run(io.transactionally)
  }

  def findUpdateDevices(ns: Namespace,
                        updateId: UpdateId,
                        offset: Offset,
                        limit: Limit): Future[PaginationResult[DeviceId]] = db.run {
    Schema.updates
      .filter(_.namespace === ns)
      .filter(_.id === updateId)
      .map(_.deviceId)
      .paginateResult(offset, limit)
  }

  def findFor(ns: Namespace,
              deviceId: DeviceId,
              offset: Offset,
              limit: Limit): Future[PaginationResult[UpdateResponse]] = {
    val io = for {
      updates <- updatesRepository.findAction(ns, deviceId, offset, limit)
      updateTargets <- findUpdateEcuTargets(updates)
      ids = updates.values.map(_.correlationId).toSet
      deviceResults <- InstallationReportRepository.fetchManyByDevice(deviceId, ids)
    } yield updateTargets.map { case (update, targets) =>
      UpdateResponse(
        update.id,
        update.status,
        update.createdAt,
        scheduledFor = update.scheduledFor,
        packages = targets.view.mapValues(_.filename).toMap,
        completedAt = update.completedAt,
        deviceResult = deviceResults
          .find(r => r.deviceId == update.deviceId && r.correlationId == update.correlationId)
          .map(r => UpdateReportedResult(r.resultCode, r.success, r.description))
      )
    }

    db.run(io.transactionally)
  }

  def findEvents(ns: Namespace,
                 deviceId: DeviceId,
                 updateId: UpdateId): Future[Seq[UpdateEventResponse]] =
    db.run(
      Schema.updates
        .filter(_.namespace === ns)
        .filter(_.deviceId === deviceId)
        .filter(_.id === updateId)
        .resultHead(MissingEntity[Update]())
    ).flatMap { update =>
      eventJournal.getEvents(deviceId, update.correlationId.some)
    }.map {
      _.map { event =>
        UpdateEventResponse(
          event.deviceUuid,
          event.eventType,
          event.deviceTime,
          event.receivedAt,
          event.payload.hcursor.downField("success").as[Boolean].toOption,
          event.ecu
        )
      }
    }

}
