/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import org.scalacheck.Gen

import scala.annotation.tailrec

object GeneratorOps {

  implicit class GenSample[T](gen: Gen[T]) {

    @tailrec
    final def generate: T =
      gen.sample match {
        case Some(v) => v
        case None    => generate
      }

  }

}
