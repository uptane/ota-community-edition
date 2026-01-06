/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import com.advancedtelematic.libats.data.DataType.Namespace
import org.scalacheck.{Arbitrary, Gen}

trait Namespaces {

  /** For property based testing purposes, we need to explain how to randomly generate namespaces.
    *
    * @see
    *   [[https://www.scalacheck.org/]]
    */
  val NamespaceGen: Gen[Namespace] =
    // TODO: for now, just use simple identifiers
    Gen.identifier.map(Namespace.apply)

  implicit val arbitraryNamespace: Arbitrary[Namespace] = Arbitrary(NamespaceGen)

  val defaultNs: Namespace = Namespace("default")
}

object Namespaces extends Namespaces
