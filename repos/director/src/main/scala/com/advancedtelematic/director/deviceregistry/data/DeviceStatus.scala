/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import io.circe.{Decoder, Encoder}

object DeviceStatus extends Enumeration {
  type DeviceStatus = Value

  val NotSeen, Error, UpToDate, UpdatePending, Outdated, UpdateScheduled = Value

  implicit val JsonEncoder: io.circe.Encoder[Value] = Encoder.encodeEnumeration(DeviceStatus)
  implicit val JsonDecoder: io.circe.Decoder[Value] = Decoder.decodeEnumeration(DeviceStatus)

  implicit val deviceStatusUnmarshaller: Unmarshaller[String, DeviceStatus] =
    Unmarshaller.strict(DeviceStatus.withName)

}
