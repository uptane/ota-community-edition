package com.advancedtelematic.ota.deviceregistry

import java.time.Instant
import java.time.temporal.ChronoUnit
import akka.http.scaladsl.model.StatusCodes._
import com.advancedtelematic.libats.data.DataType.ResultCode
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.{deviceUpdateCompletedCodec, ecuReplacementCodec}
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeleteDeviceRequest, DeviceUpdateCompleted, DeviceUpdateInFlight, EcuReplaced, EcuReplacement, EcuReplacementFailed}
import com.advancedtelematic.ota.deviceregistry.daemon.{DeleteDeviceListener, DeviceUpdateEventListener, EcuReplacementListener}
import com.advancedtelematic.ota.deviceregistry.data.Codecs.installationStatDecoder
import com.advancedtelematic.ota.deviceregistry.data.DataType.{InstallationStat, InstallationStatsLevel}
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps._
import com.advancedtelematic.ota.deviceregistry.data.{DeviceStatus, InstallationReportGenerators}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import org.scalacheck.Gen
import org.scalatest.EitherValues._
import org.scalatest.LoneElement._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

class InstallationReportSpec extends ResourcePropSpec with ScalaFutures with Eventually with InstallationReportGenerators {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(50, Millis))

  implicit val msgPub = new MockMessageBus

  val updateListener = new DeviceUpdateEventListener(msgPub)
  val ecuReplacementListener = new EcuReplacementListener
  val deleteDeviceListener = new DeleteDeviceListener()

  property("should save device reports and retrieve failed stats per devices") {
    val correlationId = genCorrelationId.generate
    val resultCodes = Seq("0", "1", "2", "2", "3", "3", "3").map(ResultCode)
    val updatesCompleted = resultCodes.map(genDeviceUpdateCompleted(correlationId, _)).map(_.generate)

    updatesCompleted.foreach(updateListener.apply)

    eventually {
      getStats(correlationId, InstallationStatsLevel.Device) ~> route ~> check {
        status shouldBe OK
        val expected = Seq(
            InstallationStat(ResultCode("0"), 1, true),
            InstallationStat(ResultCode("1"), 1, false),
            InstallationStat(ResultCode("2"), 2, false),
            InstallationStat(ResultCode("3"), 3, false)
        )
        responseAs[Seq[InstallationStat]] shouldBe expected
      }
    }
  }

  property("should save device reports and retrieve failed stats per ECUs") {
    val correlationId = genCorrelationId.generate
    val resultCodes = Seq("0", "1", "2", "2", "3", "3", "3").map(ResultCode)
    val updatesCompleted = resultCodes.map(genDeviceUpdateCompleted(correlationId, _)).map(_.generate)

    updatesCompleted.foreach(updateListener.apply)

    eventually {
      getStats(correlationId, InstallationStatsLevel.Ecu) ~> route ~> check {
        status shouldBe OK
        val expected = Seq(
          InstallationStat(ResultCode("0"), 1, true),
          InstallationStat(ResultCode("1"), 1, false),
          InstallationStat(ResultCode("2"), 2, false),
          InstallationStat(ResultCode("3"), 3, false)
        )
        responseAs[Seq[InstallationStat]] shouldBe expected
      }
    }
  }

  property("should save the whole message as a blob and get back the history for a device") {
    val deviceId       = createDeviceOk(genDeviceT.generate)
    val correlationIds = Gen.listOfN(50, genCorrelationId).generate
    val updatesCompleted  = correlationIds.map(cid => genDeviceUpdateCompleted(cid, ResultCode("0"), deviceId)).map(_.generate)

    updatesCompleted.foreach(updateListener.apply)

    eventually {
      getReportBlob(deviceId) ~> route ~> check {
        status shouldBe OK
        responseAs[PaginationResult[DeviceUpdateCompleted]].values should contain allElementsOf updatesCompleted
      }
    }
  }

  property("does not overwrite existing reports") {
    val deviceId = createDeviceOk(genDeviceT.generate)
    val correlationId = genCorrelationId.generate
    val updateCompleted1 = genDeviceUpdateCompleted(correlationId, ResultCode("0"), deviceId).generate
    val updateCompleted2 =  genDeviceUpdateCompleted(correlationId, ResultCode("1"), deviceId).generate

    updateListener.apply(updateCompleted1).futureValue

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceUpdateCompleted]].values.loneElement.result.code shouldBe ResultCode("0")
    }

    updateListener.apply(updateCompleted2).futureValue

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceUpdateCompleted]].values.loneElement.result.code shouldBe ResultCode("0")
    }
  }

  property("should fetch installation events and ECU replacement events") {
    val deviceId = createDeviceOk(genDeviceT.generate)
    val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)

    val correlationId1 = genCorrelationId.generate
    val correlationId2 = genCorrelationId.generate
    val updateCompleted1 = genDeviceUpdateCompleted(correlationId1, ResultCode("0"), deviceId, receivedAt = now).generate
    val successfulReplacement = genEcuReplacement(deviceId, now.plusSeconds(60), success = true).generate
    val updateCompleted2 =  genDeviceUpdateCompleted(correlationId2, ResultCode("1"), deviceId, receivedAt = now.plusSeconds(120)).generate
    val failedReplacement = genEcuReplacement(deviceId, now.plusSeconds(180), success = false).generate

    updateListener(updateCompleted1).futureValue
    ecuReplacementListener(successfulReplacement).futureValue
    updateListener.apply(updateCompleted2).futureValue
    ecuReplacementListener(failedReplacement).futureValue

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Json]].values
      result(0).as[EcuReplacement].right.value.asInstanceOf[EcuReplacementFailed] shouldBe failedReplacement
      result(1).as[DeviceUpdateCompleted].right.value.result.code shouldBe ResultCode("1")
      result(2).as[EcuReplacement].right.value.asInstanceOf[EcuReplaced] shouldBe successfulReplacement
      result(3).as[DeviceUpdateCompleted].right.value.result.code shouldBe ResultCode("0")
    }
    fetchDeviceOk(deviceId).deviceStatus shouldBe DeviceStatus.Error
  }

  property("fails gracefully if trying to record ECU replacements for a non-existent or deleted device") {
    val deviceId = createDeviceOk(genDeviceT.generate)

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[Json]].total shouldBe 0
    }

    deleteDeviceListener(DeleteDeviceRequest(defaultNs, deviceId)).futureValue

    val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)
    val ecuReplaced = genEcuReplacement(deviceId, now, success = true).generate
    ecuReplacementListener(ecuReplaced).futureValue

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe NotFound
    }
  }

  property("can delete replaced devices") {
    getReportBlob(genDeviceUUID.generate) ~> route ~> check {
      status shouldBe NotFound
    }

    val deviceId = createDeviceOk(genDeviceT.generate)

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Json]]
      result.total shouldBe 0
    }

    val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)
    val ecuReplaced = genEcuReplacement(deviceId, now, success = true).generate
    ecuReplacementListener(ecuReplaced).futureValue

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe OK
      val result = responseAs[PaginationResult[Json]].values
      result.head.as[EcuReplacement].right.value.asInstanceOf[EcuReplaced] shouldBe ecuReplaced
    }

    val deleteDeviceRequest = DeleteDeviceRequest(defaultNs, deviceId)
    deleteDeviceListener(deleteDeviceRequest).futureValue

    getReportBlob(deviceId) ~> route ~> check {
      status shouldBe NotFound
    }
  }

  property("multiple ECU replacement error is handled gracefully") {
    val deviceId = createDeviceOk(genDeviceT.generate)
    val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)
    val ecuReplaced = genEcuReplacement(deviceId, now, success = true).generate
    ecuReplacementListener(ecuReplaced).futureValue
    ecuReplacementListener(ecuReplaced).futureValue
  }

  property("empty installation reports") {
    val deviceId = createDeviceOk(genDeviceT.generate)

    getInstallationReports(deviceId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceUpdateCompleted]].total shouldBe 0
    }
  }

  property("one installationReport") {
    val deviceId = createDeviceOk(genDeviceT.generate)
    val now = Instant.now.truncatedTo(ChronoUnit.SECONDS)

    val correlationId = genCorrelationId.generate
    val updateCompleted = genDeviceUpdateCompleted(correlationId, ResultCode("0"), deviceId, receivedAt = now.plusSeconds(10)).generate

    updateListener(updateCompleted).futureValue
    getInstallationReports(deviceId) ~> route ~> check {
      status shouldBe OK
      responseAs[PaginationResult[DeviceUpdateCompleted]].values.loneElement.result.code shouldBe ResultCode("0")
    }
  }
}
