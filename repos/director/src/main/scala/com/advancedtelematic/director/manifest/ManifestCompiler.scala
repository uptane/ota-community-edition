package com.advancedtelematic.director.manifest

import com.advancedtelematic.director.data.UptaneDataType.Image
import com.advancedtelematic.director.data.DbDataType.{Assignment, DeviceKnownState, EcuTarget, EcuTargetId}
import com.advancedtelematic.director.data.DeviceRequest.{DeviceManifest, EcuManifest, EcuManifestCustom}
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.libats.data.DataType.{Checksum, HashMethod, Namespace}
import com.advancedtelematic.libats.data.EcuIdentifier
import org.slf4j.LoggerFactory
import cats.syntax.option._
import io.circe.syntax._
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceUpdateEvent

import scala.util.{Failure, Success, Try}


object ManifestCompiler {
  private val _log = LoggerFactory.getLogger(this.getClass)

  case class ManifestCompileResult(knownState: DeviceKnownState, messages: List[DeviceUpdateEvent])

  private def assignmentExists(assignments: Set[Assignment],
                               ecuTargets: Map[EcuTargetId, EcuTarget],
                               ecuIdentifier: EcuIdentifier, ecuManifest: EcuManifest): Option[Assignment] = {
    assignments.find { assignment =>
      val installedPath = ecuManifest.installed_image.filepath
      val installedChecksum = ecuManifest.installed_image.fileinfo.hashes.sha256

      _log.debug(s"looking for ${assignment.ecuTargetId} in $ecuTargets")

      val assignmentTarget = ecuTargets(assignment.ecuTargetId)

      assignment.ecuId == ecuIdentifier &&
        assignmentTarget.filename == installedPath &&
        assignmentTarget.checksum.hash == installedChecksum
    }
  }

  def apply(ns: Namespace, manifest: DeviceManifest): DeviceKnownState => Try[ManifestCompileResult] = (beforeState: DeviceKnownState) => {
    validateManifest(manifest, beforeState).map { _ =>
      val nextStatus = compileManifest(ns, manifest).apply(beforeState)

      val correlationIdProcessedInManifest = manifest.ecu_version_manifests.flatMap { case (ecuId, signedManifest) =>
        assignmentExists(beforeState.currentAssignments, beforeState.ecuTargets, ecuId, signedManifest.signed).map { a =>
          a.correlationId -> signedManifest.signed
        }
      }

      val msgs = ManifestReportMessages(ns, beforeState.deviceId, manifest, correlationIdProcessedInManifest).toList

      ManifestCompileResult(nextStatus, msgs)
    }
  }

  private def validateManifest(manifest: DeviceManifest, deviceKnownStatus: DeviceKnownState): Try[DeviceManifest] = {
    if(manifest.primary_ecu_serial != deviceKnownStatus.primaryEcu)
      Failure(Errors.Manifest.EcuNotPrimary)
    else
      Success(manifest)
  }

  private def compileManifest(ns: Namespace, manifest: DeviceManifest): DeviceKnownState => DeviceKnownState = (knownStatus: DeviceKnownState) => {
    _log.debug(s"current device state: $knownStatus")

    val assignmentsProcessedInManifest =  manifest.ecu_version_manifests.flatMap { case (ecuId, signedManifest) =>
      assignmentExists(knownStatus.currentAssignments, knownStatus.ecuTargets, ecuId, signedManifest.signed)
    }

    val newEcuTargets = manifest.ecu_version_manifests.values.map { ecuManifest =>
      val installedImage = ecuManifest.signed.installed_image
      val existingEcuTarget = findEcuTargetByImage(knownStatus.ecuTargets, installedImage)

      if(existingEcuTarget.isEmpty) {
        _log.debug(s"$installedImage not found in ${knownStatus.ecuTargets}")
        EcuTarget(ns, EcuTargetId.generate, installedImage.filepath, installedImage.fileinfo.length, Checksum(HashMethod.SHA256, installedImage.fileinfo.hashes.sha256), installedImage.fileinfo.hashes.sha256, uri = None)
      } else
        existingEcuTarget.get

    }.map(e => e.id -> e).toMap

    val statusInManifest = manifest.ecu_version_manifests.mapValues { ecuManifest =>
      val newTargetO = findEcuTargetByImage(newEcuTargets, ecuManifest.signed.installed_image)
      newTargetO.map(_.id)
    }.filter(_._2.isDefined)

    val status = DeviceKnownState(
      knownStatus.deviceId,
      knownStatus.primaryEcu,
      knownStatus.ecuStatus ++ statusInManifest,
      knownStatus.ecuTargets ++ newEcuTargets,
      currentAssignments = Set.empty,
      processedAssignments = Set.empty,
      generatedMetadataOutdated = knownStatus.generatedMetadataOutdated)

    val installationReportFailed = manifest.installation_report.exists(!_.report.result.success)

    if(installationReportFailed && assignmentsProcessedInManifest.isEmpty) {
      _log.debug(s"Received error installation report: ${manifest.installation_report.map(_.report)}")
      _log.info(s"${knownStatus.deviceId} Received install report success = false, clearing assignments")

      val report = manifest.installation_report.map(_.report.result).map(_.asJson.noSpaces)
      val desc = s"Device reported installation error and no assignments were processed: ${report}"

      status.copy(
        currentAssignments = Set.empty, // old director behavior is to set this to empty
        processedAssignments = knownStatus.processedAssignments ++ knownStatus.currentAssignments.map(_.toProcessedAssignment(successful = false, result = desc.some)),
        generatedMetadataOutdated = true
      )
    } else if (installationReportFailed) {
      val report = manifest.installation_report.map(_.report.result).map(_.asJson.noSpaces)
      val desc = s"Device reported installation error and no assignments were processed: $report"
      _log.info(s"${knownStatus.deviceId} Received error installation report: $desc")

      status.copy(
        currentAssignments = Set.empty,  // old director behavior is to set this to empty
        processedAssignments = knownStatus.processedAssignments ++ knownStatus.currentAssignments.map(_.toProcessedAssignment(successful = false, result = desc.some)),
        generatedMetadataOutdated = true
      )
    } else {
      _log.info(s"${knownStatus.deviceId} No failed installation report received, assignments processed = $assignmentsProcessedInManifest")

      status.copy(
        currentAssignments = knownStatus.currentAssignments -- assignmentsProcessedInManifest,
        processedAssignments = knownStatus.processedAssignments ++ assignmentsProcessedInManifest.map(_.toProcessedAssignment(successful = true))
      )
    }
  }

  private def findEcuTargetByImage(ecuTargets: Map[EcuTargetId, EcuTarget], image: Image): Option[EcuTarget] = {
    ecuTargets.values.find { ecuTarget =>
      val imagePath = image.filepath
      val imageChecksum = image.fileinfo.hashes.sha256

      ecuTarget.filename == imagePath && ecuTarget.checksum.hash == imageChecksum
    }
  }
}
