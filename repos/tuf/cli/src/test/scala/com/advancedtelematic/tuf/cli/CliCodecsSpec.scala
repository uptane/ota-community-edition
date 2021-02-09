package com.advancedtelematic.tuf.cli
import org.scalatest.FunSuite
import CliCodecs._
import com.advancedtelematic.tuf.cli.DataType.RepoServer
import io.circe.Json
import io.circe.literal._

class CliCodecsSpec extends FunSuite {

  test("repo server type encoder") {
    assert(repoServerTypeCodec(RepoServer) == Json.fromString("RepoServer"))
  }

  test("decode config without repo type") {
    val missingRepoServerType: io.circe.Json =
        json"""{
                "reposerver" : "https://tuf-reposerver-pub.gw.staging.atsgarage.com:443",
                "auth" : {
                  "server" : "https://auth-plus.gw.staging.atsgarage.com:443",
                  "client_id" : "fc134fb9-2848-473b-9503-ed6bfd231ee9",
                  "client_secret" : "secret"
                },
                "treehub" : {
                  "oauth2" : {
                    "server" : "https://auth-plus.gw.staging.atsgarage.com:443",
                    "client_id" : "fc134fb9-2848-473b-9503-ed6bfd231ee9",
                    "client_secret" : "secret"
                  },
                  "no_auth" : false,
                  "ostree" : {
                    "server" : "https://treehub-pub.gw.staging.atsgarage.com:443/api/v3"
                  }
               }
        }"""

    assert(repoConfigCodec.decodeJson(missingRepoServerType).right.get.repoServerType == RepoServer)
  }
}
