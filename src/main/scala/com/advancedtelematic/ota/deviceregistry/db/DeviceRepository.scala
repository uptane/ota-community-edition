/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.db

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import com.advancedtelematic.ota.deviceregistry.common.Errors
import com.advancedtelematic.ota.deviceregistry.data.DataType.{DeletedDevice, DeviceT, SearchParams, TaggedDevice}
import com.advancedtelematic.ota.deviceregistry.data.Device._
import com.advancedtelematic.ota.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import com.advancedtelematic.ota.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.ota.deviceregistry.data._
import com.advancedtelematic.ota.deviceregistry.db.DbOps.{PaginationResultOps, sortBySlickOrderedDeviceConversion}
import com.advancedtelematic.ota.deviceregistry.db.GroupInfoRepository.groupInfos
import com.advancedtelematic.ota.deviceregistry.db.GroupMemberRepository.groupMembers
import com.advancedtelematic.ota.deviceregistry.db.SlickMappings._
import com.advancedtelematic.ota.deviceregistry.db.TaggedDeviceRepository.{TaggedDeviceTable, taggedDevices}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

object DeviceRepository {

  private[this] implicit val DeviceStatusColumnType =
    MappedColumnType.base[DeviceStatus.Value, String](_.toString, DeviceStatus.withName)

  // scalastyle:off
  class DeviceTable(tag: Tag) extends Table[Device](tag, "Device") {
    def namespace    = column[Namespace]("namespace")
    def uuid         = column[DeviceId]("uuid")
    def deviceName   = column[DeviceName]("device_name")
    def deviceId     = column[DeviceOemId]("device_id")
    def rawId        = column[String]("device_id")
    def deviceType   = column[DeviceType]("device_type")
    def lastSeen     = column[Option[Instant]]("last_seen")
    def createdAt    = column[Instant]("created_at")
    def activatedAt  = column[Option[Instant]]("activated_at")
    def deviceStatus = column[DeviceStatus]("device_status")

    def * =
      (namespace, uuid, deviceName, deviceId, deviceType, lastSeen, createdAt, activatedAt, deviceStatus).shaped <> ((Device.apply _).tupled, Device.unapply)

    def pk = primaryKey("uuid", uuid)
  }

  // scalastyle:on
  val devices = TableQuery[DeviceTable]

  class DeletedDeviceTable(tag: Tag) extends Table[DeletedDevice](tag, "DeletedDevice") {
    def namespace = column[Namespace]("namespace")
    def uuid = column[DeviceId]("device_uuid")
    def deviceId = column[DeviceOemId]("device_id")

    def * =
      (namespace, uuid, deviceId).shaped <>
      ((DeletedDevice.apply _).tupled, DeletedDevice.unapply)

    def pk = primaryKey("pk_deleted_device", (namespace, uuid, deviceId))
  }

  val deletedDevices = TableQuery[DeletedDeviceTable]

  def create(ns: Namespace, device: DeviceT)(implicit ec: ExecutionContext): DBIO[DeviceId] = {
    val uuid = device.uuid.getOrElse(DeviceId.generate)

    val dbDevice = Device(ns,
      uuid,
      device.deviceName,
      device.deviceId,
      device.deviceType,
      createdAt = Instant.now())

    val dbIO = devices += dbDevice
    dbIO
      .handleIntegrityErrors(Errors.ConflictingDevice)
      .andThen { GroupMemberRepository.addDeviceToDynamicGroups(ns, dbDevice, Map.empty) }
      .map(_ => uuid)
      .transactionally
  }

  def findUuidFromUniqueDeviceIdOrCreate(ns: Namespace, deviceId: DeviceOemId, devT: DeviceT)(
      implicit ec: ExecutionContext
  ): DBIO[(Boolean, DeviceId)] =
    for {
      devs <- findByDeviceIdQuery(ns, deviceId).result
      (created, uuid) <- devs match {
        case Seq()  => create(ns, devT).map((true, _))
        case Seq(d) => DBIO.successful((false, d.uuid))
        case _      => DBIO.failed(Errors.ConflictingDevice)
      }
    } yield (created, uuid)

  def exists(ns: Namespace, uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Device] =
    devices
      .filter(d => d.namespace === ns && d.uuid === uuid)
      .result
      .headOption
      .flatMap(_.fold[DBIO[Device]](DBIO.failed(Errors.MissingDevice))(DBIO.successful))

  def filterExisting(ns: Namespace, deviceOemIds: Set[DeviceOemId]): DBIO[Seq[DeviceId]] =
    devices
    .filter(_.namespace === ns)
    .filter(_.deviceId inSet deviceOemIds)
    .map(_.uuid)
    .result

  def findByDeviceIdQuery(ns: Namespace, deviceId: DeviceOemId)(implicit ec: ExecutionContext): Query[DeviceTable, Device, Seq] =
    devices.filter(d => d.namespace === ns && d.deviceId === deviceId)

  def devicesForExpressionQuery(ns: Namespace, expression: GroupExpression) = {
    val all = devices.filter(_.namespace === ns).map(_.uuid)
    GroupExpressionAST.compileToSlick(expression)(all).distinct
  }

  def searchByExpression(ns: Namespace, expression: GroupExpression)
                        (implicit db: Database, ec: ExecutionContext): DBIO[Seq[DeviceId]] =
    devicesForExpressionQuery(ns, expression).result

  def countDevicesForExpression(ns: Namespace, expression: GroupExpression)
                               (implicit db: Database, ec: ExecutionContext): DBIO[Int] =
    devicesForExpressionQuery(ns, expression).length.result

  private def searchQuery(ns: Namespace,
                          nameContains: Option[String],
                          groupId: Option[GroupId],
                          notSeenSinceHours: Option[Int],
                         ) = {

    def optionalFilter[T](o: Option[T])(fn: (DeviceTable, T) => Rep[Boolean]): DeviceTable => Rep[Boolean] =
      dt => o match {
        case None => true.bind
        case Some(t) => fn(dt, t)
      }

    val groupFilter = optionalFilter(groupId) { (dt, gid) =>
      dt.uuid in groupMembers.filter(_.groupId === gid).map(_.deviceUuid)
    }

    val nameContainsFilter = optionalFilter(nameContains) { (dt, s) =>
      dt.deviceName.mappedTo[String].toLowerCase.like(s"%${s.toLowerCase}%")
    }

    val notSeenSinceFilter = optionalFilter(notSeenSinceHours) { (dt, h) =>
      dt.lastSeen.map(i => i < Instant.now.minus(h, ChronoUnit.HOURS).bind).getOrElse(true.bind)
    }

    devices
      .filter(_.namespace === ns)
      .filter(groupFilter)
      .filter(nameContainsFilter)
      .filter(notSeenSinceFilter)
  }

  private def runQueryFilteringByName(ns: Namespace, query: Query[DeviceTable, Device, Seq], nameContains: Option[String])
                                     (implicit ec: ExecutionContext) = {
    val deviceIdsByName = searchQuery(ns, nameContains, None, None).map(_.uuid)
    query.filter(_.uuid in deviceIdsByName)
  }

  private val groupedDevicesQuery: (Namespace, Option[GroupType]) => Query[DeviceTable, Device, Seq] = (ns, groupType) =>
    groupInfos
      .maybeFilter(_.groupType === groupType)
      .filter(_.namespace === ns)
      .join(groupMembers)
      .on(_.id === _.groupId)
      .join(devices)
      .on(_._2.deviceUuid === _.uuid)
      .map(_._2)
      .distinct

  def search(ns: Namespace, params: SearchParams, ids: Seq[DeviceId])
            (implicit ec: ExecutionContext): DBIO[PaginationResult[Device]] = {
    val query = (params, ids) match {

      case (SearchParams(Some(oemId), _, _, None, None, None, _, _, _), Vector()) =>
        findByDeviceIdQuery(ns, oemId)

      case (SearchParams(None, Some(true), gt, None, nameContains, None, _, _, _), Vector()) =>
        runQueryFilteringByName(ns, groupedDevicesQuery(ns, gt), nameContains)

      case (SearchParams(None, Some(false), gt, None, nameContains, None, _, _, _), Vector()) =>
        val ungroupedDevicesQuery = devices.filterNot(_.uuid.in(groupedDevicesQuery(ns, gt).map(_.uuid)))
        runQueryFilteringByName(ns, ungroupedDevicesQuery, nameContains)

      case (SearchParams(None, _, _, gid, nameContains, notSeenSinceHours, _, _, _), Vector()) =>
        searchQuery(ns, nameContains, gid, notSeenSinceHours)

      case (SearchParams(None, None, None, None, None, None, _, _, _), ids) if ids.nonEmpty =>
        findByUuids(ns, ids)

      case _ => throw new IllegalArgumentException("Invalid parameter combination.")
    }

    query
      .sortBy(params.sortBy.getOrElse(SortBy.Name))
      .paginateResult(params.offset.orDefaultOffset, params.limit.orDefaultLimit)
  }

  def updateDeviceName(ns: Namespace, uuid: DeviceId, deviceName: DeviceName)(implicit ec: ExecutionContext): DBIO[Unit] =
    devices
      .filter(_.uuid === uuid)
      .map(r => r.deviceName)
      .update(deviceName)
      .handleIntegrityErrors(Errors.ConflictingDevice)
      .handleSingleUpdateError(Errors.MissingDevice)

  def findByUuid(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Device] =
    devices
      .filter(_.uuid === uuid)
      .result
      .headOption
      .flatMap(_.fold[DBIO[Device]](DBIO.failed(Errors.MissingDevice))(DBIO.successful))

  def findByUuids(ns: Namespace, ids: Seq[DeviceId]): Query[DeviceTable, Device, Seq] = {
    devices.filter(d => (d.namespace === ns) && (d.uuid inSet ids))
  }

  def updateLastSeen(uuid: DeviceId, when: Instant)(implicit ec: ExecutionContext): DBIO[(Boolean, Namespace)] = {
    val sometime = Some(when)

    val dbIO = for {
      (ns, activatedAt) <- devices
        .filter(_.uuid === uuid)
        .map(x => (x.namespace, x.activatedAt))
        .result
        .failIfNotSingle(Errors.MissingDevice)
      _ <- devices.filter(_.uuid === uuid).map(x => (x.lastSeen, x.activatedAt)).update((sometime, activatedAt.orElse(sometime)))
    } yield (activatedAt.isEmpty, ns)

    dbIO.transactionally
  }

  def delete(ns: Namespace, uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Unit] = {
    val dbIO = for {
      device <- exists(ns, uuid)
      _ <- EventJournal.archiveIndexedEvents(uuid)
      _ <- EventJournal.deleteEvents(uuid)
      _ <- GroupMemberRepository.removeDeviceFromAllGroups(uuid)
      _ <- PublicCredentialsRepository.delete(uuid)
      _ <- SystemInfoRepository.delete(uuid)
      _ <- TaggedDeviceRepository.delete(uuid)
      _ <- devices.filter(d => d.namespace === ns && d.uuid === uuid).delete
      _ <- deletedDevices += DeletedDevice(ns, device.uuid, device.deviceId)
    } yield ()

    dbIO.transactionally
  }

  def deviceNamespace(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Namespace] =
    devices
      .filter(_.uuid === uuid)
      .map(_.namespace)
      .result
      .failIfNotSingle(Errors.MissingDevice)

  def countActivatedDevices(ns: Namespace, start: Instant, end: Instant): DBIO[Int] =
    devices
      .filter(_.namespace === ns)
      .map(_.activatedAt.getOrElse(start.minusSeconds(36000)))
      .filter(activatedAt => activatedAt >= start && activatedAt < end)
      .distinct
      .length
      .result

  def setDeviceStatus(uuid: DeviceId, status: DeviceStatus)(implicit ec: ExecutionContext): DBIO[Unit] =
    devices
      .filter(_.uuid === uuid)
      .map(_.deviceStatus)
      .update(status)
      .handleSingleUpdateError(Errors.MissingDevice)
}
