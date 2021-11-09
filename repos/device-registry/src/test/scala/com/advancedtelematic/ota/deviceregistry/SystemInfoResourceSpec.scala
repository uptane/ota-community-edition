/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.ota.deviceregistry.db.SystemInfoRepository.removeIdNrs
import io.circe.Json
import org.scalacheck.Shrink
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{AktualizrConfigChanged, DeviceSystemInfoChanged}
import com.advancedtelematic.ota.deviceregistry.data.DataType.DeviceT
import com.advancedtelematic.ota.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps._
import org.scalatest.concurrent.Eventually._
import org.scalatest.OptionValues._
import toml.Toml
import toml.Value.{Str, Tbl}

class SystemInfoResourceSpec extends ResourcePropSpec {

  import akka.http.scaladsl.model.StatusCodes._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  override lazy val messageBus = new MockMessageBus()

  property("GET /system_info request fails on non-existent device") {
    forAll { (uuid: DeviceId, json: Json) =>
      fetchSystemInfo(uuid) ~> route ~> check { status shouldBe NotFound }
      createSystemInfo(uuid, json) ~> route ~> check { status shouldBe NotFound }
      updateSystemInfo(uuid, json) ~> route ~> check { status shouldBe NotFound }
    }
  }

  property("GET /system_info/network returns 404 NotFound on non-existent device") {
    forAll { uuid: DeviceId =>
      fetchNetworkInfo(uuid) ~> route ~> check { status shouldBe NotFound }
    }
  }

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  property("GET /system_info/network returns empty strings if network info was not reported") {
    forAll { (device: DeviceT, json: Option[Json]) =>
      val uuid = createDeviceOk(device)

      json.foreach { sysinfo =>
        createSystemInfo(uuid, removeIdNrs(sysinfo)) ~> route ~> check {
          status shouldBe Created
        }
      }

      fetchNetworkInfo(uuid) ~> route ~> check {
        status shouldBe OK
        val json = responseAs[Json]
        json.hcursor.get[String]("local_ipv4").toOption should equal(Some(""))
        json.hcursor.get[String]("mac").toOption should equal(Some(""))
        json.hcursor.get[String]("hostname").toOption should equal(Some(""))
      }
    }
  }

  property("GET /system_info return empty if device have not set system_info") {
    forAll { device: DeviceT =>
      val uuid = createDeviceOk(device)

      fetchSystemInfo(uuid) ~> route ~> check {
        status shouldBe OK
        val json = responseAs[Json]

        json shouldBe Json.obj()
      }
    }
  }

  property("GET system_info after POST should return what was posted.") {
    forAll { (device: DeviceT, json0: Json) =>
      val uuid = createDeviceOk(device)
      val json1: Json = removeIdNrs(json0)

      createSystemInfo(uuid, json1) ~> route ~> check {
        status shouldBe Created
      }

      fetchSystemInfo(uuid) ~> route ~> check {
        status shouldBe OK
        val json2: Json = responseAs[Json]
        json1 shouldBe removeIdNrs(json2)
      }
    }
  }

  property("GET system_info after PUT should return what was updated.") {
    forAll { (device: DeviceT, json1: Json, json2: Json) =>
      val uuid = createDeviceOk(device)

      createSystemInfo(uuid, json1) ~> route ~> check {
        status shouldBe Created
      }

      updateSystemInfo(uuid, json2) ~> route ~> check {
        status shouldBe OK
      }

      fetchSystemInfo(uuid) ~> route ~> check {
        status shouldBe OK
        val json3: Json = responseAs[Json]
        json2 shouldBe removeIdNrs(json3)
      }
    }
  }

  property("PUT system_info if not previously created should create it.") {
    forAll { (device: DeviceT, json: Json) =>
      val uuid = createDeviceOk(device)

      updateSystemInfo(uuid, json) ~> route ~> check {
        status shouldBe OK
      }

      fetchSystemInfo(uuid) ~> route ~> check {
        status shouldBe OK
        val json2: Json = responseAs[Json]
        json shouldBe removeIdNrs(json2)
      }
    }
  }

  property("system_info adds unique numbers for each json-object") {
    def countObjects(json: Json): Int = json.arrayOrObject(
      0,
      x => x.map(countObjects).sum,
      x => x.toList.map { case (_, v) => countObjects(v) }.sum + 1
    )

    def getField(field: String)(json: Json): Seq[Json] =
      json.arrayOrObject(List(),
                         _.flatMap(getField(field)),
                         x =>
                           x.toList.flatMap {
                             case (i, v) if i == field => List(v)
                             case (_, v)               => getField(field)(v)
                         })

    forAll { (device: DeviceT, json0: Json) =>
      val uuid = createDeviceOk(device)
      val json       = removeIdNrs(json0)

      updateSystemInfo(uuid, json) ~> route ~> check {
        status shouldBe OK
      }

      fetchSystemInfo(uuid) ~> route ~> check {
        status shouldBe OK
        val retJson = responseAs[Json]
        json shouldBe removeIdNrs(retJson)

        val idNrs = getField("id-nr")(retJson)
        //unique
        idNrs.size shouldBe idNrs.toSet.size

        //same count
        countObjects(json) shouldBe idNrs.size
      }
    }
  }

  property("DeviceSystemInfoChanged is published when client updates system_info") {
    val device: DeviceT = genDeviceT.generate
    val uuid = createDeviceOk(device)

    val json = io.circe.parser.parse(
      """
        |{
        |    "product": "test-product"
        |}
        |""".stripMargin
    ).right.get

    createSystemInfo(uuid, json) ~> route ~> check {
      status shouldBe Created
    }

    eventually {
      val msg = messageBus.findReceived[DeviceSystemInfoChanged](uuid.toString)
      msg.value.newSystemInfo.value.product should contain("test-product")
    }
  }

  property("DeviceSystemInfoChanged is published with empty system info if server could not parse json") {
    val device: DeviceT = genDeviceT.generate
    val uuid = createDeviceOk(device)

    val json = io.circe.parser.parse(
      """
        |{
        |    "not-product": "somethingelse"
        |}
        |""".stripMargin
    ).right.get

    createSystemInfo(uuid, json) ~> route ~> check {
      status shouldBe Created
    }

    eventually {
      val msg = messageBus.findReceived[DeviceSystemInfoChanged](uuid.toString)
      msg.value.newSystemInfo.value.product shouldBe empty
    }
  }

  property("TOML parsing by hand") {
    val content =
      """
        |n = 1
        |[pacman]
        |type = "ostree"
        |
        |[uptane]
        |polling_sec = 91
        |force_install_completion = true
        |
        |""".stripMargin

    val t = Toml.parse(content).right.get
    val pacmanSection = t.values("pacman")
    val pacmanTable = pacmanSection.asInstanceOf[Tbl]
    pacmanTable.values("type").asInstanceOf[Str].value shouldBe "ostree"
  }

  property("A key with the name of an expected section leads to error") {
    val content = """
        |n = 1
        |pacman = "ostree"
        |type = "ostree"
        |
        |[uptane]
        |polling_sec = 91
        |secondary_preinstall_wait_sec = 60
        |force_install_completion = true
        |
        |""".stripMargin

    SystemInfoResource.parseAktualizrConfigToml(content).failed.get.getMessage should include("cannot be cast to")
  }

  property("missing section leads to error") {
    val content = """
        n = 1
        [uptane]
        polling_sec = 91
        secondary_preinstall_wait_sec = 60
        force_install_completion = true""".stripMargin

    SystemInfoResource.parseAktualizrConfigToml(content).failed.get.getMessage shouldBe "key not found: pacman"
  }

  property("additional root key is allowed") {
    val content = """
        n = 1
        [pacman]
        type = "ostree"

        [uptane]
        polling_sec = 91
        secondary_preinstall_wait_sec = 60
        force_install_completion = true"""

    SystemInfoResource.parseAktualizrConfigToml(content) shouldBe 'success
  }

  property("section order doesn't matter") {
    val content = """
        [uptane]
        polling_sec = 91
        secondary_preinstall_wait_sec = 60
        force_install_completion = true
        n = 1
        [pacman]
        type = "ostree"
        """

    SystemInfoResource.parseAktualizrConfigToml(content) shouldBe 'success
  }

  property("additional section key is allowed") {
    val content = """
        [pacman]
        type = "ostree"
        kind = "very"

        [uptane]
        polling_sec = 91
        secondary_preinstall_wait_sec = 60
        force_install_completion = true"""

    SystemInfoResource.parseAktualizrConfigToml(content) shouldBe 'success
  }

  property("only key, no value in section") {
    val content = """
        [pacman]
        type = "ostree"
        kind

        [uptane]
        polling_sec = 91
        force_install_completion = true"""

    SystemInfoResource.parseAktualizrConfigToml(content).failed.get.getMessage shouldBe "End:4:9 ...\"kind\\n\\n    \""
  }

  property("system config can be uploaded") {
    import akka.http.scaladsl.unmarshalling.Unmarshaller._

    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd-1234")))
    val config = """

        [pacman]
        type = "arcade"

        [uptane]
        polling_sec = 123
        secondary_preinstall_wait_sec = 60
        force_install_completion = true"""

    uploadSystemConfig(deviceUuid, config) ~> route ~> check {
      status shouldBe NoContent
      responseAs[String] shouldBe ""
    }

    eventually {
      val msg = messageBus.findReceived[AktualizrConfigChanged](deviceUuid.toString)
      msg.get.pollingSec shouldBe 123
      msg.get.secondaryPreinstallWaitSec should contain(60)
      msg.get.installerType shouldBe "arcade"
    }
  }

  property("system config without 'secondary_preinstall_wait_sec' can be uploaded") {
    import akka.http.scaladsl.unmarshalling.Unmarshaller._

    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd-1234-legacy")))
    val config = """

        [pacman]
        type = "arcade"

        [uptane]
        polling_sec = 123
        force_install_completion = true"""

    uploadSystemConfig(deviceUuid, config) ~> route ~> check {
      status shouldBe NoContent
      responseAs[String] shouldBe ""
    }

    eventually {
      val msg = messageBus.findReceived[AktualizrConfigChanged](deviceUuid.toString)
      msg.get.pollingSec shouldBe 123
      msg.get.secondaryPreinstallWaitSec shouldBe None
      msg.get.installerType shouldBe "arcade"
    }
  }

  property("system config TOML parsing error handling") {
    import akka.http.scaladsl.unmarshalling.Unmarshaller._

    val deviceUuid = createDeviceOk(genDeviceT.generate.copy(deviceId = DeviceOemId("abcd-1234-error")))
    val config =
      """
        |error
        |
        |""".stripMargin

    uploadSystemConfig(deviceUuid, config) ~> route ~> check {
      status shouldBe BadRequest
      val res = responseAs[String]
      res should include("The request content was malformed")
      res should include("error")
    }
  }
}
