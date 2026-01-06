package com.advancedtelematic.tuf.cli

import CliCodecs._
import com.advancedtelematic.tuf.cli.DataType.RepoServer
import io.circe.Json
import io.circe.literal._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.EitherValues._

class CliCodecsSpec extends AnyFunSuite {

  test("repo server type encoder") {
    assert(repoServerTypeCodec(RepoServer) == Json.fromString("RepoServer"))
  }

  test("decode config without repo type") {
    val missingRepoServerType: io.circe.Json =
      io.circe.parser
        .parse("""{
                "reposerver" : "https://tuf-reposerver-pub.gw.staging.atsgarage.com:443",
                "auth" : {
                  "server" : "https://ota-test.auth.eu-west-1.amazoncognito.com/oauth2/token",
                  "client_id" : "fc134fb9-2848-473b-9503-ed6bfd231ee9",
                  "client_secret" : "secret",
                  "scope" : "ota-treehub/namespaece.default"
                },
                "treehub" : {
                  "oauth2" : {
                    "server" : "https://ota-test.auth.eu-west-1.amazoncognito.com/oauth2/token",
                    "client_id" : "fc134fb9-2848-473b-9503-ed6bfd231ee9",
                    "client_secret" : "secret",
                    "scope" : "ota-treehub/namespaece.default"
                  },
                  "no_auth" : false,
                  "ostree" : {
                    "server" : "https://treehub-pub.gw.staging.atsgarage.com:443/api/v3"
                  }
               }
        }""")
        .value

    assert(
      repoConfigCodec.decodeJson(missingRepoServerType).toOption.get.repoServerType == RepoServer
    )
  }

}
