package com.advancedtelematic.director.db.deviceregistry

import cats.syntax.option.*
import com.advancedtelematic.director.deviceregistry.data.DataType.{IndexedEvent, IndexedEventType}
import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*
import com.advancedtelematic.director.util.DirectorSpec
import com.advancedtelematic.libats.data.DataType.{
  CorrelationId,
  MultiTargetUpdateCorrelationId,
  TargetSpecCorrelationId
}
import com.advancedtelematic.libats.messaging_datatype.DataType.{
  DeviceId,
  Event,
  EventType,
  ValidEcuIdentifier
}
import eu.timepit.refined.refineMV
import io.circe.Json
import io.circe.syntax.*
import org.scalacheck.Gen
import org.scalatest.EitherValues.*

import java.time.Instant
import java.util.UUID
import com.advancedtelematic.director.data.Generators.GenEcuIdentifier

class EventIndexSpec extends DirectorSpec {

  val genCorrelationId: Gen[CorrelationId] =
    Gen.uuid.flatMap(uuid =>
      Gen.oneOf(TargetSpecCorrelationId(uuid), MultiTargetUpdateCorrelationId(uuid))
    )

  val eventGen: Gen[Event] = for {
    device <- Gen.uuid.map(DeviceId.apply)
    eventId <- Gen.uuid.map(_.toString)
    eventType = EventType("", 0)
    ecu <- Gen.option(GenEcuIdentifier)
    json = Json.obj()
  } yield Event(
    device,
    eventId,
    eventType,
    Instant.now,
    Instant.now,
    ecu,
    json
  )

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
      EventType("DeviceResumed", 0) -> IndexedEventType.DeviceResumed
    )

    eventTypeMap.foreach { case (eventType, indexedEventType) =>
      val (event, correlationId) = eventWithCorrelationIdGen(eventType).generate
      val indexedEvent = EventIndex.index(event).value
      indexedEvent shouldBe IndexedEvent(
        event.deviceUuid,
        event.eventId,
        indexedEventType,
        correlationId.some
      )
    }
  }

  test("indexes a DownloadComplete event by type") {
    val event = downloadCompleteEventGen.generate

    val indexedEvent = EventIndex.index(event).value

    indexedEvent shouldBe IndexedEvent(
      event.deviceUuid,
      event.eventId,
      IndexedEventType.DownloadComplete,
      None
    )
  }

  test("does not index event if it cannot be parsed") {
    val event = eventGen.map(_.copy(eventType = EventType("UnknownEvent", 20))).generate

    val indexedEvent = EventIndex.index(event).left.value

    indexedEvent shouldBe "Unknown event type EventType(UnknownEvent,20)"
  }

}
