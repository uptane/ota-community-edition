/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.data

import java.time.Instant

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import org.scalacheck.{Arbitrary, Gen}

trait GroupGenerators {

  private lazy val defaultNs: Namespace = Namespace("default")

  def genGroupName(charGen: Gen[Char] = Arbitrary.arbChar.arbitrary): Gen[GroupName] = for {
    strLen <- Gen.choose(2, 100)
    name   <- Gen.listOfN[Char](strLen, charGen)
  } yield GroupName.from(name.mkString).right.get

  def genStaticGroup: Gen[Group] = for {
    groupName <- genGroupName()
    createdAt <- Gen.resize(1000000000, Gen.posNum[Long]).map(Instant.ofEpochSecond)
  } yield Group(GroupId.generate(), groupName, defaultNs, createdAt, GroupType.static, None)

  implicit lazy val arbGroupName: Arbitrary[GroupName] = Arbitrary(genGroupName())
  implicit lazy val arbStaticGroup: Arbitrary[Group] = Arbitrary(genStaticGroup)
}

object GroupGenerators extends GroupGenerators
