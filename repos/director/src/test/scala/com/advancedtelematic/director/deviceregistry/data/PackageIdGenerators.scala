/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import java.security.InvalidParameterException

import org.scalacheck.{Arbitrary, Gen}

trait PackageIdGenerators {

  /** For property based testing purposes, we need to explain how to randomly generate package ids.
    *
    * @see
    *   [[https://www.scalacheck.org/]]
    */
  val genPackageIdName: Gen[PackageId.Name] =
    Gen
      .nonEmptyContainerOf[List, Char](Gen.alphaNumChar)
      .map(_.mkString)

  val genPackageIdVersion: Gen[PackageId.Version] =
    Gen
      .listOfN(3, Gen.choose(0, 999))
      .map(_.mkString("."))

  def genConflictFreePackageIdVersion(n: Int): Seq[PackageId.Version] = {
    import GeneratorOps._
    if (n < 2) throw new InvalidParameterException("n must be greater than or equal to 2")
    var versions = Set(genPackageIdVersion.generate)
    while (versions.size < n) {
      val v = genPackageIdVersion.generate
      if (!versions.contains(v)) {
        versions += v
      }
    }
    versions.toSeq
  }

  val genPackageId: Gen[PackageId] =
    for {
      name <- genPackageIdName
      version <- genPackageIdVersion
    } yield PackageId(name, version)

  implicit lazy val arbPackageId: Arbitrary[PackageId] =
    Arbitrary(genPackageId)

}

object PackageIdGenerators extends PackageIdGenerators

/** Generators for invalid data are kept in dedicated scopes to rule out their use as implicits
  * (impersonating valid ones).
  */
trait InvalidPackageIdGenerators extends InvalidIdentGenerators {

  val genInvalidPackageIdName: Gen[PackageId.Name] = genInvalidIdent

  def getInvalidPackageIdName: PackageId.Name =
    genInvalidPackageIdName.sample.getOrElse(getInvalidPackageIdName)

  val genInvalidPackageIdVersion: Gen[PackageId.Version] =
    Gen.identifier.map(s => s + ".0")

  def getInvalidPackageIdVersion: PackageId.Version =
    genInvalidPackageIdVersion.sample.getOrElse(getInvalidPackageIdVersion)

  val genInvalidPackageId: Gen[PackageId] =
    for {
      name <- genInvalidPackageIdName
      version <- genInvalidPackageIdVersion
    } yield PackageId(name, version)

  def getInvalidPackageId: PackageId =
    genInvalidPackageId.sample.getOrElse(getInvalidPackageId)

}

object InvalidPackageIdGenerators extends InvalidPackageIdGenerators
