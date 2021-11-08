/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import io.circe.{Decoder, Encoder}

object DeviceStatus extends Enumeration {
  type DeviceStatus = Value

  val NotSeen, Error, UpToDate, UpdatePending, Outdated = Value

  implicit val JsonEncoder = Encoder.encodeEnumeration(DeviceStatus)
  implicit val JsonDecoder = Decoder.decodeEnumeration(DeviceStatus)
}
