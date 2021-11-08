package com.advancedtelematic.director.data

import com.advancedtelematic.libats.data.DataType.CorrelationId
import com.advancedtelematic.libats.messaging_datatype.DataType.InstallationResult
import io.circe.Json

import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload

object DeviceRequest {
  import com.advancedtelematic.director.data.UptaneDataType.Image

  final case class EcuManifest(installed_image: Image,
                               ecu_serial: EcuIdentifier,
                               attacks_detected: String,
                               custom: Option[Json] = None)

  final case class DeviceManifest(primary_ecu_serial: EcuIdentifier,
                                  ecu_version_manifests: Map[EcuIdentifier, SignedPayload[EcuManifest]],
                                  installation_report: Option[InstallationReportEntity] = None)

  final case class OperationResult(id: String, result_code: Int, result_text: String) {
    def isSuccess: Boolean = result_code == 0 || result_code == 1
    def isFail: Boolean = !isSuccess
  }

  final case class EcuManifestCustom(operation_result: OperationResult)

  final case class InstallationReportEntity(content_type: String, report: InstallationReport)

  final case class InstallationReport(
    correlation_id: CorrelationId,
    result: InstallationResult,
    items: Seq[InstallationItem],
    raw_report: Option[String])

  final case class InstallationItem(ecu: EcuIdentifier, result: InstallationResult)
}
