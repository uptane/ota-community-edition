/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import io.circe.{Json, JsonObject}
import org.scalacheck.{Arbitrary, Gen}

trait SimpleJsonGenerator {

  import Arbitrary._

  val simpleJsonPairGen: Gen[(String, Json)] = for {
    k <- Gen.identifier
    v <- arbitrary[String]
  } yield (k, Json.fromString(v))

  val simpleJsonGen: Gen[Json] = for {
    vs <- Gen.nonEmptyContainerOf[List, (String, Json)](simpleJsonPairGen)
  } yield Json.fromJsonObject(JsonObject.fromMap(vs.toMap))

  implicit lazy val arbSimpleJson: Arbitrary[Json] = Arbitrary(simpleJsonGen)
}

object SimpleJsonGenerator extends SimpleJsonGenerator
