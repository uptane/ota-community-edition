package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.model.Multipart.FormData.BodyPart
import org.apache.pekko.http.scaladsl.model.{HttpEntity, Multipart, StatusCodes, Uri}
import com.advancedtelematic.tuf.reposerver.util.NamespaceSpecOps._
import org.apache.pekko.testkit.TestProbe
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType.HashMethod
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientHashes,
  ClientTargetItem,
  TargetCustom,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  KeyType,
  RepoId,
  RsaKeyType,
  SignedPayload,
  TargetFilename,
  TargetFormat,
  TargetName,
  TargetVersion
}
import com.advancedtelematic.libtuf_server.data.Messages.{
  PackageStorageUsage,
  TufTargetAdded,
  TufTargetsModified
}
import com.advancedtelematic.tuf.reposerver.util.{
  RepoResourceDelegationsSpecUtil,
  RepoResourceSpecUtil,
  ResourceSpec,
  TufReposerverSpec
}
import eu.timepit.refined.api.Refined
import io.circe.Json
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import cats.syntax.show._
import cats.syntax.option._
import io.circe.syntax._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libats.http.HttpCodecs._
import com.advancedtelematic.libtuf_server.data.Requests.CreateRepositoryRequest

class RepoResourceBusMessagesSpec
    extends TufReposerverSpec
    with ResourceSpec
    with PatienceConfiguration
    with RepoResourceSpecUtil
    with RepoResourceDelegationsSpecUtil {

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig().copy(timeout = Span(5, Seconds))

  test("publishes messages to bus for new target entries") {
    val probe = TestProbe()
    memoryMessageBus.subscribe[TufTargetAdded](probe.testActor)

    val repoId = addTargetToRepo()
    // check for target item

    probe.expectMsgType[TufTargetAdded]
    probe.expectMsgType[TufTargetsModified]

    val signedPayload1 = buildSignedTargetsRole(repoId, offlineTargets)

    // 2nd target item
    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload1)
      .withHeaders(makeRoleChecksumHeader(repoId)) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
      header("x-ats-role-checksum").map(_.value) should contain(
        makeRoleChecksumHeader(repoId).value
      )
    }

    probe.expectMsgType[TufTargetAdded]
    probe.expectMsgType[TufTargetsModified]

    val targetRole = Get(apiUri(s"repo/${repoId.show}/targets.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val targetR = responseAs[SignedPayload[TargetsRole]].signed
      targetR.targets.keys should contain(offlineTargetFilename)
      header("x-ats-role-checksum").map(_.value) should contain(
        makeRoleChecksumHeader(repoId).value
      )
      targetR
    }

    // 3rd target item
    val hashes: ClientHashes = Map(
      HashMethod.SHA256 -> Refined
        .unsafeApply("8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4")
    )
    val targetCustomJson = TargetCustom(
      TargetName("name"),
      TargetVersion("version"),
      Seq.empty,
      TargetFormat.BINARY.some
    ).asJson
      .deepMerge(Json.obj("uri" -> Uri("https://ats.com").asJson))
    val offlineTargetFilename2: TargetFilename = Refined.unsafeApply("another/file/name")
    val offlineTargets2 = targetRole.targets.updated(
      offlineTargetFilename2,
      ClientTargetItem(hashes, 0, Some(targetCustomJson))
    )
    val signedPayload2 = buildSignedTargetsRole(repoId, offlineTargets2, targetRole.version + 1)

    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload2)
      .withHeaders(makeRoleChecksumHeader(repoId)) ~> routes ~> check {
      header("x-ats-role-checksum").map(_.value) should contain(
        makeRoleChecksumHeader(repoId).value
      )
      status shouldBe StatusCodes.NoContent
    }

    probe.expectMsgType[TufTargetAdded]
    probe.expectMsgType[TufTargetsModified]
  }

  test("publishes messages to bus on creating first target by uploading target.json") {
    val probe = TestProbe()
    memoryMessageBus.subscribe[TufTargetAdded](probe.testActor)
    val repoId = RepoId.generate()

    withNamespace(s"default-${repoId.show}") { implicit ns =>
      Post(apiUri(s"repo/${repoId.show}")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      val signedPayload = buildSignedTargetsRole(repoId, offlineTargets)

      Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload) ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
        header("x-ats-role-checksum").map(_.value) should contain(
          makeRoleChecksumHeader(repoId).value
        )
      }

      probe.expectMsgType[TufTargetAdded]
      probe.expectMsgType[TufTargetsModified]
    }
  }

  keyTypeTest("publishes messages to bus on adding target") { keyType =>
    val repoId = RepoId.generate()
    val subscriber = TestProbe()
    memoryMessageBus.subscribe[TufTargetAdded](subscriber.testActor)

    withNamespace(s"default-${repoId.show}") { implicit ns =>
      Post(
        apiUri(s"repo/${repoId.show}"),
        CreateRepositoryRequest(keyType)
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      Post(apiUri(s"repo/${repoId.show}/targets/myfile"), testFile) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      subscriber.expectMsgType[TufTargetAdded]
      subscriber.expectMsgType[TufTargetsModified]
    }
  }

  keyTypeTest("publishes usage messages to bus") { keyType =>
    val testEntity = HttpEntity(ByteString("""
        |Like all the men of the Library, in my younger days I traveled;
        |I have journeyed in quest of a book, perhaps the catalog of catalogs.
        |""".stripMargin))

    val fileBodyPart = BodyPart("file", testEntity, Map("filename" -> "babel.txt"))
    val form = Multipart.FormData(fileBodyPart)

    val repoId = RepoId.generate()
    val probe = TestProbe()
    memoryMessageBus.subscribe[PackageStorageUsage](probe.testActor)

    withNamespace(s"default-${repoId.show}") { implicit ns =>
      Post(
        apiUri(s"repo/${repoId.show}"),
        CreateRepositoryRequest(keyType)
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      Put(
        apiUri(s"repo/${repoId.show}/targets/some/file?name=pkgname&version=pkgversion&desc=wat"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }

      probe.expectMsgType[PackageStorageUsage]
      probe.expectMsgType[TufTargetAdded]
      probe.expectMsgType[TufTargetsModified]
    }
  }

  // Add trusted delegation keys
  test("publishes messages to bus on adding trusted delegation keys to target.json") {
    val repoId = addTargetToRepo()
    val probe = TestProbe()
    memoryMessageBus.subscribe[TufTargetsModified](probe.testActor)
    Put(
      apiUri(s"repo/${repoId.show}/trusted-delegations/keys"),
      List(keyPair.pubkey.asJson)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    probe.expectMsgType[TufTargetsModified]
  }

  // Add trusted delegations
  test("publishes messages to bus when adding trusted delegations to target.json") {
    val repoId = addTargetToRepo()
    Put(
      apiUri(s"repo/${repoId.show}/trusted-delegations/keys"),
      List(keyPair.pubkey.asJson)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    val probe = TestProbe()
    memoryMessageBus.subscribe[TufTargetsModified](probe.testActor)
    addNewTrustedDelegations(delegation)(repoId)
    probe.expectMsgType[TufTargetsModified]
  }

  // Delete trusted delegations
  test("publishes messages to bus when deleting trusted delegations from targets.json") {
    val repoId = addTargetToRepo()
    Put(
      apiUri(s"repo/${repoId.show}/trusted-delegations/keys"),
      List(keyPair.pubkey.asJson)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    addNewTrustedDelegations(delegation)(repoId) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    val probe = TestProbe()
    memoryMessageBus.subscribe[TufTargetsModified](probe.testActor)
    Delete(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegation.name.value}")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    probe.expectMsgType[TufTargetsModified]
  }

  // Delete target
  test("publishes messages to bus when deleting target from targets.json") {
    val repoId = addTargetToRepo()
    val probe = TestProbe()
    memoryMessageBus.subscribe[TufTargetsModified](probe.testActor)
    Delete(apiUri(s"repo/${repoId.show}/targets/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    probe.expectMsgType[TufTargetsModified]
  }

}
