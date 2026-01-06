/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

case class PackageStat(packageVersion: String, installedCount: Int)

object PackageStat {

  import io.circe.Encoder
  import io.circe.generic.semiauto.*

  implicit val encoder: Encoder[PackageStat] = deriveEncoder[PackageStat]
}
