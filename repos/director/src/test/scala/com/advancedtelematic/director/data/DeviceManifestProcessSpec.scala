package com.advancedtelematic.director.data

import com.advancedtelematic.director.manifest.DeviceManifestProcess.{fromJson, latestVersion, manifestVersion}
import com.advancedtelematic.libtuf.crypt.SignedPayloadSignatureOps._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{RsaKeyType, SignedPayload}
import io.circe.Json
import io.circe.parser._
import org.scalatest.FunSuite

import scala.io.Source

class DeviceManifestProcessSpec extends FunSuite {
  import java.security.Security

  import org.bouncycastle.jce.provider.BouncyCastleProvider

  Security.addProvider(new BouncyCastleProvider)

  def forAllVersions(f: (Int, SignedPayload[Json]) => Unit): Unit = {
    for (v <- 1 to latestVersion) {
      val manifest = Source.fromResource(s"device_manifests/v$v/manifest.json").getLines.mkString("\n")
      val signedPayload = parse(manifest).right.get.as[SignedPayload[Json]].right.get
      f(v, signedPayload)
    }
  }

  test("signatures") {
    forAllVersions { (version, signedPayload) =>
      val pem = Source.fromResource(s"device_manifests/v$version/rsa-key.pem").getLines.mkString("\n")
      val pub = TufCrypto.parsePublic(RsaKeyType, pem).get
      signedPayload.isValidFor(pub)
    }
  }

  test("versions") {
    forAllVersions { (version, signedPayload) =>
      assert(manifestVersion(signedPayload.json) == version)
    }
  }

  test("parsing") {
    forAllVersions { (_, signedPayload) =>
      assert(fromJson(signedPayload.json).isValid)
    }
  }
}
