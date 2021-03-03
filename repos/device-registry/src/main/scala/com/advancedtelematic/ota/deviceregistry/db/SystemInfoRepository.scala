/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.db

import akka.Done
import cats.data.State
import cats.implicits._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.ota.deviceregistry.common.Errors
import io.circe.Json
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SystemInfoRepository {
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._

  type SystemInfoType = Json
  case class SystemInfo(uuid: DeviceId, systemInfo: SystemInfoType)

  private val _log = LoggerFactory.getLogger(this.getClass)

  private implicit val lenientJsonMapper = MappedColumnType.base[Json, String](
    _.noSpaces
    ,
    { str =>
      io.circe.parser.parse(str) match {
        case Left(err) =>
          _log.warn(s"Could not decode json string from database: $err")
          Json.Null
        case Right(v) => v
      }
    }
  )

  // scalastyle:off
  class SystemInfoTable(tag: Tag) extends Table[SystemInfo](tag, "DeviceSystem") {
    def uuid       = column[DeviceId]("uuid")
    def systemInfo = column[Json]("system_info")

    def * = (uuid, systemInfo).shaped <> ((SystemInfo.apply _).tupled, SystemInfo.unapply _)

    def pk = primaryKey("uuid", uuid)
  }
  // scalastyle:on

  final case class NetworkInfo(deviceUuid: DeviceId, localIpV4: String, hostname: String, macAddress: String)

  class SysInfoNetworkTable(tag: Tag) extends Table[NetworkInfo](tag, "DeviceSystem") {
    def uuid       = column[DeviceId]("uuid")
    def localIpV4  = column[String]("local_ipv4")
    def hostname   = column[String]("hostname")
    def macAddress = column[String]("mac_address")

    def pk = primaryKey("sys_info_pk", uuid)

    def * = (uuid, localIpV4, hostname, macAddress).mapTo[NetworkInfo]
  }

  private val networkInfos = TableQuery[SysInfoNetworkTable]

  def setNetworkInfo(value: NetworkInfo)(implicit ec: ExecutionContext): DBIO[Done] =
    networkInfos.insertOrUpdate(value).map(_ => Done)

  def getNetworkInfo(deviceUuid: DeviceId)(implicit ec: ExecutionContext): DBIO[NetworkInfo] =
    networkInfos.filter(_.uuid === deviceUuid).result.failIfEmpty(Errors.MissingSystemInfo).map(_.head)

  val systemInfos = TableQuery[SystemInfoTable]

  def removeIdNrs(json: Json): Json = json.arrayOrObject(
    json,
    x => Json.fromValues(x.map(removeIdNrs)),
    x => Json.fromFields(x.toList.collect { case (i, v) if i != "id-nr" => (i, removeIdNrs(v)) })
  )

  private def addUniqueIdsSIM(j: Json): State[Int, Json] = j.arrayOrObject(
    State.pure(j),
    _.toList.traverse(addUniqueIdsSIM).map(Json.fromValues),
    _.toList
      .traverse {
        case (other, value) => addUniqueIdsSIM(value).map(other -> _)
      }
      .flatMap(
        xs =>
          State { nr =>
            (nr + 1, Json.fromFields(("id-nr" -> Json.fromString(s"$nr")) :: xs))
        }
      )
  )

  def addUniqueIdNrs(j: Json): Json = addUniqueIdsSIM(j).run(0).value._2

  def list(ns: Namespace)(implicit ec: ExecutionContext): DBIO[Seq[SystemInfo]] =
    DeviceRepository.devices
      .filter(_.namespace === ns)
      .join(systemInfos)
      .on(_.uuid === _.uuid)
      .map(_._2)
      .result

  def exists(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[SystemInfo] =
    systemInfos
      .filter(_.uuid === uuid)
      .result
      .headOption
      .flatMap(_.fold[DBIO[SystemInfo]](DBIO.failed(Errors.MissingSystemInfo))(DBIO.successful))

  def findByUuid(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[SystemInfoType] = {
    val dbIO = for {
      _ <- DeviceRepository.findByUuid(uuid)
      p <- systemInfos
        .filter(_.uuid === uuid)
        .result
        .failIfNotSingle(Errors.MissingSystemInfo)
    } yield p.systemInfo

    dbIO.transactionally
  }

  def create(uuid: DeviceId, data: SystemInfoType)(implicit ec: ExecutionContext): DBIO[Unit] =
    for {
      _ <- DeviceRepository.findByUuid(uuid) // check that the device exists
      _ <- exists(uuid).asTry.flatMap {
        case Success(_) => DBIO.failed(Errors.ConflictingSystemInfo)
        case Failure(_) => DBIO.successful(())
      }
      newData = addUniqueIdNrs(data)
      _ <- systemInfos += SystemInfo(uuid, newData)
    } yield ()

  def update(uuid: DeviceId, data: SystemInfoType)(implicit ec: ExecutionContext): DBIO[Unit] =
    for {
      _ <- DeviceRepository.findByUuid(uuid) // check that the device exists
      newData = addUniqueIdNrs(data)
      _ <- systemInfos.insertOrUpdate(SystemInfo(uuid, newData))
    } yield ()

  def delete(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Int] =
    systemInfos.filter(_.uuid === uuid).delete
}
