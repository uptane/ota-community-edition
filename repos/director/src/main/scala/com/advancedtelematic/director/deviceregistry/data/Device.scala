/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import cats.Show
import cats.syntax.show.*
import com.advancedtelematic.director.deviceregistry.data.DataType.MqttStatus
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.director.deviceregistry.data.Device.{DeviceOemId, DeviceType}
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.*
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import DataType.{mqttStatusDecoder, mqttStatusEncoder}
import com.advancedtelematic.director.db.deviceregistry.Schema
import com.advancedtelematic.director.db.deviceregistry.Schema.DeviceTable
import slick.ast.{FieldSymbol, Select}
import slick.lifted.{Rep, TableQuery}

final case class DeviceDB(namespace: Namespace,
                          uuid: DeviceId,
                          deviceName: DeviceName,
                          deviceId: DeviceOemId,
                          deviceType: DeviceType = DeviceType.Other,
                          lastSeen: Option[Instant] = None,
                          createdAt: Instant,
                          activatedAt: Option[Instant] = None,
                          deviceStatus: DeviceStatus = NotSeen,
                          notes: Option[String] = None,
                          hibernated: Boolean = false,
                          mqttStatus: MqttStatus,
                          mqttLastSeen: Option[Instant])

object DeviceDB {

  def toDevice(dbDevice: DeviceDB, attributes: Map[TagId, String] = Map.empty): Device =
    Device(
      namespace = dbDevice.namespace,
      uuid = dbDevice.uuid,
      deviceName = dbDevice.deviceName,
      deviceId = dbDevice.deviceId,
      deviceType = dbDevice.deviceType,
      lastSeen = dbDevice.lastSeen,
      createdAt = dbDevice.createdAt,
      activatedAt = dbDevice.activatedAt,
      deviceStatus = dbDevice.deviceStatus,
      notes = dbDevice.notes,
      hibernated = dbDevice.hibernated,
      mqttStatus = dbDevice.mqttStatus,
      mqttLastSeen =
        if (dbDevice.mqttStatus == MqttStatus.Online) Option(Instant.now())
        else dbDevice.mqttLastSeen,
      attributes = attributes
    )

}

final case class Device(namespace: Namespace,
                        uuid: DeviceId,
                        deviceName: DeviceName,
                        deviceId: DeviceOemId,
                        deviceType: DeviceType = DeviceType.Other,
                        lastSeen: Option[Instant] = None,
                        createdAt: Instant,
                        activatedAt: Option[Instant] = None,
                        deviceStatus: DeviceStatus = NotSeen,
                        notes: Option[String] = None,
                        hibernated: Boolean = false,
                        mqttLastSeen: Option[Instant],
                        mqttStatus: MqttStatus,
                        attributes: Map[TagId, String] = Map.empty)

object Device {

  final case class DeviceOemId(underlying: String) extends AnyVal
  implicit val showDeviceOemId: Show[DeviceOemId] = deviceId => deviceId.underlying

  type DeviceType = DeviceType.DeviceType

  final object DeviceType extends Enumeration {

    type DeviceType = Value
    val Other, Vehicle = Value

    implicit val JsonEncoder: io.circe.Encoder[Value] = Encoder.encodeEnumeration(DeviceType)
    implicit val JsonDecoder: io.circe.Decoder[Value] = Decoder.decodeEnumeration(DeviceType)
  }

  implicit val showDeviceType: Show[DeviceType.Value] = Show.fromToString[DeviceType.Value]

  implicit val showDevice: Show[Device] = Show.show[Device] {
    case d if d.deviceType == DeviceType.Vehicle =>
      s"Vehicle: uuid=${d.uuid.show}, VIN=${d.deviceId}, lastSeen=${d.lastSeen}"
    case d => s"Device: uuid=${d.uuid.show}, lastSeen=${d.lastSeen}"
  }

  implicit val TagIdKeyEncoder: KeyEncoder[TagId] = (key: TagId) => key.value

  implicit val TagIdKeyDecoder: KeyDecoder[TagId] = (s: String) => Some(TagId(s))

  implicit val EncoderInstance
    : io.circe.Encoder.AsObject[com.advancedtelematic.director.deviceregistry.data.Device] = {
    import com.advancedtelematic.libats.codecs.CirceCodecs._
    import Codecs.deviceOemIdEncoder
    io.circe.generic.semiauto.deriveEncoder[Device]
  }

  implicit val DecoderInstance
    : io.circe.Decoder[com.advancedtelematic.director.deviceregistry.data.Device] = {
    import com.advancedtelematic.libats.codecs.CirceCodecs._
    import Codecs.deviceOemIdDecoder
    io.circe.generic.semiauto.deriveDecoder[Device]
  }

  implicit val DeviceIdOrdering: Ordering[DeviceOemId] = (id1, id2) =>
    id1.underlying.compare(id2.underlying)

  implicit def DeviceOrdering(implicit ord: Ordering[UUID]): Ordering[Device] =
    (d1, d2) => ord.compare(d1.uuid.uuid, d2.uuid.uuid)

  implicit val showOffsetDateTable: Show[OffsetDateTime] = odt => odt.toString

  case class ActiveDeviceCount(deviceCount: Int) extends AnyVal

  object ActiveDeviceCount {

    implicit val EncoderInstance: io.circe.Encoder[
      com.advancedtelematic.director.deviceregistry.data.Device.ActiveDeviceCount
    ] =
      Encoder.encodeInt.contramap[ActiveDeviceCount](_.deviceCount)

    implicit val DecoderInstance: io.circe.Decoder[
      com.advancedtelematic.director.deviceregistry.data.Device.ActiveDeviceCount
    ] =
      Decoder.decodeInt.map(ActiveDeviceCount.apply)

  }

}

object SortDirection {
  sealed trait SortDirection
  case object Asc extends SortDirection
  case object Desc extends SortDirection
}

object DeviceSortBy {
  sealed abstract class DeviceSortBy(column: DeviceTable => Rep[?]) {
    def columnName: String =
      column(TableQuery[Schema.DeviceTable].baseTableRow).toNode match {
        case Select(_, FieldSymbol(name)) => name
        case col => throw new IllegalArgumentException(s"$col cannot be used as device sort column")
      }
  }
  case object Name extends DeviceSortBy(_.deviceName)
  case object CreatedAt extends DeviceSortBy(_.createdAt)
  case object DeviceId extends DeviceSortBy(_.oemId)
  case object Uuid extends DeviceSortBy(_.id)
  case object ActivatedAt extends DeviceSortBy(_.activatedAt)
  case object LastSeen extends DeviceSortBy(_.lastSeen)
}
