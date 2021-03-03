package com.advancedtelematic.ota.deviceregistry.daemon

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.advancedtelematic.libats.data.DataType.ResultCode
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.deviceUpdateCompletedCodec
import com.advancedtelematic.ota.deviceregistry.DatabaseSpec
import com.advancedtelematic.ota.deviceregistry.data.DataType.{DeviceInstallationResult, EcuInstallationResult}
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps._
import com.advancedtelematic.ota.deviceregistry.data.InstallationReportGenerators
import com.advancedtelematic.ota.deviceregistry.db.InstallationReportRepository
import com.advancedtelematic.ota.deviceregistry.ResourcePropSpec
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.Matchers

class DeviceUpdateEventListenerSpec
    extends ResourcePropSpec
    with ScalatestRouteTest
    with DatabaseSpec
    with ScalaFutures
    with Matchers
    with InstallationReportGenerators {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(Span(10, Seconds), Span(50, Millis))

  implicit val msgPub = MessageBusPublisher.ignore

  val listener = new DeviceUpdateEventListener(msgPub)

  property("should parse and save DeviceUpdateReport messages and is idempotent") {
    val deviceUuid = createDeviceOk(genDeviceT.generate)
    val correlationId = genCorrelationId.generate
    val message = genDeviceUpdateCompleted(correlationId, ResultCode("0"), deviceUuid).generate

    listener.apply(message).futureValue shouldBe (())

    val expectedDeviceReports =
      Seq(DeviceInstallationResult(correlationId, message.result.code, deviceUuid, message.result.success, message.eventTime, message.asJson))
    val deviceReports = db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReports.futureValue shouldBe expectedDeviceReports

    val expectedEcuReports = message.ecuReports.map{
      case (ecuId, ecuReport) => EcuInstallationResult(correlationId, ecuReport.result.code, deviceUuid, ecuId, message.result.success)
    }.toSeq
    val ecuReports = db.run(InstallationReportRepository.fetchEcuInstallationReport(correlationId))
    ecuReports.futureValue shouldBe expectedEcuReports

    // Saving the reports is idempotent
    listener.apply(message).futureValue shouldBe (())

    val deviceReportsAgain = db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReportsAgain.futureValue shouldBe expectedDeviceReports
    val ecuReportsAgain = db.run(InstallationReportRepository.fetchEcuInstallationReport(correlationId))
    ecuReportsAgain.futureValue shouldBe expectedEcuReports

  }

  property("should save success result after failed one") {
    val deviceUuid = createDeviceOk(genDeviceT.generate)
    val correlationId = genCorrelationId.generate
    val messageFailed = genDeviceUpdateCompleted(correlationId, ResultCode("-1"), deviceUuid).generate
    val messageSuccess = genDeviceUpdateCompleted(correlationId, ResultCode("0"), deviceUuid).generate

    listener.apply(messageFailed).futureValue shouldBe (())

    val expectedDeviceReportsFailed =
      Seq(DeviceInstallationResult(correlationId, messageFailed.result.code, deviceUuid, messageFailed.result.success, messageFailed.eventTime, messageFailed.asJson))
    val expectedDeviceReportsSuccess =
      Seq(DeviceInstallationResult(correlationId, messageSuccess.result.code, deviceUuid, messageSuccess.result.success, messageSuccess.eventTime, messageSuccess.asJson))

    val deviceReports = db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReports.futureValue shouldBe expectedDeviceReportsFailed

    listener.apply(messageSuccess).futureValue shouldBe (())

    val deviceReportsAgain = db.run(InstallationReportRepository.fetchDeviceInstallationResult(correlationId))
    deviceReportsAgain.futureValue shouldBe expectedDeviceReportsSuccess

  }

}
