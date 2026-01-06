/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import java.time.Instant
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.http.deviceregistry.{
  DeviceGroupStats,
  ErrorHandlers,
  Errors,
  LastSeenTable
}
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceT,
  HibernationStatus,
  MqttStatus
}
import com.advancedtelematic.director.deviceregistry.data.Device.*
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.director.deviceregistry.data.*
import SlickMappings.*
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext
import cats.syntax.option.*
import slick.jdbc.{GetResult, PositionedParameters, SetParameter}

import java.sql.Timestamp
import scala.annotation.unused
import Schema.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*

object DeviceRepository {

  val deletedDevices = TableQuery[DeletedDeviceTable]

  def create(ns: Namespace, device: DeviceT)(implicit ec: ExecutionContext): DBIO[DeviceId] = {
    val uuid = device.uuid.getOrElse(DeviceId.generate())

    val dbDevice = DeviceDB(
      ns,
      uuid,
      device.deviceName,
      device.deviceId,
      device.deviceType,
      createdAt = Instant.now(),
      hibernated = device.hibernated.getOrElse(false),
      mqttStatus = MqttStatus.NotSeen,
      mqttLastSeen = None
    )

    val dbIO = devices += dbDevice
    dbIO
      .handleIntegrityErrors(Errors.ConflictingDevice(device.deviceName.some, device.deviceId.some))
      .mapError(ErrorHandlers.sqlExceptionHandler)
      .andThen(
        GroupMemberRepository.addDeviceToDynamicGroups(ns, DeviceDB.toDevice(dbDevice), Map.empty)
      )
      .map(_ => uuid)
      .transactionally
  }

  def findUuidFromUniqueDeviceIdOrCreate(ns: Namespace, deviceId: DeviceOemId, devT: DeviceT)(
    implicit ec: ExecutionContext): DBIO[(Boolean, DeviceId)] =
    for {
      devs <- findByDeviceIdQuery(ns, deviceId).result
      (created, uuid) <- devs match {
        case Seq()  => create(ns, devT).map((true, _))
        case Seq(d) => DBIO.successful((false, d.uuid))
        case _ => DBIO.failed(Errors.ConflictingDevice(devT.deviceName.some, devT.deviceId.some))
      }
    } yield (created, uuid)

  def exists(ns: Namespace, uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Device] =
    devices
      .filter(d => d.namespace === ns && d.id === uuid)
      .result
      .headOption
      .flatMap(
        _.map(DeviceDB.toDevice(_))
          .fold[DBIO[Device]](DBIO.failed(Errors.MissingDevice))(DBIO.successful)
      )

  def filterExisting(ns: Namespace, deviceOemIds: Set[DeviceOemId]): DBIO[Seq[DeviceId]] =
    devices
      .filter(_.namespace === ns)
      .filter(_.oemId.inSet(deviceOemIds))
      .map(_.id)
      .result

  def findByDeviceIdQuery(ns: Namespace, deviceId: DeviceOemId): Query[DeviceTable, DeviceDB, Seq] =
    devices.filter(d => d.namespace === ns && d.oemId === deviceId)

  def setDevice(ns: Namespace, uuid: DeviceId, deviceName: DeviceName, notes: Option[String])(
    implicit ec: ExecutionContext): DBIO[Unit] =
    devices
      .filter(_.namespace === ns)
      .filter(_.id === uuid)
      .map(r => r.deviceName -> r.notes)
      .update(deviceName -> notes)
      .handleIntegrityErrors(Errors.ConflictingDevice(deviceName.some))
      .handleSingleUpdateError(Errors.MissingDevice)

  def updateDevice(ns: Namespace,
                   uuid: DeviceId,
                   deviceName: Option[DeviceName],
                   notes: Option[String])(implicit ec: ExecutionContext): DBIO[Unit] = {
    val findQ = devices
      .filter(_.id === uuid)
      .filter(_.namespace === ns)

    val updateQ = (deviceName, notes) match {
      case (Some(_name), Some(_notes)) =>
        findQ.map(r => r.deviceName -> r.notes).update(_name -> Option(_notes))
      case (Some(_name), None)  => findQ.map(r => r.deviceName).update(_name)
      case (None, Some(_notes)) => findQ.map(r => r.notes).update(Option(_notes))
      case (None, None)         => DBIO.successful(0)
    }

    updateQ
      .handleIntegrityErrors(Errors.ConflictingDevice(deviceName))
      .handleSingleUpdateError(Errors.MissingDevice)
  }

  def findByUuid(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Device] =
    devices
      .filter(_.id === uuid)
      .result
      .headOption
      .flatMap(
        _.map(DeviceDB.toDevice(_))
          .fold[DBIO[Device]](DBIO.failed(Errors.MissingDevice))(DBIO.successful)
      )

  def findByUuids(ns: Namespace, ids: Seq[DeviceId]): Query[DeviceTable, DeviceDB, Seq] =
    devices.filter(d => (d.namespace === ns) && (d.id.inSet(ids)))

  def findByOemIds(ns: Namespace, oemIds: Seq[DeviceOemId]): DBIO[Seq[DeviceDB]] =
    devices.filter(d => (d.namespace === ns) && (d.oemId.inSet(oemIds))).result

  def findByDeviceUuids(ns: Namespace, deviceIds: Seq[DeviceId]): DBIO[Seq[DeviceDB]] =
    findByUuids(ns, deviceIds).result

  def updateLastSeen(uuid: DeviceId,
                     when: Instant)(implicit ec: ExecutionContext): DBIO[Boolean] = {
    val sometime = Some(when)

    val dbIO = for {
      activatedAt <- devices
        .filter(_.id === uuid)
        .map(_.activatedAt)
        .result
        .failIfNotSingle(Errors.MissingDevice)
      _ <- devices
        .filter(_.id === uuid)
        .map(x => (x.lastSeen, x.activatedAt))
        .update((sometime, activatedAt.orElse(sometime)))
    } yield activatedAt.isEmpty

    dbIO.transactionally
  }

  def deviceNamespace(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Namespace] =
    devices
      .filter(_.id === uuid)
      .map(_.namespace)
      .result
      .failIfNotSingle(Errors.MissingDevice)

  def countActivatedDevices(ns: Namespace, start: Instant, end: Instant): DBIO[Int] = {
    @unused
    implicit val setInstant: SetParameter[Instant] = (value: Instant, pos: PositionedParameters) =>
      pos.setTimestamp(Timestamp.from(value))

    // Using raw sql because slick will use it's own instant mapping for the comparisons, instead of javaInstantMapping
    val sql =
      sql"""
            select count(*) from #${devices.baseTableRow.tableName} WHERE
            namespace = ${ns.get} AND
            activated_at >= $start AND
            activated_at < $end
        """

    sql.as[Int].head
  }

  def setDeviceStatus(uuid: DeviceId, status: DeviceStatus)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    setDeviceStatusAction(uuid, status)

  protected[db] def setDeviceStatusAction(uuid: DeviceId, status: DeviceStatus)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    devices
      .filter(_.id === uuid)
      .map(_.deviceStatus)
      .update(status)
      .handleSingleUpdateError(Errors.MissingDevice)

  // updates device status if current status matches `currentStatus`
  protected[db] def compareAndSetDeviceStatusAction(uuid: DeviceId,
                                                    currentStatus: DeviceStatus,
                                                    status: DeviceStatus)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    devices
      .filter(_.id === uuid)
      .filter(_.deviceStatus === currentStatus)
      .map(_.deviceStatus)
      .update(status)
      .map(_ => ())

  // Returns the previous hibernation status
  def setHibernationStatus(ns: Namespace, id: DeviceId, status: HibernationStatus)(
    implicit ec: ExecutionContext): DBIO[HibernationStatus] =
    devices
      .filter(_.namespace === ns)
      .filter(_.id === id)
      .map(_.hibernated)
      .update(status)
      .flatMap {
        case c if c >= 1 =>
          DBIO.successful(!status)
        case c if c == 0 =>
          DBIO.successful(status)
      }

  def setMqttStatus(id: DeviceId, status: MqttStatus, lastSeen: Instant)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    devices
      .filter(_.id === id)
      .map(r => (r.mqttStatus, r.mqttLastSeen))
      .update(status -> lastSeen.some)
      .handleSingleUpdateError(Errors.MissingDevice)

  def getDeviceGroupStats(
    groupId: GroupId)(implicit ec: ExecutionContext): DBIO[DeviceGroupStats] = {

    implicit val getDeviceStatus = GetResult[DeviceStatus] { r =>
      DeviceStatus.withName(r.nextString())
    }

    val statusQuery = sql"""
      SELECT
        device_status,
        COUNT(*) as status_count
      FROM Device d
      INNER JOIN GroupMembers gm ON d.uuid = gm.device_uuid
      WHERE gm.group_id = ${groupId.uuid.toString}
      GROUP BY device_status
    """.as[(DeviceStatus, Long)]

    implicit val getLastSeenTable = GetResult[LastSeenTable] { r =>
      LastSeenTable(
        r.rs.getLong("seen_10mins"),
        r.rs.getLong("seen_1hour"),
        r.rs.getLong("seen_1day"),
        r.rs.getLong("seen_1week"),
        r.rs.getLong("seen_1month"),
        r.rs.getLong("seen_1year")
      )
    }

    val lastSeenQuery = sql"""
      SELECT
        SUM(CASE WHEN last_seen IS NOT NULL AND TIMESTAMPDIFF(MINUTE, last_seen, NOW()) <= 10 THEN 1 ELSE 0 END) as seen_10mins,
        SUM(CASE WHEN last_seen IS NOT NULL AND TIMESTAMPDIFF(MINUTE, last_seen, NOW()) <= 60 THEN 1 ELSE 0 END) as seen_1hour,
        SUM(CASE WHEN last_seen IS NOT NULL AND TIMESTAMPDIFF(MINUTE, last_seen, NOW()) <= 1440 THEN 1 ELSE 0 END) as seen_1day,
        SUM(CASE WHEN last_seen IS NOT NULL AND TIMESTAMPDIFF(MINUTE, last_seen, NOW()) <= 10080 THEN 1 ELSE 0 END) as seen_1week,
        SUM(CASE WHEN last_seen IS NOT NULL AND TIMESTAMPDIFF(MINUTE, last_seen, NOW()) <= 43200 THEN 1 ELSE 0 END) as seen_1month,
        SUM(CASE WHEN last_seen IS NOT NULL AND TIMESTAMPDIFF(MINUTE, last_seen, NOW()) <= 525600 THEN 1 ELSE 0 END) as seen_1year
      FROM Device d
      INNER JOIN GroupMembers gm ON d.uuid = gm.device_uuid
      WHERE gm.group_id = ${groupId.uuid.toString}
    """.as[LastSeenTable]

    statusQuery.flatMap { statusRows =>
      val status = statusRows.toMap
      lastSeenQuery.map { rows =>
        val lastSeen = rows.headOption.getOrElse(LastSeenTable(0L, 0L, 0L, 0L, 0L, 0L))
        DeviceGroupStats(status, lastSeen)
      }
    }
  }

}
