package com.advancedtelematic.director.manifest

import cats.syntax.option.*
import com.advancedtelematic.director.data.DataType.ScheduledUpdate
import com.advancedtelematic.director.data.DbDataType.{
  Assignment,
  DeviceKnownState,
  EcuTarget,
  EcuTargetId,
  ProcessedAssignment
}
import com.advancedtelematic.director.data.DeviceRequest.*
import com.advancedtelematic.director.data.UptaneDataType.Image
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.libats.data.DataType.{
  Checksum,
  CorrelationId,
  HashMethod,
  Namespace,
  ResultCode,
  ResultDescription
}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.{
  DeviceId,
  EcuInstallationReport,
  InstallationResult
}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.*
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeviceUpdateCompleted,
  DeviceUpdateEvent
}
import io.circe.syntax.*
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.util.{Failure, Success, Try}
import com.advancedtelematic.director.data.Codecs.*

object ManifestCompiler {
  private val _log = LoggerFactory.getLogger(this.getClass)

  case class ManifestCompileResult(knownState: DeviceKnownState, messages: List[DeviceUpdateEvent])

  private def assignmentExists(assignments: Set[Assignment],
                               ecuTargets: Map[EcuTargetId, EcuTarget],
                               ecuIdentifier: EcuIdentifier,
                               ecuManifest: EcuManifest): Option[Assignment] =
    assignments.find { assignment =>
      val installedPath = ecuManifest.installed_image.filepath
      val installedChecksum = ecuManifest.installed_image.fileinfo.hashes.sha256

      _log.debug(s"looking for ${assignment.ecuTargetId} in $ecuTargets")

      val assignmentTarget = ecuTargets(assignment.ecuTargetId)

      assignment.ecuId == ecuIdentifier &&
      assignmentTarget.filename == installedPath &&
      assignmentTarget.checksum.hash == installedChecksum
    }

  private def compileScheduleUpdatesChanges(
    knownState: DeviceKnownState,
    msgs: List[DeviceUpdateEvent]): (DeviceKnownState, List[DeviceUpdateEvent]) = {
    /*
    This is not ideal because in compileManifest we match existing assignments using `ecu_version_manifests`
    and checking what is installed on the device. We then do the same again here, to match against the
    scheduled updates ecu targets.

    We cannot rely on processed assignments **only** because scheduled updates might not have generated assignments yet.
    Processed assignments still need to be checked in case the assignment was processed, but not installed, for example,
    if the update as cancelled.

    We cannot rely on correlation id, because we might not have an assignment, which contains the correlation id.

    Here we use knownStatus.ecuStatus rather than `ecu_version_manifests` as we don't have the manifest
    at this point, but knownStatus.ecuStatus is populated based on matching `ecu_version_manifests` so
    that is what would need to end up doing here anyway.
     */

    if (knownState.scheduledUpdates.nonEmpty)
      _log
        .atInfo()
        .addKeyValue("scheduledUpdatesMtuIds", knownState.scheduledUpdates.map(_.updateId).asJson)
        .addKeyValue("deviceId", knownState.deviceId.asJson)
        .log("calculating scheduled updates changes")
    else
      _log
        .atDebug()
        .addKeyValue("deviceId", knownState.deviceId.asJson)
        .log("no scheduled updates")

    val newScheduledUpdates = knownState.scheduledUpdates.map { su =>
      val scheduledUpdateEcuTargets = knownState.scheduledUpdatesEcuTargetIds
        .get(su.updateId)
        .toList
        .flatten
        .flatMap(knownState.ecuTargets.get)

      if (scheduledUpdateEcuTargets.isEmpty) {
        _log
          .atWarn()
          .addKeyValue("deviceId", knownState.deviceId.asJson)
          .addKeyValue("scheduledUpdateId", su.id.asJson)
          .log("no ecu targets for scheduled update")
      }

      val totalTargetsForScheduledUpdate =
        knownState.scheduledUpdatesEcuTargetIds.get(su.updateId).toList.flatten.size

      val processedInEcuStatus = knownState.ecuStatus.values.flatten.filter { ecuTargetId =>
        scheduledUpdateEcuTargets.exists { ecuTarget =>
          knownState.ecuTargets.get(ecuTargetId).exists(_.matches(ecuTarget))
        }
      }

      val processedInAssignment = knownState.processedAssignments
        .map(_.ecuTargetId)
        .filter(scheduledUpdateEcuTargets.map(_.id).contains)

      val alreadyProcessedIds = (processedInEcuStatus ++ processedInAssignment).toSet

      val updated =
        if (
          alreadyProcessedIds.nonEmpty && (alreadyProcessedIds.size >= totalTargetsForScheduledUpdate)
        )
          su.copy(status = ScheduledUpdate.Status.Completed)
        else if (alreadyProcessedIds.nonEmpty)
          su.copy(status = ScheduledUpdate.Status.PartiallyCompleted)
        else
          su

      _log
        .atInfo()
        .addKeyValue("deviceId", knownState.deviceId.asJson)
        .addKeyValue("totalTargetsForScheduledUpdate", totalTargetsForScheduledUpdate)
        .addKeyValue("alreadyProcessedCount", alreadyProcessedIds.size)
        .addKeyValue("processedAsAssignment", processedInAssignment.asJson)
        .addKeyValue("processedInEcuStatus", processedInEcuStatus.asJson)
        .addKeyValue("scheduledUpdateEcuTargets", scheduledUpdateEcuTargets.map(_.id).asJson)
        .addKeyValue("result", updated.asJson)
        .log(s"updating scheduled updates for ${su.id}")

      updated
    }

    (knownState.copy(scheduledUpdates = newScheduledUpdates), msgs)
  }

  def apply(ns: Namespace,
            manifest: DeviceManifest): DeviceKnownState => Try[ManifestCompileResult] =
    (beforeState: DeviceKnownState) =>
      validateManifest(manifest, beforeState).map { _ =>
        val (nextStatus, msgs) = compileManifest(ns, manifest)
          .andThen(compileScheduleUpdatesChanges.tupled)
          .apply(beforeState)
        ManifestCompileResult(nextStatus, msgs)
      }

  private def validateManifest(manifest: DeviceManifest,
                               deviceKnownStatus: DeviceKnownState): Try[DeviceManifest] =
    if (manifest.primary_ecu_serial != deviceKnownStatus.primaryEcu)
      Failure(Errors.Manifest.EcuNotPrimary)
    else
      Success(manifest)

  private def compileManifest(
    ns: Namespace,
    manifest: DeviceManifest): DeviceKnownState => (DeviceKnownState, List[DeviceUpdateCompleted]) =
    (knownStatus: DeviceKnownState) => {
      _log.debug(s"current device state: $knownStatus")

      val assignmentsProcessedInManifest = manifest.ecu_version_manifests.flatMap {
        case (ecuId, signedManifest) =>
          assignmentExists(
            knownStatus.currentAssignments,
            knownStatus.ecuTargets,
            ecuId,
            signedManifest.signed
          )
      }

      val newEcuTargets = manifest.ecu_version_manifests.values
        .flatMap { ecuManifest =>
          val installedImage = ecuManifest.signed.installed_image
          val existingEcuTarget = findEcuTargetsByImage(knownStatus.ecuTargets, installedImage)

          if (existingEcuTarget.isEmpty) {
            _log.debug(s"$installedImage not found in ${knownStatus.ecuTargets}")
            List(
              EcuTarget(
                ns,
                EcuTargetId.generate(),
                installedImage.filepath,
                installedImage.fileinfo.length,
                Checksum(HashMethod.SHA256, installedImage.fileinfo.hashes.sha256),
                installedImage.fileinfo.hashes.sha256,
                uri = None,
                userDefinedCustom = None
              )
            )
          } else
            existingEcuTarget

        }
        .map(e => e.id -> e)
        .toMap

      val statusInManifest = manifest.ecu_version_manifests.view
        .mapValues { ecuManifest =>
          val newTargetO = findEcuTargetsByImage(newEcuTargets, ecuManifest.signed.installed_image)

          if (newTargetO.length > 1)
            _log
              .atError()
              .addKeyValue("deviceId", knownStatus.deviceId.asJson)
              .addKeyValue("matchingTargets", newTargetO.asJson)
              .log(
                "too many ecu targets match the reported image for ecu. This can cause problems when determining what is installed on the device"
              )

          newTargetO.headOption.map(_.id)
        }
        .filter(_._2.isDefined)

      val status = DeviceKnownState(
        knownStatus.deviceId,
        knownStatus.primaryEcu,
        knownStatus.ecuStatus ++ statusInManifest,
        knownStatus.ecuTargets ++ newEcuTargets,
        currentAssignments = Set.empty,
        processedAssignments = Set.empty,
        scheduledUpdates = knownStatus.scheduledUpdates,
        knownStatus.scheduledUpdatesEcuTargetIds,
        generatedMetadataOutdated = knownStatus.generatedMetadataOutdated
      )

      val installationReport = manifest.installation_report.map(_.report)

      installationReport match {
        case Left(MissingInstallationReport) =>
          val desc =
            s"Missing installation report, assignments processed = $assignmentsProcessedInManifest"
          _log.info(s"${knownStatus.deviceId} " + desc)

          val newStatus = status.copy(
            currentAssignments = knownStatus.currentAssignments -- assignmentsProcessedInManifest,
            processedAssignments =
              knownStatus.processedAssignments ++ assignmentsProcessedInManifest.map(
                _.toProcessedAssignment(successful = true, canceled = false, desc.some)
              )
          )

          val msgs = resultMsgFromEcuManifests(ns, knownStatus, manifest)

          newStatus -> msgs

        case Left(InvalidInstallationReport(reason, payload)) =>
          val desc =
            s"${knownStatus.deviceId} Device sent an invalid installation report ($reason). Cancelling current assignments"
          _log.info(desc)
          val cancelled = assignmentsProcessedInManifest.isEmpty
          val cancelledAssignments = knownStatus.currentAssignments.map(
            _.toProcessedAssignment(successful = false, canceled = cancelled, desc.some)
          )

          val newStatus = status.copy(
            currentAssignments = Set.empty,
            processedAssignments = knownStatus.processedAssignments ++ cancelledAssignments
          )

          val msgs = buildMessagesFromCancelledAssignments(
            ns,
            knownStatus.deviceId,
            desc,
            cancelledAssignments,
            ResultCodes.DeviceSentInvalidInstallationReport,
            payload.map(_.noSpaces)
          )

          newStatus -> msgs

        case Right(report) if !report.result.success =>
          val _report = manifest.installation_report.map(_.report.result).map(_.asJson.noSpaces)
          val desc = s"Device reported installation error: ${_report}"
          _log
            .atInfo()
            .addKeyValue("deviceId", knownStatus.deviceId.asJson)
            .addKeyValue("report", desc.asJson)
            .log(s"${knownStatus.deviceId} Received error installation report: $desc")

          val cancelled = assignmentsProcessedInManifest.isEmpty
          val cancelledAssignments = knownStatus.currentAssignments.map(
            _.toProcessedAssignment(successful = false, canceled = cancelled, desc.some)
          )

          val newStatus = status.copy(
            currentAssignments = Set.empty,
            processedAssignments = knownStatus.processedAssignments ++ cancelledAssignments,
            generatedMetadataOutdated = true
          )

          val processedCorrelationIds = assignmentsProcessedInManifest.map(_.correlationId).toList

          var msgs =
            if (assignmentsProcessedInManifest.isEmpty)
              buildMessagesFromCancelledAssignments(
                ns,
                knownStatus.deviceId,
                desc,
                cancelledAssignments,
                ResultCodes.DirectorCancelledAssignment
              )
            else
              resultMsgFromInstallationReport(
                ns,
                knownStatus.deviceId,
                manifest,
                processedCorrelationIds,
                report
              )

          if (!processedCorrelationIds.contains(report.correlation_id))
            msgs = msgs ++ resultMsgFromInstallationReport(
              ns,
              knownStatus.deviceId,
              manifest,
              List(report.correlation_id),
              report
            )

          newStatus -> msgs

        case Right(report) if assignmentsProcessedInManifest.isEmpty =>
          val desc =
            "Device sent a successful installation report, but no assignments were processed in the manifest"
          _log.info(s"${knownStatus.deviceId} " + desc)

          val cancelledAssignments = knownStatus.currentAssignments.map(
            _.toProcessedAssignment(successful = false, canceled = true, desc.some)
          )

          val newStatus = status.copy(
            currentAssignments = Set.empty,
            processedAssignments = knownStatus.processedAssignments ++ cancelledAssignments,
            generatedMetadataOutdated = true
          )

          var msgs = buildMessagesFromCancelledAssignments(
            ns,
            knownStatus.deviceId,
            desc,
            cancelledAssignments,
            ResultCodes.DirectorCancelledAssignment
          )

          if (!cancelledAssignments.map(_.correlationId).contains(report.correlation_id))
            msgs = msgs ++ resultMsgFromInstallationReport(
              ns,
              knownStatus.deviceId,
              manifest,
              List(report.correlation_id),
              report
            )

          newStatus -> msgs

        case Right(report) =>
          val desc = "Device sent a successful installation report"
          _log.info(
            s"${knownStatus.deviceId} Device sent a successful installation report, processing assignments $assignmentsProcessedInManifest"
          )

          val newStatus = status.copy(
            currentAssignments = knownStatus.currentAssignments -- assignmentsProcessedInManifest,
            processedAssignments =
              knownStatus.processedAssignments ++ assignmentsProcessedInManifest.map(
                _.toProcessedAssignment(successful = true, canceled = false, desc.some)
              )
          )

          val correlationIds =
            assignmentsProcessedInManifest.map(_.correlationId).toSet + report.correlation_id
          val msg = resultMsgFromInstallationReport(
            ns,
            knownStatus.deviceId,
            manifest,
            correlationIds.toList,
            report
          )

          newStatus -> msg
      }
    }

  private def buildMessagesFromCancelledAssignments(
    ns: Namespace,
    deviceId: DeviceId,
    desc: String,
    cancelledAssignments: Iterable[ProcessedAssignment],
    resultCode: ResultCode,
    rawReport: Option[String] = None) =
    if (cancelledAssignments.nonEmpty) {
      val now = Instant.now
      val rdesc = ResultDescription(desc)
      val ir = InstallationResult(success = false, code = resultCode, description = rdesc)

      cancelledAssignments.toList.map { assignment =>
        DeviceUpdateCompleted(ns, now, assignment.correlationId, deviceId, ir, Map.empty, rawReport)
      }
    } else
      List.empty

  private def findEcuTargetsByImage(ecuTargets: Map[EcuTargetId, EcuTarget],
                                    image: Image): Seq[EcuTarget] =
    ecuTargets.values.filter { ecuTarget =>
      val imagePath = image.filepath
      val imageChecksum = image.fileinfo.hashes.sha256

      ecuTarget.filename == imagePath && ecuTarget.checksum.hash == imageChecksum
    }.toSeq

  private def resultMsgFromInstallationReport(
    namespace: Namespace,
    deviceId: DeviceId,
    deviceManifest: DeviceManifest,
    processedCorrelationIds: List[CorrelationId],
    report: InstallationReport): List[DeviceUpdateCompleted] = {

    val reportedImages = deviceManifest.ecu_version_manifests.view.mapValues { ecuManifest =>
      ecuManifest.signed.installed_image.filepath
    }

    val ecuResults = report.items
      .filter(item => reportedImages.contains(item.ecu))
      .map { item =>
        item.ecu -> EcuInstallationReport(item.result, Seq(reportedImages(item.ecu).toString))
      }
      .toMap

    val now = Instant.now

    processedCorrelationIds.map { cid =>
      DeviceUpdateCompleted(
        namespace,
        now,
        cid,
        deviceId,
        report.result,
        ecuResults,
        report.raw_report
      )
    }
  }

  // Some legacy devices do not send an installation report, so we need to extract the result from ecu reports
  private def resultMsgFromEcuManifests(namespace: Namespace,
                                        beforeState: DeviceKnownState,
                                        manifest: DeviceManifest): List[DeviceUpdateCompleted] = {
    val manifests = manifest.ecu_version_manifests.flatMap { case (ecuId, signedManifest) =>
      assignmentExists(
        beforeState.currentAssignments,
        beforeState.ecuTargets,
        ecuId,
        signedManifest.signed
      ).map { a =>
        a.correlationId -> signedManifest.signed
      }
    }

    manifests.map { case (correlationId, ecuReport) =>
      val ecuReports = ecuReport.custom
        .flatMap(_.as[EcuManifestCustom].toOption)
        .map { custom =>
          val operationResult = custom.operation_result
          val installationResult = InstallationResult(
            operationResult.isSuccess,
            ResultCode(operationResult.result_code.toString),
            ResultDescription(operationResult.result_text)
          )
          // we could add hash here as well in targets not just filepath
          Map(
            ecuReport.ecu_serial -> EcuInstallationReport(
              installationResult,
              Seq(ecuReport.installed_image.filepath.toString)
            )
          )
        }
        .getOrElse(Map.empty)

      val installationResult =
        if (ecuReports.exists(!_._2.result.success))
          InstallationResult(
            success = false,
            ResultCode("19"),
            ResultDescription("One or more targeted ECUs failed to update")
          )
        else
          InstallationResult(
            success = true,
            ResultCode("0"),
            ResultDescription("All targeted ECUs were successfully updated")
          )

      DeviceUpdateCompleted(
        namespace,
        Instant.now(),
        correlationId,
        beforeState.deviceId,
        installationResult,
        ecuReports
      )
    }.toList
  }

}
