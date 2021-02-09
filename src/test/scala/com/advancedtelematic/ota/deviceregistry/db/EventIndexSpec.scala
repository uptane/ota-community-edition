package com.advancedtelematic.ota.deviceregistry.db

import java.time.Instant

import cats.syntax.option._
import com.advancedtelematic.libats.data.DataType.{CampaignId, CorrelationId, MultiTargetUpdateId}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, Event, EventType}
import com.advancedtelematic.ota.deviceregistry.DatabaseSpec
import com.advancedtelematic.ota.deviceregistry.data.DataType.{IndexedEvent, IndexedEventType}
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps._
import io.circe.Json
import io.circe.syntax._
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, FunSuite, Matchers}
import java.util.UUID

class EventIndexSpec extends FunSuite with ScalaFutures with DatabaseSpec with Matchers with EitherValues {

  val genCorrelationId: Gen[CorrelationId] =
    Gen.uuid.flatMap(uuid => Gen.oneOf(CampaignId(uuid), MultiTargetUpdateId(uuid)))
  val eventGen: Gen[Event] = for {
    device <- Gen.uuid.map(DeviceId.apply)
    eventId <- Gen.uuid.map(_.toString)
    eventType = EventType("", 0)
    json = Json.obj()
  } yield Event(device, eventId, eventType, Instant.now, Instant.now, json)

  val downloadCompleteEventGen: Gen[Event] =
    eventGen.map(_.copy(eventType = EventType("DownloadComplete", 0)))

  def eventWithCorrelationIdGen(eventType: EventType): Gen[(Event, CorrelationId)] = for {
    event <- eventGen
    correlationId <- genCorrelationId
    json = Json.obj("correlationId" -> correlationId.asJson)
  } yield (event.copy(eventType = eventType, payload = json), correlationId)

  def eventWithCampaignIdGen(eventType: EventType): Gen[(Event, UUID)] = for {
    event <- eventGen
    campaignId <- Gen.uuid
    json = Json.obj("campaignId" -> campaignId.asJson)
  } yield (event.copy(eventType = eventType, payload = json), campaignId)

  test("indexes an event with correlation ID by type") {
    val eventTypeMap = Map(
      EventType("InstallationComplete", 0) -> IndexedEventType.InstallationComplete,
      EventType("EcuDownloadStarted", 0) -> IndexedEventType.EcuDownloadStarted,
      EventType("EcuDownloadCompleted", 0) -> IndexedEventType.EcuDownloadCompleted,
      EventType("EcuInstallationStarted", 0) -> IndexedEventType.EcuInstallationStarted,
      EventType("EcuInstallationApplied", 0) -> IndexedEventType.EcuInstallationApplied,
      EventType("EcuInstallationCompleted", 0) -> IndexedEventType.EcuInstallationCompleted,
      EventType("DevicePaused", 0) -> IndexedEventType.DevicePaused,
      EventType("DeviceResumed", 0) -> IndexedEventType.DeviceResumed,
    )

    eventTypeMap.foreach { case (eventType, indexedEventType) =>
      val (event, correlationId) = eventWithCorrelationIdGen(eventType).generate
      val indexedEvent = EventIndex.index(event).right.value
      indexedEvent shouldBe IndexedEvent(event.deviceUuid, event.eventId, indexedEventType, correlationId.some)
    }
  }

  test("indexes an event with campaign ID by type") {
    val eventTypeMap = Map(
      EventType("campaign_accepted", 0) -> IndexedEventType.CampaignAccepted,
      EventType("campaign_declined", 0) -> IndexedEventType.CampaignDeclined,
      EventType("campaign_postponed", 0) -> IndexedEventType.CampaignPostponed,
    )
    eventTypeMap.foreach { case (eventType, indexedEventType) =>
      val (event, campaignId) = eventWithCampaignIdGen(eventType).generate
      val correlationId = CampaignId(campaignId)
      val indexedEvent = EventIndex.index(event).right.value
      indexedEvent shouldBe IndexedEvent(event.deviceUuid, event.eventId, indexedEventType, correlationId.some)
    }
  }

  test("indexes a DownloadComplete event by type") {
    val event = downloadCompleteEventGen.generate

    val indexedEvent = EventIndex.index(event).right.value

    indexedEvent shouldBe IndexedEvent(event.deviceUuid, event.eventId, IndexedEventType.DownloadComplete, None)
  }

  test("does not index event if it cannot be parsed") {
    val event = eventGen.map(_.copy(eventType = EventType("UnknownEvent", 20))).generate

    val indexedEvent = EventIndex.index(event).left.value

    indexedEvent shouldBe "Unknown event type EventType(UnknownEvent,20)"
  }
}
