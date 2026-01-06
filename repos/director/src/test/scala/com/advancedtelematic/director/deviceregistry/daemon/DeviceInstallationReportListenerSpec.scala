package com.advancedtelematic.director.deviceregistry.daemon

import com.advancedtelematic.director.db.deviceregistry.InstallationReportRepository
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceInstallationResult,
  EcuInstallationResult
}
import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*
import com.advancedtelematic.director.deviceregistry.data.{
  DeviceStatus,
  InstallationReportGenerators
}
import com.advancedtelematic.director.http.LogDeviceSeen
import com.advancedtelematic.director.http.deviceregistry.{RegistryDeviceRequests, ResourcePropSpec}
import com.advancedtelematic.director.util.DirectorSpec
import com.advancedtelematic.libats.data.DataType.ResultCode
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.deviceUpdateCompletedCodec
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceUpdateInFlight
import io.circe.syntax.*

import java.time.Instant

class DeviceInstallationReportListenerSpec
    extends DirectorSpec
    with ResourcePropSpec
    with RegistryDeviceRequests {

  import InstallationReportGenerators.*
  import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.*

  val listener = new DeviceUpdateEventListener(msgPub)

  test("should parse and save DeviceUpdateReport messages and is idempotent") {
    val deviceUuid = createDeviceOk(genDeviceT.generate)
    val correlationId = genCorrelationId.generate
    val message = genDeviceUpdateCompleted(correlationId, ResultCode("0"), deviceUuid).generate

    listener.apply(message).futureValue shouldBe (())

    val expectedDeviceReports =
      Seq(
        DeviceInstallationResult(
          correlationId,
          message.result.code,
          deviceUuid,
          message.result.success,
          message.eventTime,
          message.asJson
        )
      )
    val deviceReports =
      db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReports.futureValue shouldBe expectedDeviceReports

    val expectedEcuReports = message.ecuReports.map { case (ecuId, ecuReport) =>
      EcuInstallationResult(
        correlationId,
        ecuReport.result.code,
        deviceUuid,
        ecuId,
        message.result.success
      )
    }.toSeq
    val ecuReports = db.run(InstallationReportRepository.fetchEcuInstallationReport(correlationId))
    ecuReports.futureValue shouldBe expectedEcuReports

    // Saving the reports is idempotent
    listener.apply(message).futureValue shouldBe (())

    val deviceReportsAgain =
      db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReportsAgain.futureValue shouldBe expectedDeviceReports
    val ecuReportsAgain =
      db.run(InstallationReportRepository.fetchEcuInstallationReport(correlationId))
    ecuReportsAgain.futureValue shouldBe expectedEcuReports

  }

  test("should save success result after failed one") {
    val deviceUuid = createDeviceOk(genDeviceT.generate)
    val correlationId = genCorrelationId.generate
    val messageFailed =
      genDeviceUpdateCompleted(correlationId, ResultCode("-1"), deviceUuid).generate
    val messageSuccess =
      genDeviceUpdateCompleted(correlationId, ResultCode("0"), deviceUuid).generate

    listener.apply(messageFailed).futureValue shouldBe (())

    val expectedDeviceReportsFailed =
      Seq(
        DeviceInstallationResult(
          correlationId,
          messageFailed.result.code,
          deviceUuid,
          messageFailed.result.success,
          messageFailed.eventTime,
          messageFailed.asJson
        )
      )
    val expectedDeviceReportsSuccess =
      Seq(
        DeviceInstallationResult(
          correlationId,
          messageSuccess.result.code,
          deviceUuid,
          messageSuccess.result.success,
          messageSuccess.eventTime,
          messageSuccess.asJson
        )
      )

    val deviceReports =
      db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReports.futureValue shouldBe expectedDeviceReportsFailed

    listener.apply(messageSuccess).futureValue shouldBe (())

    val deviceReportsAgain =
      db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReportsAgain.futureValue shouldBe expectedDeviceReportsSuccess

  }

  test("should process DeviceUpdateInFlight") {
    val deviceId = createDeviceOk(genDeviceT.generate)
    val correlationId = genCorrelationId.generate
    val updateInFlight = DeviceUpdateInFlight(defaultNs, Instant.now(), correlationId, deviceId)

    LogDeviceSeen.logDevice(defaultNs, deviceId).futureValue

    listener.apply(updateInFlight).futureValue

    val device = fetchDeviceOk(deviceId)

    device.deviceStatus shouldBe DeviceStatus.UpdatePending
  }

}
