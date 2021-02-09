/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import cats.syntax.show._
import com.advancedtelematic.ota.deviceregistry.data.Device.DeviceOemId
import org.scalacheck.{Arbitrary, Gen}

/**
  * Created by vladimir on 16/03/16.
  */
trait DeviceIdGenerators {

  /**
    * For property based testing purposes, we need to explain how to
    * randomly generate (possibly invalid) VINs.
    *
    * @see [[https://www.scalacheck.org/]]
    */
  val genVin: Gen[DeviceOemId] =
    for {
      vin <- SemanticVin.genSemanticVin
    } yield DeviceOemId(vin.show)

  implicit lazy val arbVin: Arbitrary[DeviceOemId] =
    Arbitrary(genVin)

  val genVinChar: Gen[Char] =
    Gen.oneOf('A' to 'Z' diff List('I', 'O', 'Q'))

  val genInvalidDeviceId: Gen[DeviceOemId] = {

    val genTooLongVin: Gen[String] = for {
      n  <- Gen.choose(18, 100) // scalastyle:ignore magic.number
      cs <- Gen.listOfN(n, genVinChar)
    } yield cs.mkString

    val genTooShortVin: Gen[String] = for {
      n  <- Gen.choose(1, 16) // scalastyle:ignore magic.number
      cs <- Gen.listOfN(n, genVinChar)
    } yield cs.mkString

    val genNotAlphaNumVin: Gen[String] =
      Gen
        .listOfN(17, Arbitrary.arbitrary[Char])
        . // scalastyle:ignore magic.number
        suchThat(_.exists(!_.isLetterOrDigit))
        .flatMap(_.mkString)

    Gen
      .oneOf(genTooLongVin, genTooShortVin, genNotAlphaNumVin)
      .map(DeviceOemId)
  }

  def getInvalidVin: DeviceOemId =
    genInvalidDeviceId.sample.getOrElse(getInvalidVin)
}
