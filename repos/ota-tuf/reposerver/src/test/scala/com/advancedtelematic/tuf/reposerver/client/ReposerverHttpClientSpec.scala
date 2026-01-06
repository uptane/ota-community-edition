package com.advancedtelematic.tuf.reposerver.client

import java.nio.file.Files

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.scaladsl.{FileIO, Sink}
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.BINARY
import com.advancedtelematic.libtuf.data.TufDataType.{
  RepoId,
  TargetName,
  TargetVersion,
  ValidTargetFilename
}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest

import com.advancedtelematic.libtuf_server.repo.client.{ReposerverClient, ReposerverHttpClient}
import com.advancedtelematic.tuf.reposerver.util.NamespaceSpecOps.genNs
import com.advancedtelematic.tuf.reposerver.util._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.time.{Seconds, Span}

class ReposerverHttpClientSpec
    extends TufReposerverSpec
    with ResourceSpec
    with HttpClientSpecSupport
    with PatienceConfiguration
    with Eventually {

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig().copy(timeout = Span(30, Seconds))

  val client = new ReposerverHttpClient("http://localhost", testHttpClient)

  keyTypeTest("creates a root") { keyType =>
    val ns = genNs
    client.createRoot(ns, keyType).futureValue shouldBe a[RepoId]
    client.repoExists(ns).futureValue shouldBe true
  }

  keyTypeTest("fetches a root") { keyType =>
    val ns = genNs
    client.createRoot(ns, keyType).futureValue
    val (repoId, signedRoot) = client.fetchRoot(ns, None).futureValue
    repoId shouldBe a[RepoId]
    signedRoot.signed shouldBe a[RootRole]
    signedRoot.signed.keys.head._2.keytype shouldBe keyType
  }

  keyTypeTest("fails if role not on keyserver") { keyType =>
    val ns = genNs
    val repoId = client.createRoot(ns, keyType).futureValue
    fakeKeyserverClient.deleteRepo(repoId)
    client.fetchRoot(ns, None).failed.futureValue shouldBe ReposerverClient.RootNotInKeyserver
    client.repoExists(ns).futureValue shouldBe false
  }

  keyTypeTest("fails if keys not ready") { keyType =>
    val ns = genNs
    val repoId = client.createRoot(ns, keyType).futureValue
    fakeKeyserverClient.forceKeyGenerationPending(repoId)
    client.fetchRoot(ns, None).failed.futureValue shouldBe ReposerverClient.KeysNotReady
  }

  keyTypeTest("can add target") { keyType =>
    val ns = genNs
    client.createRoot(ns, keyType).futureValue shouldBe a[RepoId]

    client
      .targetExists(ns, "filename".refineTry[ValidTargetFilename].get)
      .futureValue shouldBe false

    client
      .addTarget(
        ns,
        "filename",
        Uri("http://example.com"),
        Sha256Digest.digest("hi".getBytes),
        42,
        BINARY
      )
      .futureValue shouldBe (())

    val validTargetFilename = "filename".refineTry[ValidTargetFilename].get

    client.targetExists(ns, validTargetFilename).futureValue shouldBe true

    client.fetchTargets(ns).futureValue.signed.targets.contains(validTargetFilename)
  }

  keyTypeTest("can add target with content") { keyType =>
    val ns = genNs
    val tempFile = Files.createTempFile("reposerver-client", ".txt")
    val text = "some string".getBytes
    Files.write(tempFile, text)

    val repoId = client.createRoot(ns, keyType).futureValue
    val content = FileIO.fromPath(tempFile)

    client
      .addTargetFromContent(
        ns,
        "myfilename",
        text.length,
        BINARY,
        content,
        TargetName("fakename"),
        TargetVersion("0.0.0")
      )
      .futureValue shouldBe (())

    val bytes = targetStore
      .retrieve(repoId, "myfilename".refineTry[ValidTargetFilename].get)
      .flatMap {
        _.entity.dataBytes.runWith(Sink.reduce[ByteString](_ ++ _))
      }
      .futureValue

    bytes.utf8String shouldBe "some string"
  }

  keyTypeTest("can't add target if keys are not in keyserver") { keyType =>
    val ns = genNs
    val repoId = client.createRoot(ns, keyType).futureValue
    fakeKeyserverClient.forceKeyGenerationPending(repoId)

    client
      .addTarget(
        ns,
        "filename",
        Uri("http://example.com"),
        Sha256Digest.digest("hi".getBytes),
        42,
        BINARY
      )
      .failed
      .futureValue shouldBe ReposerverClient.KeysNotReady
  }

  test("can't add target to nonexistant repo") {
    client
      .addTarget(
        Namespace("non-existant-namespace"),
        "filename",
        Uri("http://example.com"),
        Sha256Digest.digest("hi".getBytes),
        42,
        BINARY
      )
      .failed
      .futureValue shouldBe ReposerverClient.NotFound
  }

  keyTypeTest("fetching targets for non-existing repo leads to error") { keyType =>
    val ns = genNs

    client.fetchTargets(ns).failed.futureValue shouldBe ReposerverClient.NotFound
  }

  keyTypeTest("fetches empty targets") { keyType =>
    val ns = genNs
    client.createRoot(ns, keyType).futureValue shouldBe a[RepoId]

    client.fetchTargets(ns).futureValue.signed.targets shouldBe Symbol("empty")
  }

}
