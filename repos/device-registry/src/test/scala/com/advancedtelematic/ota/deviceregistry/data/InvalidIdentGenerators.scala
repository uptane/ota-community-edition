/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import org.scalacheck.Gen

trait InvalidIdentGenerators {

  val EMPTY_STR = ""

  val genSymbol: Gen[Char] =
    Gen.oneOf('!', '@', '#', '$', '^', '&', '*', '(', ')')

  val genInvalidIdent: Gen[String] =
    for (prefix <- Gen.identifier;
         suffix <- Gen.identifier) yield prefix + getSymbol + suffix

  def getInvalidIdent: String = genInvalidIdent.sample.getOrElse(getInvalidIdent)

  def getSymbol: Char = genSymbol.sample.getOrElse(getSymbol)

}

object InvalidIdentGenerators extends InvalidIdentGenerators
