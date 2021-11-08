/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import cats.Show
import cats.syntax.show._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.ota.deviceregistry.data.Device.{DeviceOemId, DeviceType}
import com.advancedtelematic.ota.deviceregistry.data.DeviceStatus._
import io.circe.{Codec, Decoder, Encoder}

final case class Device(namespace: Namespace,
                        uuid: DeviceId,
                        deviceName: DeviceName,
                        deviceId: DeviceOemId,
                        deviceType: DeviceType = DeviceType.Other,
                        lastSeen: Option[Instant] = None,
                        createdAt: Instant,
                        activatedAt: Option[Instant] = None,
                        deviceStatus: DeviceStatus = NotSeen)

object Device {

  final case class DeviceOemId(underlying: String) extends AnyVal
  implicit val showDeviceOemId: Show[DeviceOemId] = deviceId => deviceId.underlying

  type DeviceType = DeviceType.DeviceType

  final object DeviceType extends Enumeration {
    // TODO: We should encode Enums as strings, not Ints
    // Moved this from SlickEnum, because this should **NOT** be used
    // It's difficult to read this when reading from the database and the Id is not stable when we add/remove
    // values from the enum
    import slick.jdbc.MySQLProfile.MappedJdbcType
    import slick.jdbc.MySQLProfile.api._

    implicit val enumMapper = MappedJdbcType.base[Value, Int](_.id, this.apply)

    type DeviceType = Value
    val Other, Vehicle = Value

    implicit val JsonEncoder = Encoder.encodeEnumeration(DeviceType)
    implicit val JsonDecoder = Decoder.decodeEnumeration(DeviceType)
  }

  implicit val showDeviceType = Show.fromToString[DeviceType.Value]

  implicit val showDevice: Show[Device] = Show.show[Device] {
    case d if d.deviceType == DeviceType.Vehicle =>
      s"Vehicle: uuid=${d.uuid.show}, VIN=${d.deviceId}, lastSeen=${d.lastSeen}"
    case d => s"Device: uuid=${d.uuid.show}, lastSeen=${d.lastSeen}"
  }

  implicit val EncoderInstance = {
    import com.advancedtelematic.libats.codecs.CirceCodecs._
    import Codecs.deviceOemIdEncoder
    io.circe.generic.semiauto.deriveEncoder[Device]
  }
  implicit val DecoderInstance = {
    import com.advancedtelematic.libats.codecs.CirceCodecs._
    import Codecs.deviceOemIdDecoder
    io.circe.generic.semiauto.deriveDecoder[Device]
  }

  implicit val DeviceIdOrdering: Ordering[DeviceOemId] = (id1, id2) => id1.underlying compare id2.underlying

  implicit def DeviceOrdering(implicit ord: Ordering[UUID]): Ordering[Device] =
    (d1, d2) => ord.compare(d1.uuid.uuid, d2.uuid.uuid)

  implicit val showOffsetDateTable: Show[OffsetDateTime] = odt => odt.toString

  case class ActiveDeviceCount(deviceCount: Int) extends AnyVal

  object ActiveDeviceCount {
    implicit val EncoderInstance = Encoder.encodeInt.contramap[ActiveDeviceCount](_.deviceCount)
    implicit val DecoderInstance = Decoder.decodeInt.map(ActiveDeviceCount.apply)
  }
}
