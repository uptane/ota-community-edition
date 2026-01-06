package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.DataType.Update.Status
import com.advancedtelematic.director.data.DbDataType.{Device, DeviceKnownState, EcuTargetId}
import com.advancedtelematic.director.manifest.ManifestCompiler.ManifestCompileResult
import com.advancedtelematic.libats.http.Errors.MissingEntity
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier}
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import SlickMapping.updateStatusMapper
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.TransactionIsolation
import com.advancedtelematic.libats.slick.codecs.SlickRefined.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.advancedtelematic.libats.slick.db.SlickExtensions.DBIOSeqOps

class CompiledManifestExecutor()(implicit val db: Database, val ec: ExecutionContext) {

  private val _log = LoggerFactory.getLogger(this.getClass)

  protected[director] def findStateAction(deviceId: DeviceId): DBIO[DeviceKnownState] =
    for {
      assignments <- Schema.assignments.filter(_.deviceId === deviceId).result
      processed <- Schema.processedAssignments.filter(_.deviceId === deviceId).result
      ecuStatus <- Schema.activeEcus
        .filter(_.deviceId === deviceId)
        .map(ecu => ecu.ecuSerial -> ecu.installedTarget)
        .result
      device <- Schema.allProvisionedDevices
        .filter(_.id === deviceId)
        .result
        .failIfNotSingle(MissingEntity[Device]())
      updates <- Schema.updates
        .filter(_.deviceId === deviceId)
        .filterNot(_.status.inSet(Set(Status.Completed, Status.Cancelled)))
        .result
      hardwareUpdatesEcuTargetIds <- Schema.hardwareUpdates
        .filter(_.id.inSet(updates.map(_.targetSpecId).toSet))
        .map(t => t.id -> t.toTarget)
        .result
      ecuTargetIds = ecuStatus.flatMap(_._2) ++ assignments.map(
        _.ecuTargetId
      ) ++ hardwareUpdatesEcuTargetIds.map(_._2)
      ecuTargets <- Schema.ecuTargets.filter(_.id.inSet(ecuTargetIds)).map(t => t.id -> t).result
    } yield DeviceKnownState(
      deviceId,
      device.primaryEcuId,
      ecuStatus.toMap,
      ecuTargets.toMap,
      assignments.toSet,
      processed.toSet,
      updates.toSet,
      hardwareUpdatesEcuTargetIds.groupBy(_._1).view.mapValues(_.map(_._2)).toMap,
      device.generatedMetadataOutdated
    )

  private def updateEcuAction(deviceId: DeviceId,
                              ecuIdentifier: EcuIdentifier,
                              installedTarget: Option[EcuTargetId]): DBIO[Unit] =
    Schema.activeEcus
      .filter(_.deviceId === deviceId)
      .filter(_.ecuSerial === ecuIdentifier)
      .map(_.installedTarget)
      .update(installedTarget)
      .map(_ => ())

  private def updateStatusAction(deviceId: DeviceId,
                                 oldStatus: DeviceKnownState,
                                 newStatus: DeviceKnownState): DBIO[Unit] = {
    assert(oldStatus.primaryEcu == newStatus.primaryEcu, "a device cannot change its primary ecu")

    val assignmentsToDelete =
      (oldStatus.currentAssignments -- newStatus.currentAssignments).map(_.ecuId)
    val newProcessedAssignments = newStatus.processedAssignments -- oldStatus.processedAssignments

    val changedEcuStatus = newStatus.ecuStatus.filter { case (ecuId, ecuTargetId) =>
      oldStatus.ecuStatus.get(ecuId).flatten != ecuTargetId
    }
    val newEcuTargets = newStatus.ecuTargets -- oldStatus.ecuTargets.keys

    val deleteAssignmentsIO =
      if (assignmentsToDelete.isEmpty)
        DBIO.successful(())
      else
        Schema.assignments
          .filter(_.deviceId === deviceId)
          .filter(_.ecuId.inSet(assignmentsToDelete))
          .delete

    val newUpdates = newStatus.updates -- oldStatus.updates

    for {
      _ <- DBIO.sequence(newEcuTargets.values.map(Schema.ecuTargets.insertOrUpdate))
      _ <- DBIO.sequence(changedEcuStatus.map { case (ecu, target) =>
        updateEcuAction(deviceId, ecu, target)
      })
      _ <- deleteAssignmentsIO
      _ <- DBIO.sequence(newProcessedAssignments.map(Schema.processedAssignments += _).toList)
      _ <- updateMetadataOutdatedFlagAction(deviceId, oldStatus, newStatus)
      _ <- DBIO.sequence(newUpdates.map(Schema.updates.insertOrUpdate).toList)
    } yield ()
  }

  private def updateMetadataOutdatedFlagAction(deviceId: DeviceId,
                                               old: DeviceKnownState,
                                               newStatus: DeviceKnownState): DBIO[Unit] =
    if (old.generatedMetadataOutdated != newStatus.generatedMetadataOutdated)
      Schema.allProvisionedDevices
        .filter(_.id === deviceId)
        .map(_.generatedMetadataOutdated)
        .update(newStatus.generatedMetadataOutdated)
        .map(_ => ())
    else
      DBIO.successful(())

  private def dbActionFromTry[T](t: Try[T]): DBIO[T] = t match {
    case Success(v)  => DBIO.successful(v)
    case Failure(ex) => DBIO.failed(ex)
  }

  def process(deviceId: DeviceId, compiledManifest: DeviceKnownState => Try[ManifestCompileResult])
    : Future[ManifestCompileResult] = {
    val io = for {
      initialStatus <- findStateAction(deviceId)
      manifestCompileResult <- dbActionFromTry(compiledManifest.apply(initialStatus))
      _ = _log.debug(s"Updating device status to ${manifestCompileResult.knownState}")
      _ <- updateStatusAction(deviceId, initialStatus, manifestCompileResult.knownState)
    } yield manifestCompileResult

    db.run(io.withTransactionIsolation(TransactionIsolation.Serializable).transactionally)
  }

}
