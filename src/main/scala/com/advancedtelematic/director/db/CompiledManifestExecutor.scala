package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.DbDataType.{DeviceKnownState, EcuTargetId}
import com.advancedtelematic.director.manifest.ManifestCompiler.ManifestCompileResult
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric._
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class CompiledManifestExecutor()(implicit val db: Database, val ec: ExecutionContext) {

  private val _log = LoggerFactory.getLogger(this.getClass)

  private def findStateAction(deviceId: DeviceId): DBIO[DeviceKnownState] = {
    for {
      assignments <- Schema.assignments.filter(_.deviceId === deviceId).result
      processed <- Schema.processedAssignments.filter(_.deviceId === deviceId).result
      ecuStatus <- Schema.activeEcus.filter(_.deviceId === deviceId).map(ecu => ecu.ecuSerial -> ecu.installedTarget).result
      device <- Schema.allDevices.filter(_.id === deviceId).result.head
      ecuTargetIds = ecuStatus.flatMap(_._2) ++ assignments.map(_.ecuTargetId)
      ecuTargets <- Schema.ecuTargets.filter(_.id.inSet(ecuTargetIds)).map { t => t.id -> t }.result
    } yield DeviceKnownState(deviceId, device.primaryEcuId, ecuStatus.toMap, ecuTargets.toMap, assignments.toSet, processed.toSet, device.generatedMetadataOutdated)
  }

  private def updateEcuAction(deviceId: DeviceId, ecuIdentifier: EcuIdentifier, installedTarget: Option[EcuTargetId]): DBIO[Unit] = {
    Schema.activeEcus
      .filter(_.deviceId === deviceId)
      .filter(_.ecuSerial === ecuIdentifier).map(_.installedTarget).update(installedTarget).map(_ => ())
  }

  private def updateStatusAction(deviceId: DeviceId, oldStatus: DeviceKnownState, newStatus: DeviceKnownState): DBIO[Unit] = {
    assert(oldStatus.primaryEcu == newStatus.primaryEcu, "a device cannot change its primary ecu")

    val assignmentsToDelete = (oldStatus.currentAssignments -- newStatus.currentAssignments).map(_.ecuId)
    val newProcessedAssignments = newStatus.processedAssignments -- oldStatus.processedAssignments

    val changedEcuStatus = newStatus.ecuStatus.filter { case (ecuId, ecuTargetId) =>  oldStatus.ecuStatus.get(ecuId).flatten != ecuTargetId }
    val newEcuTargets = newStatus.ecuTargets -- oldStatus.ecuTargets.keys

    for {
      _ <- DBIO.sequence(newEcuTargets.values.map(Schema.ecuTargets.insertOrUpdate))
      _ <- DBIO.sequence(changedEcuStatus.map { case (ecu, target) => updateEcuAction(deviceId, ecu, target) })
      _ <- Schema.assignments.filter(_.deviceId === deviceId).filter(_.ecuId.inSet(assignmentsToDelete)).delete
      _ <- DBIO.sequence(newProcessedAssignments.map(Schema.processedAssignments += _).toList )
      _ <- updateMetadataOutdatedFlagAction(deviceId, oldStatus, newStatus)
    } yield ()
  }

  private def updateMetadataOutdatedFlagAction(deviceId: DeviceId, old: DeviceKnownState, newStatus: DeviceKnownState): DBIO[Unit] = {
    if(old.generatedMetadataOutdated != newStatus.generatedMetadataOutdated)
      Schema.allDevices.filter(_.id === deviceId)
        .map(_.generatedMetadataOutdated)
        .update(newStatus.generatedMetadataOutdated)
        .map(_ => ())
    else
      DBIO.successful(())
  }

  private def dbActionFromTry[T](t: Try[T]): DBIO[T] = t match {
    case Success(v) => DBIO.successful(v)
    case Failure(ex) => DBIO.failed(ex)
  }

  def process(deviceId: DeviceId, compiledManifest: DeviceKnownState => Try[ManifestCompileResult]): Future[ManifestCompileResult] = {
    val io = for {
      initialStatus <- findStateAction(deviceId)
      manifestCompileResult <- dbActionFromTry(compiledManifest.apply(initialStatus))
      _ = _log.debug(s"Updating device status to ${manifestCompileResult.knownState}")
      _ <- updateStatusAction(deviceId, initialStatus, manifestCompileResult.knownState)
    } yield manifestCompileResult

    db.run(io.transactionally)
  }
}
