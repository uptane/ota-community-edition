package com.advancedtelematic.tuf.cli

import java.nio.file.{Files, Paths}
import java.time.Instant

import scala.jdk.CollectionConverters._
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TargetsRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.{
  KeyType,
  RoleType,
  SignedPayload,
  TufKey,
  TufPrivateKey
}
import com.advancedtelematic.libtuf.http.{DirectorClient, ReposerverClient, TufServerClient}
import com.advancedtelematic.tuf.cli.DataType.KeyName
import com.advancedtelematic.tuf.cli.repo.{CliKeyStorage, DirectorRepo, RepoServerRepo, TufRepo}
import org.scalactic.source.Position
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.crypt.SignedPayloadSignatureOps._
import com.advancedtelematic.tuf.cli.util.{CliSpec, FakeReposerverTufServerClient}
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.tuf.cli.util.TufRepoInitializerUtil._
import com.advancedtelematic.tuf.cli.util.TufRepoNameOps._
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}

class TufRepoMoveOfflineSpec extends CliSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private def moveOfflineTest(name: String)(
    fn: (TufRepo[TufServerClient],
         FakeReposerverTufServerClient,
         => Future[(TufKey, TufKey, SignedPayload[RootRole])]) => Any)(
    implicit pos: Position): Unit = {

    test(name + " reposerver") {
      val repo = initRepo[RepoServerRepo](KeyType.default)
      val client = new FakeReposerverTufServerClient(KeyType.default)

      fn(
        repo.asInstanceOf[TufRepo[TufServerClient]],
        client,
        OfflineMover.reposerver(repo, KeyType.default, client)
      )
    }

    test(name + " director") {
      val repo = initRepo[DirectorRepo](KeyType.default)
      val client = new FakeReposerverTufServerClient(KeyType.default)

      fn(
        repo.asInstanceOf[TufRepo[TufServerClient]],
        client,
        OfflineMover.director(repo, KeyType.default, client)
      )
    }
  }

  moveOfflineTest("root after rotate contains new key ids") { (repo, client, moveOffline) =>
    val (pub, pubT, signedPayload) = moveOffline.futureValue

    signedPayload.signed shouldBe a[RootRole]
    signedPayload.signed.keys.keys should contain(pub.id)
    signedPayload.signed.keys.values should contain(pub)
    signedPayload.signed.keys.keys should contain(pubT.id)
    signedPayload.signed.keys.values should contain(pubT)
  }

  moveOfflineTest("root after rotate is properly signed") { (repo, client, moveOffline) =>
    val oldRoot = client.root().futureValue.signed
    val oldRootPubKeyId = oldRoot.roles(RoleType.ROOT).keyids.head
    val oldRootPub = oldRoot.keys(oldRootPubKeyId)

    val (pub, pubT, signedPayload) = moveOffline.futureValue

    signedPayload.isValidFor(pub)
    signedPayload.isValidFor(oldRootPub)
  }

  moveOfflineTest("rotate key is signed by both root keys") { (repo, client, moveOffline) =>
    val keyStorage = CliKeyStorage.forRepo(repo.repoPath)
    val (newPubKey, _, signedPayload) = moveOffline.futureValue
    val oldPubKey = keyStorage.readPublicKey(KeyName(s"oldroot${repo.name}")).get

    signedPayload.isValidFor(newPubKey) shouldBe true
    signedPayload.isValidFor(oldPubKey) shouldBe true
  }

  moveOfflineTest("saves deleted root when rotating") { (repo, client, moveOffline) =>
    val keyStorage = CliKeyStorage.forRepo(repo.repoPath)
    moveOffline.futureValue
    val oldPrivateKey = keyStorage.readPrivateKey(KeyName(s"oldroot${repo.name}")).get

    oldPrivateKey shouldBe a[TufPrivateKey]
  }

  test("reposerver repo does not overwrite existing unsigned targets.json during rotate") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)
    val signedTargets = repo.readUnsignedRole[TargetsRole].get

    OfflineMover.reposerver(repo, KeyType.default, client).futureValue

    repo.readUnsignedRole[TargetsRole].get.asJson shouldBe signedTargets.asJson
  }

  test("new root role has proper version bump") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)

    val (pub, pubT, signedPayload) =
      OfflineMover.reposerver(repo, KeyType.default, client).futureValue

    val rootRole = signedPayload.signed

    rootRole.version shouldBe 2
  }

  test("pushed targets are validated against new targets key when moving root offline") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)
    val (_, pubTargets, _) = OfflineMover.reposerver(repo, KeyType.default, client).futureValue
    repo.signTargets(Seq(KeyName(s"targets${repo.name}")), _ => Instant.now().plusSeconds(60)).get
    Files.write(
      repo.repoPath.resolve("roles").resolve(TufRole.targetsTufRole.checksumPath),
      Seq("997890bc85c5796408ceb20b0ca75dabe6fe868136e926d24ad0f36aa424f99d").asJava
    )

    val payload = repo.pushTargets(client).futureValue

    payload.isValidFor(pubTargets) shouldBe true
  }

  test("new root role contains new root id") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = new FakeReposerverTufServerClient(KeyType.default)
    val (pub, pubT, signedPayload) =
      OfflineMover.reposerver(repo, KeyType.default, client).futureValue
    val rootRole = signedPayload.signed

    rootRole.roles(RoleType.ROOT).keyids should contain(pub.id)
    rootRole.roles(RoleType.TARGETS).keyids should contain(pubT.id)

    rootRole.keys.keys should contain(pub.id)
    rootRole.keys.keys should contain(pubT.id)
  }

  test("new root role does not contain old targets keys") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)
    val oldTargetsKeyId =
      client.root().map(_.signed.roles(RoleType.TARGETS).keyids.head).futureValue
    val (_, pubT, signedPayload) =
      OfflineMover.reposerver(repo, KeyType.default, client).futureValue
    val rootRole = signedPayload.signed

    rootRole.keys.keys should contain(pubT.id)
    rootRole.keys.keys shouldNot contain(oldTargetsKeyId)
  }

  test("pulls targets.json from reposerver during rotate") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)

    Files.delete(repo.repoPath.resolve("roles/unsigned/targets.json"))

    val signedTargets = client.targets().futureValue

    OfflineMover.reposerver(repo, KeyType.default, client).futureValue

    repo.readUnsignedRole[TargetsRole].get.asJson shouldBe signedTargets.targets.signed.asJson

    repo.repoPath.resolve("roles/targets.json.checksum").toFile.exists() shouldBe true
  }

  test("doesn't save unsigned roles when root key is supplied") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)

    Files.delete(repo.repoPath.resolve("roles/unsigned/root.json"))

    val result = OfflineMover.reposerver(repo, KeyType.default, client)

    ScalaFutures.whenReady(result) { _ =>
      repo.repoPath.resolve("roles/unsigned/root.json").toFile.exists shouldBe false
    }
  }

  test("stops before signing and pushing when root key is not supplied reposerver") {
    val repo = initRepo[RepoServerRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)
    val result = OfflineMover.reposerverWithoutRoot(repo, client)

    ScalaFutures.whenReady(result.failed) { e =>
      e shouldBe a[NoSuchElementException]
    }
  }

  test("stops before signing and pushing when root key is not supplied director") {
    val repo = initRepo[DirectorRepo](KeyType.default)
    val client = FakeReposerverTufServerClient(KeyType.default)
    val result = OfflineMover.directorWithoutRoot(repo, client)

    ScalaFutures.whenReady(result.failed) { e =>
      e shouldBe a[NoSuchElementException]
    }
  }

}

private object OfflineMover {

  def reposerverWithoutRoot(repo: TufRepo[ReposerverClient], client: FakeReposerverTufServerClient)(
    implicit ec: ExecutionContext): Future[SignedPayload[RootRole]] = {
    val oldRootName = KeyName(s"oldroot${repo.name}")
    repo.moveRootOffline(client, None, oldRootName, None, None, Instant.now().plusSeconds(60))
  }

  def directorWithoutRoot(repo: TufRepo[DirectorClient], client: FakeReposerverTufServerClient)(
    implicit ec: ExecutionContext): Future[SignedPayload[RootRole]] = {
    val oldRootName = KeyName(s"oldroot${repo.name}")

    repo.moveRootOffline(client, None, oldRootName, None, None, Instant.now().plusSeconds(60))
  }

  def reposerver(repo: TufRepo[ReposerverClient],
                 keyType: KeyType,
                 client: FakeReposerverTufServerClient)(
    implicit ec: ExecutionContext): Future[(TufKey, TufKey, SignedPayload[RootRole])] = {
    val oldRootName = KeyName(s"oldroot${repo.name}")
    // newRootName is optional for `move-offline` and required for `generate key`
    val newRootName = Some(KeyName(s"newroot${repo.name}"))
    val newTargetsName = KeyName(s"targets${repo.name}")

    val pub = repo.genKeys(newRootName.get, keyType).get.pubkey
    val pubT = repo.genKeys(newTargetsName, keyType).get.pubkey

    repo
      .moveRootOffline(
        client,
        newRootName,
        oldRootName,
        None,
        Option(newTargetsName),
        Instant.now().plusSeconds(60)
      )
      .map(s => (pub, pubT, s))
  }

  def director(repo: TufRepo[DirectorClient],
               keyType: KeyType,
               client: FakeReposerverTufServerClient)(
    implicit ec: ExecutionContext): Future[(TufKey, TufKey, SignedPayload[RootRole])] = {
    val oldRootName = KeyName(s"oldroot${repo.name}")
    // newRootName is optional for `move-offline` and required for `generate key`
    val newRootName = Some(KeyName(s"newroot${repo.name}"))

    val pub = repo.genKeys(newRootName.get, keyType).get.pubkey

    repo
      .moveRootOffline(client, newRootName, oldRootName, None, None, Instant.now().plusSeconds(60))
      .flatMap { s =>
        client
          .fetchKeyPair(s.signed.roles(RoleType.TARGETS).keyids.head)
          .map(_.pubkey)
          .map(pubT => (pub, pubT, s))
      }
  }

}
