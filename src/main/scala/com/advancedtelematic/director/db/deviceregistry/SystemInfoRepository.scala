/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import org.apache.pekko.Done
import cats.data.State
import cats.implicits._
import com.advancedtelematic.director.http.deviceregistry.Errors
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import io.circe.{Decoder, Encoder, Json}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SystemInfoRepository {
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._

  type SystemInfoType = Json
  case class SystemInfo(uuid: DeviceId, systemInfo: SystemInfoType)

  private val _log = LoggerFactory.getLogger(this.getClass)

  private implicit val lenientJsonMapper: slick.jdbc.MySQLProfile.BaseColumnType[io.circe.Json] =
    MappedColumnType.base[Json, String](
      _.noSpaces,
      str =>
        io.circe.parser.parse(str) match {
          case Left(err) =>
            _log.warn(s"Could not decode json string from database: $err")
            Json.Null
          case Right(v) => v
        }
    )

  // scalastyle:off
  class SystemInfoTable(tag: Tag) extends Table[SystemInfo](tag, "DeviceSystem") {
    def uuid = column[DeviceId]("uuid")
    def systemInfo = column[Json]("system_info")

    def * = (uuid, systemInfo).shaped <> ((SystemInfo.apply _).tupled, SystemInfo.unapply _)

    def pk = primaryKey("uuid", uuid)
  }
  // scalastyle:on

  final case class NetworkInfo(deviceUuid: DeviceId,
                               localIpV4: Option[String] = None,
                               hostname: Option[String] = None,
                               macAddress: Option[String] = None)

  object NetworkInfo {

    implicit val NetworkInfoEncoder: Encoder[NetworkInfo] = Encoder.instance { x =>
      import io.circe.syntax._
      Json.obj(
        "local_ipv4" -> x.localIpV4.asJson,
        "mac" -> x.macAddress.asJson,
        "hostname" -> x.hostname.asJson
      )
    }

    implicit val DeviceIdToNetworkInfoDecoder: Decoder[DeviceId => NetworkInfo] = Decoder.instance {
      c =>
        for {
          ip <- c.getOrElse[Option[String]]("local_ipv4")(None)
          mac <- c.getOrElse[Option[String]]("mac")(None)
          hostname <- c.getOrElse[Option[String]]("hostname")(None)
        } yield (uuid: DeviceId) => NetworkInfo(uuid, ip, hostname, mac)
    }

  }

  implicit val networkInfoWithDeviceIdEncoder: Encoder[NetworkInfo] = Encoder.instance { x =>
    import io.circe.syntax._
    Json.obj(
      "deviceUuid" -> x.deviceUuid.asJson,
      "local_ipv4" -> x.localIpV4.asJson,
      "mac" -> x.macAddress.asJson,
      "hostname" -> x.hostname.asJson
    )
  }

  implicit val networkInfoWithDeviceIdDecoder: Decoder[NetworkInfo] = Decoder.instance { x =>
    for {
      id <- x.get[DeviceId]("deviceUuid")
      ip <- x.getOrElse[Option[String]]("local_ipv4")(None)
      mac <- x.getOrElse[Option[String]]("mac")(None)
      hostname <- x.getOrElse[Option[String]]("hostname")(None)
    } yield NetworkInfo(id, ip, hostname, mac)
  }

  class SysInfoNetworkTable(tag: Tag) extends Table[NetworkInfo](tag, "DeviceSystem") {
    def uuid = column[DeviceId]("uuid")
    def localIpV4 = column[Option[String]]("local_ipv4")
    def hostname = column[Option[String]]("hostname")
    def macAddress = column[Option[String]]("mac_address")

    def pk = primaryKey("sys_info_pk", uuid)
    def * = (uuid, localIpV4, hostname, macAddress).mapTo[NetworkInfo]
  }

  private val networkInfos = TableQuery[SysInfoNetworkTable]

  def setNetworkInfo(value: NetworkInfo)(implicit ec: ExecutionContext): DBIO[Done] =
    networkInfos.insertOrUpdate(value).map(_ => Done)

  def getNetworkInfo(deviceUuid: DeviceId)(implicit ec: ExecutionContext): DBIO[NetworkInfo] =
    networkInfos
      .filter(_.uuid === deviceUuid)
      .result
      .failIfEmpty(Errors.MissingSystemInfo)
      .map(_.head)

  def getNetworksInfo(devices: Seq[DeviceId])(
    implicit ec: ExecutionContext): DBIO[Map[DeviceId, NetworkInfo]] =
    networkInfos.filter(_.uuid.inSet(devices)).map(ni => ni.uuid -> ni).result.map(_.toMap)

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
      .traverse { case (other, value) =>
        addUniqueIdsSIM(value).map(other -> _)
      }
      .flatMap(xs =>
        State { nr =>
          (nr + 1, Json.fromFields(("id-nr" -> Json.fromString(s"$nr")) :: xs))
        }
      )
  )

  def addUniqueIdNrs(j: Json): Json = addUniqueIdsSIM(j).run(0).value._2

  def list(ns: Namespace): DBIO[Seq[SystemInfo]] =
    Schema.devices
      .filter(_.namespace === ns)
      .join(systemInfos)
      .on(_.id === _.uuid)
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

  def delete(uuid: DeviceId): DBIO[Int] =
    systemInfos.filter(_.uuid === uuid).delete

}
