package com.advancedtelematic.libtuf.data

import cats.syntax.either._
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{Delegation, TargetCustom, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.TargetName
import io.circe.syntax._
import io.circe.{parser => circeParser, DecodingFailure, Json, ParsingFailure}
import org.scalatest.concurrent.ScalaFutures

class TufClientCodecsSpec extends LibtufSpec with ScalaFutures {

  test("can decode targets.json without delegations") {
    val result = circeParser.parse("""
        | {
        |  "_type": "Targets",
        |  "expires": "2018-12-13T15:37:21Z",
        |  "targets": {
        |    "myfile01": {
        |      "hashes": {
        |        "sha256": "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
        |      },
        |      "length": 2,
        |      "custom": null
        |    }
        |  },
        |  "version": 1
        |}
      """.stripMargin)

    val targets = result.flatMap(_.as[TargetsRole]).valueOr(throw _)

    targets.delegations shouldBe empty
    targets.delegations shouldBe empty
  }

  test("can decode targets.json") {
    val result = circeParser.parse("""
        | {
        |  "_type": "Targets",
        |  "expires": "2018-12-13T15:37:21Z",
        |  "targets": {
        |    "myfile01": {
        |      "hashes": {
        |        "sha256": "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
        |      },
        |      "length": 2,
        |      "custom": null
        |    }
        |  },
        |  "version": 1,
        |  "delegations": {
        |    "keys" : {
        |      "b0f098fa0807d92b6070c1c07a97f9fb8493fddd930fb185ff68a97552a1057a" : {
        |        "keyval" : {
        |          "public" : "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAllEzCtMDDnx6MSNd64an\nPZLFkkQtel47LGxYACPLM9kIlC0nWcASaz6RndqSWGC/As+v81OUkWfvwioDrl0t\nG7syX2qFNAWK2w6vm5DxMUtrTLUpm7TvNYOgRG9NaLqphARnyz8wCw7jClLuHIT3\nBR1QufHs3UAZvMWHf73ZvWQdzW7uQjUG+kEmHXanndmUfki2JcKtC9DYdcqaVB1r\n2mdksfa3uW6YwYWC65WKY6Om6tlJDaF5YHQqnM/kiHSKXjnt+rJSUu8p+0WeCIVe\nYCOwqOZN8NubMGicd9pe+saevMNyEY1wyZ2AEzF4KhhsHAufqxhTcXTOJW5sZHPm\nYwIDAQAB\n-----END PUBLIC KEY-----\n"
        |        },
        |        "keytype" : "RSA"
        |      }
        |    },
        |    "roles" : [
        |      {
        |        "name" : "delegation01",
        |        "keyids" : [
        |          "b0f098fa0807d92b6070c1c07a97f9fb8493fddd930fb185ff68a97552a1057a"
        |        ],
        |        "paths" : [
        |          "all/*"
        |        ],
        |        "threshold" : 1,
        |        "terminating" : true
        |      }
        |    ]
        |  }
        |}
      """.stripMargin)

    val targets = result.flatMap(_.as[TargetsRole]).valueOr(throw _)

    targets shouldBe a[TargetsRole]
    targets.delegations shouldNot be(empty)

    targets.delegations.get.roles shouldNot be(empty)
  }

  test("delegation role name codec fails when delegation path is invalid") {
    val str =
      """{
        |  "name": "validname",
        |  "keyids": [],
        |  "paths": ["invalid..path"],
        |  "threshold": 1,
        |  "terminating": true
        |}
        |
      """.stripMargin

    val result = circeParser.parse(str).flatMap(_.as[Delegation]).toTry

    result.failed.get shouldBe a[DecodingFailure]

    result.failed.get.getMessage should include("be empty or bigger than 254 chars or contain `..`")
  }

  test("delegation role name codec fails when delegation name is invalid") {
    val str =
      """{
        |  "name": "",
        |  "keyids": [],
        |  "paths": ["validpath"],
        |  "threshold": 1,
        |  "terminating": true
        |}
        |
      """.stripMargin

    val result = circeParser.parse(str).flatMap(_.as[Delegation]).toTry

    result.failed.get shouldBe a[DecodingFailure]

    result.failed.get.getMessage should include(
      "name cannot be empty, bigger than 50 characters, or contain any special characters other than `_, -, .`"
    )
  }

  test("encodes a decoded targets.json to the same canonical json") {
    val result = circeParser.parse("""
        | {
        |  "_type": "Targets",
        |  "expires": "2018-12-13T15:37:21Z",
        |  "targets": {
        |    "myfile01": {
        |      "hashes": {
        |        "sha256": "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
        |      },
        |      "length": 2,
        |      "custom": null
        |    }
        |  },
        |  "version": 1,
        |  "delegations": {
        |    "keys": {},
        |    "roles": []
        |  }
        |}
      """.stripMargin)

    val targets = result.flatMap(_.as[TargetsRole]).valueOr(throw _)

    targets.asJson.canonical shouldBe result.valueOr(throw _).canonical
  }

  test("lossless TargetCustom codec") {
    val str = """{
                  "name" : "bananas",
                  "version" : "0.0.1",
                  "hardwareIds" : [
                    "archlinux"
                  ],
                  "targetFormat" : "BINARY",
                  "createdAt" : "2019-03-19T14:54:48Z",
                  "updatedAt" : "2019-03-19T14:54:48Z",
                  "additionalField": true
                 }"""

    val json = circeParser.parse(str).toOption.get
    val targetCustom = json.as[TargetCustom].toOption.get

    targetCustom.name shouldBe TargetName("bananas")
    targetCustom.uri shouldBe None
    targetCustom.proprietary shouldBe Json.obj("additionalField" -> Json.True)

    targetCustom.asJson.dropNullValues shouldBe json
  }

}
