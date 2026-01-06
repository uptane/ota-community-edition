/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import java.time.Instant
import cats.syntax.show.*
import com.advancedtelematic.libats.data.DataType.CorrelationId
import com.advancedtelematic.libats.messaging_datatype.DataType.{
  DeviceId,
  EcuIdentifier,
  Event,
  EventType
}
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickUrnMapper.correlationIdMapper
import com.advancedtelematic.director.deviceregistry.data.DataType.IndexedEventType.IndexedEventType
import com.advancedtelematic.director.deviceregistry.data.DataType.{IndexedEvent, *}
import SlickMappings.*
import io.circe.Json
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*

import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libats.codecs.CirceRefined.*

object EventJournal {

  class EventJournalTable(tag: Tag) extends Table[Event](tag, "EventJournal") {
    def deviceUuid = column[DeviceId]("device_uuid")
    def eventId = column[String]("event_id")
    def deviceTime = column[Instant]("device_time")(javaInstantMapping)
    def eventTypeId = column[String]("event_type_id")
    def eventTypeVersion = column[Int]("event_type_version")
    def event = column[Json]("event")
    def receivedAt = column[Instant]("received_at")(javaInstantMapping)

    def pk = primaryKey("events_pk", (deviceUuid, eventId))

    private def toRow(e: Event) =
      Some(
        e.deviceUuid,
        e.eventId,
        e.eventType.id,
        e.eventType.version,
        e.deviceTime,
        e.receivedAt,
        e.payload
      )

    private def toEvent(x: (DeviceId, String, String, Int, Instant, Instant, Json)): Event = {
      val ecu = x._7.hcursor.downField("ecu").as[Option[EcuIdentifier]].toOption.flatten
      Event(x._1, x._2, EventType(x._3, x._4), x._5, x._6, ecu, x._7)
    }

    override def * =
      (
        deviceUuid,
        eventId,
        eventTypeId,
        eventTypeVersion,
        deviceTime,
        receivedAt,
        event
      ).shaped <> (toEvent, toRow)

  }

  protected[db] val events = TableQuery[EventJournalTable]

  class IndexedEventTable(tag: Tag) extends Table[IndexedEvent](tag, "IndexedEvents") {
    def deviceUuid = column[DeviceId]("device_uuid")
    def eventId = column[String]("event_id")
    def eventType = column[IndexedEventType]("event_type")
    def correlationId = column[Option[CorrelationId]]("correlation_id")
    def createdAt = column[Instant]("created_at")(javaInstantMapping)

    def pk = primaryKey("indexed_event_pk", (deviceUuid, eventId))

    def * = (deviceUuid, eventId, eventType, correlationId).shaped <> (
      IndexedEvent.tupled,
      IndexedEvent.unapply
    )

  }

  protected[db] val indexedEvents = TableQuery[IndexedEventTable]

  class IndexedEventArchiveTable(tag: Tag)
      extends Table[IndexedEvent](tag, "IndexedEventsArchive") {
    def deviceUuid = column[DeviceId]("device_uuid")
    def eventId = column[String]("event_id")
    def eventType = column[IndexedEventType]("event_type")
    def correlationId = column[Option[CorrelationId]]("correlation_id")

    def pk = primaryKey("indexed_event_pk", (deviceUuid, eventId))

    def * = (deviceUuid, eventId, eventType, correlationId).shaped <> (
      IndexedEvent.tupled,
      IndexedEvent.unapply
    )

  }

  protected[db] val indexedEventsArchive = TableQuery[IndexedEventArchiveTable]

  def deleteEvents(deviceUuid: DeviceId): DBIO[Int] =
    events.filter(_.deviceUuid === deviceUuid).delete

  def archiveIndexedEvents(deviceUuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Int] = {
    val q = indexedEvents.filter(_.deviceUuid === deviceUuid)
    val io = for {
      ies <- q.result
      _ <- indexedEventsArchive ++= ies
      n <- q.delete
    } yield n
    io.transactionally
  }

}

class EventJournal()(implicit db: Database, ec: ExecutionContext) {
  import EventJournal.{events, indexedEvents, indexedEventsArchive}

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def recordEvent(event: Event): Future[Unit] = {
    val indexDbio = EventIndex.index(event) match {
      case Left(err) =>
        _log.info(s"Could not index event ${event.show}: $err")
        DBIO.successful(())
      case Right(e) =>
        indexedEvents.insertOrUpdate(e)
    }

    val io = events.insertOrUpdate(event).andThen(indexDbio).transactionally

    db.run(io).map(_ => ())
  }

  def getEvents(deviceUuid: DeviceId, correlationId: Option[CorrelationId]): Future[Seq[Event]] =
    if (correlationId.isDefined)
      getIndexedEvents(deviceUuid, correlationId).map(_.map(_._1))
    else
      db.run(events.filter(_.deviceUuid === deviceUuid).result)

  def getIndexedEvents(deviceUuid: DeviceId,
                       correlationId: Option[CorrelationId]): Future[Seq[(Event, IndexedEvent)]] =
    db.run {
      EventJournal.events
        .filter(_.deviceUuid === deviceUuid)
        .join(EventJournal.indexedEvents.maybeFilter(_.correlationId === correlationId))
        .on { case (ej, ie) => ej.deviceUuid === ie.deviceUuid && ej.eventId === ie.eventId }
        .sortBy { case (ej, ie) => ej.deviceTime.desc -> ie.createdAt.desc }
        .result
    }

  protected[db] def getArchivedIndexedEvents(deviceUuid: DeviceId): Future[Seq[IndexedEvent]] =
    db.run(indexedEventsArchive.filter(_.deviceUuid === deviceUuid).result)

}
