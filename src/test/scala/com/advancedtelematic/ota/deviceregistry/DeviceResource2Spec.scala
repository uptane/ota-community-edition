package com.advancedtelematic.ota.deviceregistry

import java.time.Instant
import java.util.UUID
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.libats.messaging_datatype.DataType.{Event, EventType}
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceEventMessage
import com.advancedtelematic.ota.deviceregistry.daemon.DeviceEventListener
import com.advancedtelematic.ota.deviceregistry.data.DataType.IndexedEventType
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.CampaignId
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.ota.deviceregistry.DeviceResource2.ApiDeviceEvents
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.testing.ArbitraryInstances
import cats.syntax.either._
import org.scalatest.OptionValues._
import org.scalatest.EitherValues._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Millis, Seconds, Span}


class DeviceResource2Spec extends AnyFunSuite with ResourceSpec with Eventually with ScalaFutures with ArbitraryInstances {
  import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps._
  import io.circe.syntax._

  private val deviceEventListener = new DeviceEventListener()

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(15, Millis))

  test("events includes events for a device") {
    val device = genDeviceT.retryUntil(_.uuid.isDefined).generate
    val deviceId = createDeviceOk(device)
    val ecuId = EcuIdentifier.from("somefakeid").valueOr(throw _)
    val campaignIdUuid = UUID.randomUUID()
    val campaignId = CampaignId(campaignIdUuid)
    val now = Instant.now()

    val payload = Map(
      "ecu" -> ecuId.asJson,
      "correlationId" -> campaignId.toString().asJson,
      "campaignId" -> campaignIdUuid.toString.asJson
    ).asJson

    val event01 = Event(deviceId, UUID.randomUUID().toString, EventType("campaign_accepted", 0), now, now, payload)
    deviceEventListener.apply(DeviceEventMessage(defaultNs, event01)).futureValue

    val event02 = Event(deviceId, UUID.randomUUID().toString, EventType("EcuInstallationStarted", 0), now.plusMillis(1), now.plusMillis(1), payload)
    deviceEventListener.apply(DeviceEventMessage(defaultNs, event02)).futureValue

    val event03 = Event(deviceId, UUID.randomUUID().toString, EventType("EcuInstallationCompleted", 0), now.plusMillis(2), now.plusMillis(2), payload)
    deviceEventListener.apply(DeviceEventMessage(defaultNs, event03)).futureValue

    getEventsV2(deviceId) ~> route ~> check {
      status shouldBe StatusCodes.OK
      val updateStatus = responseAs[ApiDeviceEvents]

      updateStatus.deviceUuid shouldBe deviceId

      val events = updateStatus.events

      events should have size(3)

      events.headOption.value.updateId.value shouldBe campaignId
      events.headOption.value.ecuId.value shouldBe ecuId
      events.map(_.name) shouldBe Vector(IndexedEventType.EcuInstallationCompleted, IndexedEventType.EcuInstallationStarted, IndexedEventType.CampaignAccepted)
    }
  }

  test("returns events filtered by updateId") {
    val device = genDeviceT.retryUntil(_.uuid.isDefined).generate
    val deviceId = createDeviceOk(device)
    val ecuId = EcuIdentifier.from("somefakeid").valueOr(throw _)
    val now = Instant.now()

    val campaignId01 = CampaignId(UUID.randomUUID())
    val campaignId02 = CampaignId(UUID.randomUUID())

    val payload01 = Map(
      "ecu" -> ecuId.asJson,
      "correlationId" -> campaignId01.toString().asJson
    ).asJson

    val payload02 = Map(
      "ecu" -> ecuId.asJson,
      "correlationId" -> campaignId02.toString().asJson
    ).asJson

    val event01 = Event(deviceId, UUID.randomUUID().toString, EventType("EcuInstallationStarted", 0), now, now, payload01)
    deviceEventListener.apply(DeviceEventMessage(defaultNs, event01)).futureValue

    val event02 = Event(deviceId, UUID.randomUUID().toString, EventType("EcuInstallationCompleted", 0), now, now, payload02)
    deviceEventListener.apply(DeviceEventMessage(defaultNs, event02)).futureValue

    getEventsV2(deviceId, Some(campaignId01)) ~> route ~> check {
      status shouldBe StatusCodes.OK
      val updateStatus = responseAs[ApiDeviceEvents]

      updateStatus.deviceUuid shouldBe deviceId

      val events = updateStatus.events

      events should have size(1)

      events.headOption.value.updateId.value shouldBe campaignId01
      events.map(_.name).headOption.value shouldBe IndexedEventType.EcuInstallationStarted
    }
  }

}
