/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import java.time.{Instant, OffsetDateTime}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.syntax.either._
import cats.syntax.show._
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, ResultCode}
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.http.ValidatedGenericMarshalling.validatedStringUnmarshaller
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId._
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, Event, EventType}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeleteDeviceRequest, DeviceEventMessage}
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.ota.deviceregistry.common.Errors
import com.advancedtelematic.ota.deviceregistry.common.Errors.MissingDevice
import com.advancedtelematic.ota.deviceregistry.data.Codecs._
import com.advancedtelematic.ota.deviceregistry.data.DataType.InstallationStatsLevel.InstallationStatsLevel
import com.advancedtelematic.ota.deviceregistry.data.DataType.{DeviceT, DeviceUuids, InstallationStatsLevel, RenameTagId, SearchParams, UpdateDevice, UpdateTagValue}
import com.advancedtelematic.ota.deviceregistry.data.Device.{ActiveDeviceCount, DeviceOemId}
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import com.advancedtelematic.ota.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.ota.deviceregistry.data.SortBy.SortBy
import com.advancedtelematic.ota.deviceregistry.data.TagId.validatedTagId
import com.advancedtelematic.ota.deviceregistry.data.{GroupExpression, PackageId, SortBy, TagId}
import com.advancedtelematic.ota.deviceregistry.db.DbOps.PaginationResultOps
import com.advancedtelematic.ota.deviceregistry.db._
import com.advancedtelematic.ota.deviceregistry.messages.DeviceCreated
import com.advancedtelematic.ota.deviceregistry.http.nonNegativeLong
import io.circe.Json
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

object DevicesResource {
  import akka.http.scaladsl.server.PathMatchers.Segment

  type EventPayload = (DeviceId, Instant) => Event

  private[DevicesResource] implicit val EventPayloadDecoder: io.circe.Decoder[EventPayload] =
    io.circe.Decoder.instance { c =>
      for {
        id         <- c.get[String]("id")
        deviceTime <- c.get[Instant]("deviceTime")(io.circe.Decoder.decodeInstant)
        eventType  <- c.get[EventType]("eventType")
        payload    <- c.get[Json]("event")
      } yield
        (deviceUuid: DeviceId, receivedAt: Instant) =>
          Event(deviceUuid, id, eventType, deviceTime, receivedAt, payload)
    }

  implicit val groupIdUnmarshaller: Unmarshaller[String, GroupId] = GroupId.unmarshaller

  implicit val resultCodeUnmarshaller: FromStringUnmarshaller[ResultCode] = Unmarshaller.strict(ResultCode)

  implicit val correlationIdUnmarshaller: FromStringUnmarshaller[CorrelationId] = Unmarshaller.strict {
    CorrelationId.fromString(_).leftMap(new IllegalArgumentException(_)).valueOr(throw _)
  }

  implicit val installationStatsLevelUnmarshaller: FromStringUnmarshaller[InstallationStatsLevel] =
    Unmarshaller.strict {
      _.toLowerCase match {
        case "device" => InstallationStatsLevel.Device
        case "ecu"    => InstallationStatsLevel.Ecu
        case s        => throw new IllegalArgumentException(s"Invalid value for installation stats level parameter: $s.")
      }
    }

  implicit val sortByUnmarshaller: FromStringUnmarshaller[SortBy] = Unmarshaller.strict {
    _.toLowerCase match {
      case "name"      => SortBy.Name
      case "createdat" => SortBy.CreatedAt
      case s           => throw new IllegalArgumentException(s"Invalid value for sorting parameter: '$s'.")
    }
  }

  val tagIdMatcher: PathMatcher1[TagId] = Segment.flatMap(TagId.from(_).toOption)
}

class DevicesResource(
    namespaceExtractor: Directive1[Namespace],
    messageBus: MessageBusPublisher,
    deviceNamespaceAuthorizer: Directive1[DeviceId]
)(implicit system: ActorSystem, db: Database, mat: Materializer, ec: ExecutionContext) {

  import DevicesResource._
  import Directives._
  import StatusCodes._
  import com.advancedtelematic.libats.http.AnyvalMarshallingSupport._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  val extractPackageId: Directive1[PackageId] =
    pathPrefix(Segment / Segment).as(PackageId.apply)

  val eventJournal = new EventJournal()

  def searchDevice(ns: Namespace): Route =
    parameters(
      'deviceId.as[DeviceOemId].?,
      'grouped.as[Boolean].?,
      'groupType.as[GroupType].?,
      'groupId.as[GroupId].?,
      'nameContains.as[String].?,
      'notSeenSinceHours.as[Int].?,
      'sortBy.as[SortBy].?,
      'offset.as(nonNegativeLong).?,
      'limit.as(nonNegativeLong).?).as(SearchParams.apply _) { params =>
        entity(as[DeviceUuids]) { p =>
          complete(db.run(DeviceRepository.search(ns, params, p.deviceUuids)))
        } ~
        complete(db.run(DeviceRepository.search(ns, params, Vector.empty)))
      }

  def createDevice(ns: Namespace, device: DeviceT): Route = {
    val f = db
      .run(DeviceRepository.create(ns, device))
      .andThen {
        case scala.util.Success(uuid) =>
          messageBus.publish(
            DeviceCreated(ns, uuid, device.deviceName, device.deviceId, device.deviceType, Instant.now())
          )
      }

    onSuccess(f) { uuid =>
      respondWithHeaders(List(Location(Uri("/devices/" + uuid.show)))) {
        complete(Created -> uuid)
      }
    }
  }

  def deleteDevice(ns: Namespace, uuid: DeviceId): Route = {
    val f = messageBus.publish(DeleteDeviceRequest(ns, uuid, Instant.now()))
    onSuccess(f) { complete(StatusCodes.Accepted) }
  }

  def fetchDevice(uuid: DeviceId): Route =
    complete(db.run(DeviceRepository.findByUuid(uuid)))

  def updateDevice(ns: Namespace, uuid: DeviceId, updateDevice: UpdateDevice): Route =
    complete(db.run(DeviceRepository.updateDeviceName(ns, uuid, updateDevice.deviceName)))

  def countDynamicGroupCandidates(ns: Namespace, expression: GroupExpression): Route =
    complete(db.run(DeviceRepository.countDevicesForExpression(ns, expression)))

  def getGroupsForDevice(uuid: DeviceId): Route =
    parameters('offset.as(nonNegativeLong).?, 'limit.as(nonNegativeLong).?) { (offset, limit) =>
      complete(db.run(GroupMemberRepository.listGroupsForDevice(uuid, offset, limit)))
    }

  def updateInstalledSoftware(device: DeviceId): Route =
    entity(as[Seq[PackageId]]) { installedSoftware =>
      val f = db.run(InstalledPackages.setInstalled(device, installedSoftware.toSet))
      onSuccess(f) { complete(StatusCodes.NoContent) }
    }

  def getDevicesCount(pkg: PackageId, ns: Namespace): Route =
    complete(db.run(InstalledPackages.getDevicesCount(pkg, ns)))

  def listPackagesOnDevice(device: DeviceId): Route =
    parameters('nameContains.as[String].?, 'offset.as(nonNegativeLong).?, 'limit.as(nonNegativeLong).?) { (nameContains, offset, limit) =>
      complete(db.run(InstalledPackages.installedOn(device, nameContains, offset, limit)))
    }

  implicit def offsetDateTimeUnmarshaller: FromStringUnmarshaller[OffsetDateTime] =
    Unmarshaller.strict(OffsetDateTime.parse)

  def getActiveDeviceCount(ns: Namespace): Route =
    parameters('start.as[OffsetDateTime], 'end.as[OffsetDateTime]) { (start, end) =>
      complete(
        db.run(DeviceRepository.countActivatedDevices(ns, start.toInstant, end.toInstant))
          .map(ActiveDeviceCount.apply)
      )
    }

  def getDistinctPackages(ns: Namespace): Route =
    parameters('offset.as(nonNegativeLong).?, 'limit.as(nonNegativeLong).?) { (offset, limit) =>
      complete(db.run(InstalledPackages.getInstalledForAllDevices(ns, offset, limit)))
    }

  def findAffected(ns: Namespace): Route =
    entity(as[Set[PackageId]]) { packageIds =>
      val f = InstalledPackages.allInstalledPackagesById(ns, packageIds).map {
        _.groupBy(_._1).mapValues(_.map(_._2).toSet)
      }
      complete(db.run(f))
    }

  def getPackageStats(ns: Namespace, name: PackageId.Name): Route =
    parameters('offset.as(nonNegativeLong).?, 'limit.as(nonNegativeLong).?) { (offset, limit) =>
      val f = db.run(InstalledPackages.listAllWithPackageByName(ns, name, offset, limit))
      complete(f)
    }

  def fetchInstallationHistory(deviceId: DeviceId, offset: Option[Long], limit: Option[Long]): Route =
    complete(db.run(EcuReplacementRepository.deviceHistory(deviceId, offset.orDefaultOffset, limit.orDefaultLimit)))

  def installationReports(deviceId: DeviceId, offset: Option[Long], limit: Option[Long]): Route =
    complete(db.run(InstallationReportRepository.installationReports(deviceId, offset.orDefaultOffset, limit.orDefaultLimit)))

  def fetchInstallationStats(correlationId: CorrelationId, reportLevel: Option[InstallationStatsLevel]): Route = {
    val action = reportLevel match {
      case Some(InstallationStatsLevel.Ecu) => InstallationReportRepository.installationStatsPerEcu(correlationId)
      case _                                => InstallationReportRepository.installationStatsPerDevice(correlationId)
    }
    complete(db.run(action))
  }

  private def fetchDeviceTags(ns: Namespace): Route =
    complete(db.run(TaggedDeviceRepository.fetchAll(ns)))

  private def fetchDeviceTags(deviceId: DeviceId): Route =
    complete(db.run(TaggedDeviceRepository.fetchForDevice(deviceId)))

  private def patchDeviceTagValue(namespace: Namespace, deviceId: DeviceId, tagId: TagId, tagValue: String) = {
    val f = db.run {
      for {
        _ <- TaggedDeviceRepository.updateDeviceTagValue(namespace, deviceId, tagId, tagValue)
        tags <- TaggedDeviceRepository.fetchForDevice(deviceId)
      } yield tags
    }
    complete(f)
  }

  private def renameDeviceTag(ns: Namespace, tagId: TagId, newTagId: TagId): Route = {
    val action = for {
      _ <- TaggedDeviceRepository.updateTagId(ns, tagId, newTagId)
      _ <- GroupInfoRepository.renameTagIdInExpression(ns, tagId, newTagId)
    } yield ()
    complete(db.run(action.transactionally))
  }

  private def deleteDeviceTag(namespace: Namespace, tagId: TagId) =
    complete(db.run(TaggedDeviceRepository.deleteTag(namespace, tagId)))

  private def tagDevicesFromCsv(ns: Namespace, byteSource: Source[ByteString, Any]): Route = {
    val deviceIdKey = "DeviceID"
    val csvRows = byteSource
      .via(CsvParsing.lineScanner(delimiter = CsvParsing.SemiColon))
      .via(CsvToMap.toMapAsStrings())
      .runWith(Sink.seq)
      .flatMap { rows =>
        if (rows.head.keys.exists(_ == deviceIdKey)) Future.successful(rows)
        else Future.failed(Errors.MalformedInputFile)
      }

    val f = csvRows.map(_.map { row =>
      val deviceId = DeviceOemId(row(deviceIdKey))
      val tags = row.collect { case (k, v) if k != deviceIdKey =>
        validatedTagId.from(k).map(_ -> v).valueOr(_ => throw Errors.MalformedInputFile)
      }
      TaggedDeviceRepository
        .tagDeviceByOemId(ns, deviceId, tags)
        .recover { case Failure(MissingDevice) => DBIO.successful(()) }
    })

    complete {
      for {
        dbios <- f
        action = DBIO.sequence(dbios).transactionally
        _ <- db.run(action)
      } yield NoContent
    }
  }

  def api: Route = namespaceExtractor { ns =>
    pathPrefix("devices") {
      (post & entity(as[DeviceT]) & pathEnd) { device =>
        createDevice(ns, device)
      } ~
      get {
        (path("count") & parameter('expression.as[GroupExpression].?)) {
          case None      => complete(Errors.InvalidGroupExpression(""))
          case Some(exp) => countDynamicGroupCandidates(ns, exp)
        } ~
        (path("stats") & parameters('correlationId.as[CorrelationId], 'reportLevel.as[InstallationStatsLevel].?)) {
          (cid, reportLevel) => fetchInstallationStats(cid, reportLevel)
        } ~
        pathEnd {
          searchDevice(ns)
        }
      } ~
      deviceNamespaceAuthorizer { uuid =>
        get {
          path("groups") {
            getGroupsForDevice(uuid)
          } ~
          path("packages") {
            listPackagesOnDevice(uuid)
          } ~
          path("active_device_count") {
            getActiveDeviceCount(ns)
          } ~
          (path("installation_reports") & parameters('offset.as(nonNegativeLong).?, 'limit.as(nonNegativeLong).?)) {
            (offset, limit) => installationReports(uuid, offset, limit)
          } ~
          (path("installation_history") & parameters('offset.as(nonNegativeLong).?, 'limit.as(nonNegativeLong).?)) {
            (offset, limit) => fetchInstallationHistory(uuid, offset, limit)
          } ~
          (pathPrefix("device_count") & extractPackageId) { pkg =>
            getDevicesCount(pkg, ns)
          } ~
          path("device_tags") {
            fetchDeviceTags(uuid)
          } ~
          pathEnd {
            fetchDevice(uuid)
          }
        } ~
        (put & pathEnd & entity(as[UpdateDevice])) { updateBody =>
          updateDevice(ns, uuid, updateBody)
        } ~
        (patch & path("device_tags") & entity(as[UpdateTagValue])) { utv =>
          patchDeviceTagValue(ns, uuid, utv.tagId, utv.tagValue)
        } ~
        (delete & pathEnd) {
          deleteDevice(ns, uuid)
        } ~
        path("events") {
          import DevicesResource.EventPayloadDecoder
          (get & parameter('correlationId.as[CorrelationId].?)) { correlationId =>
            // TODO: This should not return raw Events
            // https://saeljira.it.here.com/browse/OTA-4163
            // API should not return arbitrary json (`payload`) to the clients. This is why we index interesting events, so we can give this info to clients
            val events = eventJournal.getEvents(uuid, correlationId)
            complete(events)
          } ~
          (post & pathEnd) {
            extractLog { log =>
              entity(as[List[EventPayload]]) { xs =>
                val timestamp = Instant.now()
                val recordingResult: List[Future[Unit]] =
                  xs.map(_.apply(uuid, timestamp)).map(x => messageBus.publish(DeviceEventMessage(ns, x)))
                onComplete(Future.sequence(recordingResult)) {
                  case scala.util.Success(_) =>
                    complete(StatusCodes.NoContent)

                  case scala.util.Failure(t) =>
                    log.error(t, "Unable write events to log.")
                    complete(StatusCodes.ServiceUnavailable)
                }
              }
            }
          }
        }
      }
    } ~
    pathPrefix("device_tags") {
      (put & path(tagIdMatcher) & entity(as[RenameTagId])) { (tagId, body) =>
        renameDeviceTag(ns, tagId, body.tagId)
      } ~
      (delete & path(tagIdMatcher)) { tagId =>
        deleteDeviceTag(ns, tagId)
      } ~
      pathEnd {
        get {
          fetchDeviceTags(ns)
        } ~
        // TODO use extractRequestEntity instead of fileUpload
        (post & fileUpload("custom-device-fields")) { case (_, byteSource) =>
          tagDevicesFromCsv(ns, byteSource)
        }
      }
    } ~
    (get & pathPrefix("device_count") & extractPackageId) { pkg =>
      getDevicesCount(pkg, ns)
    } ~
    (get & path("active_device_count")) {
      getActiveDeviceCount(ns)
    }
  }

  def mydeviceRoutes: Route = namespaceExtractor { authedNs => // Don't use this as a namespace
    pathPrefix("mydevice" / DeviceId.Path) { uuid =>
      (get & pathEnd) {
        fetchDevice(uuid)
      } ~
      (put & path("packages")) {
        updateInstalledSoftware(uuid)
      }
    }
  }

  val devicePackagesRoutes: Route = namespaceExtractor { ns =>
    pathPrefix("device_packages") {
      (pathEnd & get) {
        getDistinctPackages(ns)
      } ~
      (path(Segment) & get) { name =>
        getPackageStats(ns, name)
      } ~
      (path("affected") & post) {
        findAffected(ns)
      }
    }
  }

  val route: Route = api ~ mydeviceRoutes ~ devicePackagesRoutes
}
