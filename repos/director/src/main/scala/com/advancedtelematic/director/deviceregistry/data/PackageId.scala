/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import cats.{Eq, Show}

final case class PackageId(name: PackageId.Name, version: PackageId.Version)

object PackageId {
  type Name = String
  type Version = String

  implicit val EncoderInstance
    : io.circe.Encoder.AsObject[com.advancedtelematic.director.deviceregistry.data.PackageId] =
    io.circe.generic.semiauto.deriveEncoder[PackageId]

  implicit val DecoderInstance
    : io.circe.Decoder[com.advancedtelematic.director.deviceregistry.data.PackageId] =
    io.circe.generic.semiauto.deriveDecoder[PackageId]

  /** Use the underlying (string) ordering, show and equality for package ids.
    */
  implicit val PackageIdOrdering: Ordering[PackageId] = new Ordering[PackageId] {

    override def compare(id1: PackageId, id2: PackageId): Int =
      (id1.name + id1.version).compare(id2.name + id2.version)

  }

  implicit val showInstance: Show[PackageId] =
    Show.show(id => s"${id.name}-${id.version}")

  implicit val eqInstance: Eq[PackageId] =
    Eq.fromUniversalEquals[PackageId]

}
