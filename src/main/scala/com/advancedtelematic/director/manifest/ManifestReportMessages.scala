package com.advancedtelematic.director.manifest

import java.time.Instant

import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DeviceRequest.{DeviceManifest, EcuManifest, EcuManifestCustom}
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuInstallationReport, InstallationResult}
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceUpdateCompleted

object ManifestReportMessages {

  private def fromInstallationReport(namespace: Namespace, deviceId: DeviceId, deviceManifest: DeviceManifest): Option[DeviceUpdateCompleted] = {
    val reportedImages = deviceManifest.ecu_version_manifests.mapValues { ecuManifest =>
      ecuManifest.signed.installed_image.filepath
    }

    deviceManifest.installation_report.map(_.report).map { report =>
      val ecuResults = report.items
        .filter(item => reportedImages.contains(item.ecu))
        .map { item =>
          item.ecu -> EcuInstallationReport(item.result, Seq(reportedImages(item.ecu).toString))
        }.toMap

      DeviceUpdateCompleted(namespace, Instant.now, report.correlation_id, deviceId, report.result, ecuResults, report.raw_report)
    }
  }

  // Some legacy devices do not send an installation report with correlation, so we need to extract the result from ecu reports
  private def fromEcuManifests(namespace: Namespace, deviceId: DeviceId, manifests: Map[CorrelationId, EcuManifest]): Set[DeviceUpdateCompleted] = {
    manifests.map { case (correlationId, ecuReport) =>
      val ecuReports = ecuReport.custom.flatMap(_.as[EcuManifestCustom].toOption).map { custom =>
        val operationResult = custom.operation_result
        val installationResult = InstallationResult(operationResult.isSuccess, ResultCode(operationResult.result_code.toString), ResultDescription(operationResult.result_text))
        Map(ecuReport.ecu_serial -> EcuInstallationReport(installationResult, Seq(ecuReport.installed_image.filepath.toString)))
      }.getOrElse(Map.empty)

      val installationResult = if (ecuReports.exists(!_._2.result.success)) {
        InstallationResult(success = false, ResultCode("19"), ResultDescription("One or more targeted ECUs failed to update"))
      } else {
        InstallationResult(success = true, ResultCode("0"), ResultDescription("All targeted ECUs were successfully updated"))
      }

      DeviceUpdateCompleted(namespace, Instant.now(), correlationId, deviceId, installationResult, ecuReports)
    }.toSet
  }

  def apply(namespace: Namespace, deviceId: DeviceId, deviceManifest: DeviceManifest, ecuManifests: Map[CorrelationId, EcuManifest]): Set[DeviceUpdateCompleted] = {
    val default = fromInstallationReport(namespace, deviceId, deviceManifest)

    if(default.isDefined) {
      default.toSet
    } else {
      fromEcuManifests(namespace, deviceId, ecuManifests)
    }
  }
}
