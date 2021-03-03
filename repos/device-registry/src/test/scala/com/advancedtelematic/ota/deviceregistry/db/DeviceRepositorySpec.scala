/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.db

import java.time.Instant
import com.advancedtelematic.libats.test.LongTest
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.ota.deviceregistry.DatabaseSpec
import com.advancedtelematic.ota.deviceregistry.data.DeviceGenerators.{genDeviceId, genDeviceT}
import com.advancedtelematic.ota.deviceregistry.data.DataType.DeletedDevice
import com.advancedtelematic.ota.deviceregistry.data.Namespaces
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.LoneElement._
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

class DeviceRepositorySpec extends FunSuite with DatabaseSpec with ScalaFutures with Matchers with LongTest {

  test("updateLastSeen sets activated_at the first time only") {

    val device = genDeviceT.sample.get.copy(deviceId = genDeviceId.sample.get)
    val setTwice = for {
      uuid   <- DeviceRepository.create(Namespaces.defaultNs, device)
      first  <- DeviceRepository.updateLastSeen(uuid, Instant.now()).map(_._1)
      second <- DeviceRepository.updateLastSeen(uuid, Instant.now()).map(_._1)
    } yield (first, second)

    whenReady(db.run(setTwice), Timeout(Span(10, Seconds))) {
      case (f, s) =>
        f shouldBe true
        s shouldBe false
    }
  }

  test("activated_at can be counted") {

    val device = genDeviceT.sample.get.copy(deviceId = genDeviceId.sample.get)
    val createDevice = for {
      uuid <- DeviceRepository.create(Namespaces.defaultNs, device)
      now = Instant.now()
      _     <- DeviceRepository.updateLastSeen(uuid, now)
      count <- DeviceRepository.countActivatedDevices(Namespaces.defaultNs, now, now.plusSeconds(100))
    } yield count

    whenReady(db.run(createDevice), Timeout(Span(10, Seconds))) { count =>
      count shouldBe 1
    }
  }

  test("deleting a device should store information about it in a special table") {
    val deviceT = genDeviceT.sample.get
    val deviceUuid = db.run(DeviceRepository.create(Namespaces.defaultNs, deviceT)).futureValue

    db.run(DeviceRepository.delete(Namespaces.defaultNs, deviceUuid)).futureValue

    val deletedDevices = db.run(
      DeviceRepository.deletedDevices
        .filter(_.uuid === deviceUuid)
        .result
    ).futureValue

    deletedDevices.loneElement shouldBe DeletedDevice(
      Namespaces.defaultNs,
      deviceUuid,
      deviceT.deviceId)
  }
}
