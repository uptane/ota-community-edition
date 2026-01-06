/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.*
import akka.http.scaladsl.server.*
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.syntax.either.*
import cats.syntax.show.*
import com.advancedtelematic.director.db.deviceregistry.*
import com.advancedtelematic.director.db.deviceregistry.DbOps.PaginationResultOps
import com.advancedtelematic.director.deviceregistry.data.*
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.DataType.InstallationStatsLevel.InstallationStatsLevel
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceCountParams,
  DeviceT,
  DevicesQuery,
  InstallationStatsLevel,
  RenameTagId,
  SearchParams,
  SetDevice,
  UpdateDevice,
  UpdateHibernationStatusRequest,
  UpdateTagValue
}
import com.advancedtelematic.director.deviceregistry.data.Device.{ActiveDeviceCount, DeviceOemId}
import com.advancedtelematic.director.deviceregistry.data.DeviceSortBy.DeviceSortBy
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupSortBy.GroupSortBy
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.director.deviceregistry.data.SortDirection.SortDirection
import com.advancedtelematic.director.deviceregistry.data.TagId.validatedTagId
import com.advancedtelematic.director.deviceregistry.messages.DeviceCreated
import com.advancedtelematic.director.http.deviceregistry.Errors.{Codes, MissingDevice}
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, ResultCode}
import com.advancedtelematic.libats.http.Errors.JsonError
import com.advancedtelematic.libats.http.RefinedMarshallingSupport.*
import com.advancedtelematic.libats.http.UUIDKeyAkka.*
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId.*
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, Event, EventType}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.*
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeleteDeviceRequest,
  DeviceEventMessage
}
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import io.circe.Json
import io.circe.syntax.EncoderOps
import slick.jdbc.MySQLProfile.api.*
import Unmarshallers.nonNegativeLong
import com.advancedtelematic.director.db.DeleteDeviceDBIO

import java.time.{Instant, OffsetDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

object DevicesResource {
  import akka.http.scaladsl.server.PathMatchers.Segment

  type EventPayload = (DeviceId, Instant) => Event

  private[DevicesResource] implicit val EventPayloadDecoder: io.circe.Decoder[EventPayload] =
    io.circe.Decoder.instance { c =>
      for {
        id <- c.get[String]("id")
        deviceTime <- c.get[Instant]("deviceTime")(io.circe.Decoder.decodeInstant)
        eventType <- c.get[EventType]("eventType")
        payload <- c.get[Json]("event")
      } yield (deviceUuid: DeviceId, receivedAt: Instant) =>
        Event(deviceUuid, id, eventType, deviceTime, receivedAt, payload)
    }

  implicit val groupIdUnmarshaller: Unmarshaller[String, GroupId] = GroupId.unmarshaller

  implicit val resultCodeUnmarshaller: FromStringUnmarshaller[ResultCode] =
    Unmarshaller.strict(ResultCode.apply)

  implicit val correlationIdUnmarshaller: FromStringUnmarshaller[CorrelationId] =
    Unmarshaller.strict {
      CorrelationId.fromString(_).leftMap(new IllegalArgumentException(_)).valueOr(throw _)
    }

  implicit val installationStatsLevelUnmarshaller: FromStringUnmarshaller[InstallationStatsLevel] =
    Unmarshaller.strict {
      _.toLowerCase match {
        case "device" => InstallationStatsLevel.Device
        case "ecu"    => InstallationStatsLevel.Ecu
        case s        =>
          throw new IllegalArgumentException(
            s"Invalid value for installation stats level parameter: $s."
          )
      }
    }

  implicit val deviceSortByUnmarshaller: FromStringUnmarshaller[DeviceSortBy] =
    Unmarshaller.strict {
      _.toLowerCase match {
        case "name"        => DeviceSortBy.Name
        case "createdat"   => DeviceSortBy.CreatedAt
        case "deviceid"    => DeviceSortBy.DeviceId
        case "uuid"        => DeviceSortBy.Uuid
        case "activatedat" => DeviceSortBy.ActivatedAt
        case "lastseen"    => DeviceSortBy.LastSeen
        case s => throw new IllegalArgumentException(s"Invalid value for sorting parameter: '$s'.")
      }
    }

  implicit val groupSortByUnmarshaller: FromStringUnmarshaller[GroupSortBy] = Unmarshaller.strict {
    _.toLowerCase match {
      case "name"      => GroupSortBy.Name
      case "createdat" => GroupSortBy.CreatedAt
      case s => throw new IllegalArgumentException(s"Invalid value for sorting parameter: '$s'.")
    }
  }

  implicit val sortDirectionUnmarshaller: FromStringUnmarshaller[SortDirection] =
    Unmarshaller.strict {
      _.toLowerCase match {
        case "asc"  => SortDirection.Asc
        case "desc" => SortDirection.Desc
        case s => throw new IllegalArgumentException(s"Invalid value for sorting direction: '$s'.")
      }
    }

  val tagIdMatcher: PathMatcher1[TagId] = Segment.flatMap(TagId.from(_).toOption)
}

class DevicesResource(namespaceExtractor: Directive1[Namespace],
                      messageBus: MessageBusPublisher,
                      deviceNamespaceAuthorizer: Directive1[DeviceId])(
  implicit db: Database,
  mat: Materializer,
  ec: ExecutionContext) {

  import DevicesResource.*
  import Directives.*
  import StatusCodes.*
  import com.advancedtelematic.libats.http.AnyvalMarshallingSupport.*
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*

  val extractPackageId: Directive1[PackageId] =
    pathPrefix(Segment / Segment).as(PackageId.apply)

  val eventJournal = new EventJournal()

  val countParameters: Directive1[DeviceCountParams] =
    parameters(Symbol("recentSinceSecs").as[Long].?, Symbol("offlineSinceSecs").as[Long].?).tmap {
      case (recentSince, offlineSince) =>
        DeviceCountParams(
          recentSince.map(secs => Duration(secs, TimeUnit.SECONDS)),
          offlineSince.map(secs => Duration(secs, TimeUnit.SECONDS))
        )
    }

  def countDevices(ns: Namespace, params: DeviceCountParams): Route =
    complete(db.run(SearchDBIO.countByStatus(ns, params)))

  def searchDevice(ns: Namespace): Route =
    parameters(
      Symbol("deviceId").as[DeviceOemId].?,
      Symbol("grouped").as[Boolean].?,
      Symbol("groupType").as[GroupType].?,
      Symbol("groupId").as[GroupId].?,
      Symbol("nameContains").as[String].?,
      Symbol("notSeenSinceHours").as[Int].?,
      Symbol("hibernated").as[Boolean].?,
      Symbol("status").as[DeviceStatus].?,
      Symbol("activatedAfter").as[Instant].?,
      Symbol("activatedBefore").as[Instant].?,
      Symbol("lastSeenStart").as[Instant].?,
      Symbol("lastSeenEnd").as[Instant].?,
      Symbol("createdAtStart").as[Instant].?,
      Symbol("createdAtEnd").as[Instant].?,
      Symbol("hardwareIds").as(CsvSeq[HardwareIdentifier]).?,
      Symbol("sortBy").as[DeviceSortBy].?,
      Symbol("sortDirection").as[SortDirection].?,
      Symbol("offset").as(nonNegativeLong).?,
      Symbol("limit").as(nonNegativeLong).?
    ).as(
      (oemId,
       grouped,
       groupType,
       groupId,
       nameContains,
       notSeenSinceHours,
       hibernated,
       status,
       activatedAfter,
       activatedBefore,
       lastSeenStart,
       lastSeenEnd,
       createdAtStart,
       createdAtEnd,
       hardwareId: Option[Seq[HardwareIdentifier]],
       sortBy,
       sortDirection,
       offset,
       limit) =>
        SearchParams.apply(
          oemId,
          grouped,
          groupType,
          groupId,
          nameContains,
          notSeenSinceHours,
          hibernated,
          status,
          activatedAfter,
          activatedBefore,
          lastSeenStart,
          lastSeenEnd,
          createdAtStart,
          createdAtEnd,
          hardwareId.getOrElse(Seq.empty),
          sortBy,
          sortDirection,
          offset,
          limit
        )
    ) { params =>
      complete(for {
        pr <- db.run(SearchDBIO.search(ns, params))
        taggedDevicesMap <- db.run(
          TaggedDeviceRepository.fetchForDevices(pr.values.map(_.uuid)).map { deviceTags =>
            deviceTags.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
          }
        )
      } yield pr.copy(values = pr.values.map { device =>
        DeviceDB.toDevice(device, taggedDevicesMap.getOrElse(device.uuid, Seq.empty).toMap)
      }))

    }

  def createDevice(ns: Namespace, device: DeviceT): Route = {
    val f = db
      .run(DeviceRepository.create(ns, device))
      .andThen { case scala.util.Success(uuid) =>
        messageBus.publish(
          DeviceCreated(
            ns,
            uuid,
            device.deviceName,
            device.deviceId,
            device.deviceType,
            Instant.now()
          )
        )
      }

    onSuccess(f) { uuid =>
      respondWithHeaders(List(Location(Uri("/devices/" + uuid.show)))) {
        complete(Created -> uuid)
      }
    }
  }

  def deleteDevice(ns: Namespace, uuid: DeviceId): Route = {
    val f = for {
      _ <- db.run(DeleteDeviceDBIO.deleteDeviceIO(ns, uuid))
      _ <- messageBus.publishSafe(DeleteDeviceRequest(ns, uuid, Instant.now()))
    } yield StatusCodes.Accepted

    complete(f)
  }

  def fetchDevice(uuid: DeviceId): Route =
    complete(db.run(DeviceRepository.findByUuid(uuid)))

  def setDevice(ns: Namespace, uuid: DeviceId, updateDevice: SetDevice): Route =
    complete(
      db.run(DeviceRepository.setDevice(ns, uuid, updateDevice.deviceName, updateDevice.notes))
    )

  def updateDevice(ns: Namespace, uuid: DeviceId, updateDevice: UpdateDevice): Route =
    complete(
      db.run(DeviceRepository.updateDevice(ns, uuid, updateDevice.deviceName, updateDevice.notes))
    )

  def queryDevices(ns: Namespace, query: DevicesQuery): Future[List[Device]] = {
    val oemDevices = query.oemIds.getOrElse(List.empty[DeviceOemId])
    val uuidDevices = query.deviceUuids.getOrElse(List.empty[DeviceId])
    for {
      foundOemDevices <- db.run(DeviceRepository.findByOemIds(ns, oemDevices))
      foundUuidDevices <- db.run(DeviceRepository.findByDeviceUuids(ns, uuidDevices))
    } yield {
      val foundDevices = (foundOemDevices ++ foundUuidDevices).toSet.toList
      val missingOemDevices =
        oemDevices.filterNot(expected => foundOemDevices.map(_.deviceId).contains(expected))
      val missingUuidDevices =
        uuidDevices.filterNot(expected => foundUuidDevices.map(_.uuid).contains(expected))
      if (missingOemDevices.nonEmpty || missingUuidDevices.nonEmpty) {
        val msg = Map(
          "missingOemIds" -> missingOemDevices.map(_.underlying).mkString(","),
          "missingDeviceUuids" -> missingUuidDevices.map(_.uuid).mkString(",")
        )
        throw JsonError(Codes.MissingDevice, StatusCodes.NotFound, msg.asJson, "Devices not found")
      }
      foundDevices.map(DeviceDB.toDevice(_))
    }
  }

  def countDynamicGroupCandidates(ns: Namespace, expression: GroupExpression): Route =
    complete(db.run(SearchDBIO.countDevicesForExpression(ns, expression)))

  def getGroupsForDevice(uuid: DeviceId): Route =
    parameters(Symbol("offset").as(nonNegativeLong).?, Symbol("limit").as(nonNegativeLong).?) {
      (offset, limit) =>
        complete(db.run(GroupMemberRepository.listGroupsForDevice(uuid, offset, limit)))
    }

  def updateInstalledSoftware(device: DeviceId): Route =
    entity(as[Seq[PackageId]]) { installedSoftware =>
      val f = db.run(InstalledPackages.setInstalled(device, installedSoftware.toSet))
      onSuccess(f)(complete(StatusCodes.NoContent))
    }

  def getDevicesCount(pkg: PackageId, ns: Namespace): Route =
    complete(db.run(InstalledPackages.getDevicesCount(pkg, ns)))

  def listPackagesOnDevice(device: DeviceId): Route =
    parameters(
      Symbol("nameContains").as[String].?,
      Symbol("offset").as(nonNegativeLong).?,
      Symbol("limit").as(nonNegativeLong).?
    ) { (nameContains, offset, limit) =>
      complete(db.run(InstalledPackages.installedOn(device, nameContains, offset, limit)))
    }

  implicit def offsetDateTimeUnmarshaller: FromStringUnmarshaller[OffsetDateTime] =
    Unmarshaller.strict(OffsetDateTime.parse)

  implicit def instantUnmarshaller: FromStringUnmarshaller[Instant] =
    Unmarshaller.strict(Instant.parse)

  def getActiveDeviceCount(ns: Namespace): Route =
    parameters(Symbol("start").as[OffsetDateTime], Symbol("end").as[OffsetDateTime]) {
      (start, end) =>
        complete(
          db.run(DeviceRepository.countActivatedDevices(ns, start.toInstant, end.toInstant))
            .map(ActiveDeviceCount.apply)
        )
    }

  def getDistinctPackages(ns: Namespace): Route =
    parameters(Symbol("offset").as(nonNegativeLong).?, Symbol("limit").as(nonNegativeLong).?) {
      (offset, limit) =>
        complete(db.run(InstalledPackages.getInstalledForAllDevices(ns, offset, limit)))
    }

  def findAffected(ns: Namespace): Route =
    entity(as[Set[PackageId]]) { packageIds =>
      val f = InstalledPackages.allInstalledPackagesById(ns, packageIds).map {
        _.groupBy(_._1).view.mapValues(_.map(_._2).toSet).toMap
      }
      complete(db.run(f))
    }

  def getPackageStats(ns: Namespace, name: PackageId.Name): Route =
    parameters(Symbol("offset").as(nonNegativeLong).?, Symbol("limit").as(nonNegativeLong).?) {
      (offset, limit) =>
        val f = db.run(InstalledPackages.listAllWithPackageByName(ns, name, offset, limit))
        complete(f)
    }

  def fetchInstallationHistory(deviceId: DeviceId,
                               offset: Option[Long],
                               limit: Option[Long]): Route =
    complete(
      db.run(
        EcuReplacementRepository
          .deviceHistory(deviceId, offset.orDefaultOffset, limit.orDefaultLimit)
      )
    )

  def installationReports(deviceId: DeviceId, offset: Option[Long], limit: Option[Long]): Route =
    complete(
      db.run(
        InstallationReportRepository
          .installationReports(deviceId, offset.orDefaultOffset, limit.orDefaultLimit)
      )
    )

  def fetchInstallationStats(correlationId: CorrelationId,
                             reportLevel: Option[InstallationStatsLevel]): Route = {
    val action = reportLevel match {
      case Some(InstallationStatsLevel.Ecu) =>
        InstallationReportRepository.installationStatsPerEcu(correlationId)
      case _ => InstallationReportRepository.installationStatsPerDevice(correlationId)
    }
    complete(db.run(action))
  }

  private def fetchDeviceTags(ns: Namespace): Route =
    complete(db.run(TaggedDeviceRepository.fetchAll(ns)))

  private def fetchDeviceTags(deviceId: DeviceId): Route =
    complete(db.run(TaggedDeviceRepository.fetchForDevice(deviceId)))

  private def patchDeviceTagValue(namespace: Namespace,
                                  deviceId: DeviceId,
                                  tagId: TagId,
                                  tagValue: String) = {
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
      val tags = row.collect {
        case (k, v) if k != deviceIdKey =>
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

  private def updateHibernationStatus(ns: Namespace, uuid: DeviceId): Route =
    post {
      entity(as[UpdateHibernationStatusRequest]) { req =>
        val f = db.run(DeviceRepository.setHibernationStatus(ns, uuid, req.status))
        complete(f.map(_ => StatusCodes.OK))
      }
    }

  def api: Route = namespaceExtractor { ns =>
    pathPrefix("devices") {
      (post & pathEnd & entity(as[DeviceT])) { device =>
        createDevice(ns, device)
      } ~
        get {
          (path("count") & countParameters) { params =>
            countDevices(ns, params)
          } ~
            (path("dynamic-group-count") & parameter(Symbol("expression").as[GroupExpression].?)) {
              case None      => complete(Errors.InvalidGroupExpression(""))
              case Some(exp) => countDynamicGroupCandidates(ns, exp)
            } ~
            (path("stats") & parameters(
              Symbol("correlationId").as[CorrelationId],
              Symbol("reportLevel").as[InstallationStatsLevel].?
            )) { (cid, reportLevel) =>
              fetchInstallationStats(cid, reportLevel)
            } ~
            (pathEnd & entity(as[DevicesQuery])) { devicesQuery =>
              complete(queryDevices(ns, devicesQuery))
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
              (path("installation_reports") & parameters(
                Symbol("offset").as(nonNegativeLong).?,
                Symbol("limit").as(nonNegativeLong).?
              )) { (offset, limit) =>
                installationReports(uuid, offset, limit)
              } ~
              (path("installation_history") & parameters(
                Symbol("offset").as(nonNegativeLong).?,
                Symbol("limit").as(nonNegativeLong).?
              )) { (offset, limit) =>
                fetchInstallationHistory(uuid, offset, limit)
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
            path("hibernation") {
              updateHibernationStatus(ns, uuid)
            } ~
            (put & pathEnd & entity(as[SetDevice])) { setBody =>
              setDevice(ns, uuid, setBody)
            } ~
            (patch & pathEnd & entity(as[UpdateDevice])) { updateBody =>
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
              (get & parameter(Symbol("correlationId").as[CorrelationId].?)) { correlationId =>
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
                        xs.map(_.apply(uuid, timestamp))
                          .map(x => messageBus.publish(DeviceEventMessage(ns, x)))
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

  def mydeviceRoutes: Route = namespaceExtractor { _ => // Don't use this as a namespace
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
