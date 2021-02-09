package com.advancedtelematic.tuf.cli

import java.net.URI
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.syntax.either._
import cats.syntax.option._
import com.advancedtelematic.libats.data.DataType.{HashMethod, ValidChecksum}
import com.advancedtelematic.libtuf.crypt.SignedPayloadSignatureOps._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole._
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, RootRole, TargetCustom, TargetsRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, RoleType, SignedPayload, TargetFilename, TargetFormat, TargetName, TargetVersion, ValidTargetFilename}
import com.advancedtelematic.tuf.cli.DataType.KeyName
import com.advancedtelematic.tuf.cli.repo.RepoServerRepo
import com.advancedtelematic.tuf.cli.repo.TufRepo.{RoleMissing, RootPullError}
import com.advancedtelematic.tuf.cli.util.{CliSpec, FakeReposerverTufServerClient, KeyTypeSpecSupport}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import io.circe.jawn._
import io.circe.syntax._
import org.scalactic.source.Position

import scala.util.Success

class TufRepoSpec extends CliSpec with KeyTypeSpecSupport {

  import com.advancedtelematic.tuf.cli.util.TufRepoInitializerUtil._

  def reposerverTest(name: String)(fn: (RepoServerRepo, FakeReposerverTufServerClient) => Any)(implicit pos: Position): Unit = {
    keyTypeTest(name) { keyType => fn(initRepo[RepoServerRepo](keyType), new FakeReposerverTufServerClient(keyType)) }
  }

  val fakeTargetFilename = refineV[ValidTargetFilename]("fake-one-1.2.3").right.get

  val fakeTargetItem: ClientTargetItem = {
    val name = TargetName("fake-one")
    val version = TargetVersion("1.2.3")

    val custom = TargetCustom(name, version, Seq.empty, Option(TargetFormat.BINARY), Option(URI.create("https://ats.com")))
    val clientHashes = Map(HashMethod.SHA256 -> refineV[ValidChecksum]("03aa3f5e2779b625a455651b54866447f995a2970d164581b4073044435359ed").right.get)

    ClientTargetItem(clientHashes, length = 100, custom = Option(custom.asJson))
  }

  reposerverTest("adds a key to a repo ") { (repo, _) =>
    repo.genKeys(KeyName("newkey"), KeyType.default)
    Files.exists(repo.repoPath.resolve("keys").resolve("newkey.pub")) shouldBe true
  }

  test("initTargets creates an empty target") {
    val now = Instant.now
    val repo = initRepo[RepoServerRepo]

    val path = repo.initTargets(20, now.plusSeconds(1)).get
    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    role.targets should be(empty)

    role.expires.isAfter(now) shouldBe true
    role.version shouldBe 20
  }

  test("fails if target does not exist") {
    val repo = initRepo[RepoServerRepo]()

    val targetFilename = refineV[ValidTargetFilename]("fake-one-1.2.3").right.get

    val path = repo.deleteTarget(targetFilename)

    path.failed.get shouldBe a[IllegalArgumentException]
  }

  test("deletes an existing target targets") {
    val repo = initRepo[RepoServerRepo]

    repo.addTarget(fakeTargetFilename, fakeTargetItem).get

    val path = repo.deleteTarget(fakeTargetFilename).get

    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    role.targets.keys shouldNot contain(fakeTargetFilename)
  }

  keyTypeTest("adds a target to an existing targets") { keyType =>
    val repo = initRepo[RepoServerRepo](keyType)

    val path = repo.addTarget(fakeTargetFilename, fakeTargetItem).get
    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    role.targets.keys.map(_.value) should contain("fake-one-1.2.3")
    role.targets.values.head.customParsed[TargetCustom].flatMap(_.uri) should contain(new URI("https://ats.com"))
  }

  test("adds a target to an existing targets with specified format") {
    val repo = initRepo[RepoServerRepo]()

    val custom = fakeTargetItem.customParsed[TargetCustom].get.copy(targetFormat = TargetFormat.OSTREE.some).asJson
    val path = repo.addTarget(fakeTargetFilename, fakeTargetItem.copy(custom = custom.some)).get
    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    val format = role.targets.get(fakeTargetFilename).flatMap(_.customParsed[TargetCustom]).flatMap(_.targetFormat)
    format should contain(TargetFormat.OSTREE)
  }

  private def defaultExpiration(i: Instant) = Instant.now().plusSeconds(60)

  test("bumps version when signing targets role") {
    val repo = initRepo[RepoServerRepo]()
    val previousExpires = repo.readUnsignedRole[TargetsRole].get.expires

    val targetsKeyName = KeyName("somekey")
    repo.genKeys(targetsKeyName, KeyType.default).get

    repo.signTargets(Seq(targetsKeyName), defaultExpiration).get

    val payload = repo.readSignedRole[TargetsRole].get
    payload.signed.version shouldBe 12
    payload.signed.expires should be > previousExpires
  }

  test("sets version when specified ") {
    val repo = initRepo[RepoServerRepo]()

    val targetsKeyName = KeyName("somekey")
    repo.genKeys(targetsKeyName, KeyType.default).get

    repo.signTargets(Seq(targetsKeyName), defaultExpiration, Option(21)).get

    val payload = repo.readSignedRole[TargetsRole].get
    payload.signed.version shouldBe 21
  }

  keyTypeTest("signs targets") { keyType =>
    val repo = initRepo[RepoServerRepo](keyType)
    val targetsKeyName = KeyName("somekey")
    val pub = repo.genKeys(targetsKeyName, keyType).get.pubkey

    val path = repo.signTargets(Seq(targetsKeyName), defaultExpiration).get
    val payload = parseFile(path.toFile).flatMap(_.as[SignedPayload[TargetsRole]]).valueOr(throw _)

    payload.signatures.map(_.keyid) should contain(pub.id)

    payload.isValidFor(pub) shouldBe true
  }

  reposerverTest("saves targets.json and checksum to file when pulling") { (repo, client) =>
    val rootRole = client.root().futureValue

    repo.pullVerifyTargets(client, rootRole.signed).futureValue

    repo.readUnsignedRole[TargetsRole].get shouldBe a[TargetsRole]

    Files.readAllLines(repo.repoPath.resolve("roles/targets.json.checksum")).get(0) shouldNot be(empty)
  }

  reposerverTest("can pull a root.json when no local root is available, when forcing") { (repo, client) =>
    val newRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    val signed = repo.readSignedRole[RootRole]
    signed shouldBe a[Success[_]]

    signed.get.asJson shouldBe newRoot.asJson

    val rootRole = repo.readUnsignedRole[RootRole]
    rootRole.get.asJson shouldBe newRoot.signed.asJson
  }

  reposerverTest("adds root key to unsigned root") { (repo, _) =>
    val keyname = KeyName("somekey")
    val keyPair = repo.genKeys(keyname, KeyType.default).get

    repo.addRootKeys(List(keyname)).get

    val rootRole = repo.readUnsignedRole[RootRole].get

    rootRole.keys(keyPair.pubkey.id) shouldBe keyPair.pubkey
    rootRole.roles(RoleType.ROOT).keyids should contain(keyPair.pubkey.id)
  }

  reposerverTest("removes root key from unsigned root") { (repo, _) =>
    val keyname = KeyName("somekey")
    val keyPair = repo.genKeys(keyname, KeyType.default).get

    repo.addRootKeys(List(keyname)).get
    val keyIds = repo.keyIdsByName(List(KeyName("root"))).get
    repo.removeRootKeys(keyIds).get

    val rootRole = repo.readUnsignedRole[RootRole].get

    rootRole.roles(RoleType.ROOT).keyids shouldBe Seq(keyPair.pubkey.id)
  }

  reposerverTest("can remove keys using key ids") { (repo, _) =>
    val keyname = KeyName("somekey")
    val keyPair = repo.genKeys(keyname, KeyType.default).get
    val othername = KeyName("otherkey")
    val otherKeyPair = repo.genKeys(othername, KeyType.default).get

    repo.addRootKeys(List(keyname, othername))
    repo.removeRootKeys(List(keyPair.pubkey.id))

    val rootRole = repo.readUnsignedRole[RootRole].get

    rootRole.roles(RoleType.ROOT).keyids should contain(otherKeyPair.pubkey.id)
    rootRole.roles(RoleType.ROOT).keyids shouldNot contain(keyPair.pubkey.id)
  }

  reposerverTest("pull succeeds when new root.json is valid against local root.json") { (repo, server) =>
    val oldRoot = repo.pullRoot(server, userSkipsLocalValidation = true).futureValue

    val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + 1)
    val newRoot = server.sign(newUnsignedRoot)

    server.pushSignedRoot(newRoot).futureValue

    repo.pullRoot(server, userSkipsLocalValidation = false).futureValue
  }

  reposerverTest("pull fails when new root.json is not the same as old root but has same version numbers") { (repo, client) =>
    val oldSignedRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    val newRoot = oldSignedRoot.signed.copy(expires = Instant.now().plus(100, ChronoUnit.DAYS))
    client.setRoot(client.sign(newRoot))

    val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

    error shouldBe a[RootPullError]
    error.asInstanceOf[RootPullError].errors.head shouldBe "New root has same version as old root but is not the same root.json"
  }

  reposerverTest("pull succeeds when new root.json is the same as old json") { (repo, client) =>
    repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    repo.pullRoot(client, userSkipsLocalValidation = false).futureValue shouldBe a[SignedPayload[_]]
  }


  reposerverTest("pull fails when new root.json is not valid against local root.json") { (repo, client) =>
    val oldRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + 1)
    client.setRoot(SignedPayload(Seq.empty, newUnsignedRoot, newUnsignedRoot.asJson))

    val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

    val oldKeyId = oldRoot.signed.roles(RoleType.ROOT).keyids.head

    error shouldBe a[RootPullError]
    error.getMessage should include(s"No signature found for key $oldKeyId")
    error.getMessage should include(s"Root role version 1 requires 1 valid signatures in version 2, 0 supplied")
  }

  reposerverTest("fails with proper error when cannot find root at specified version") { (repo, client) =>
    val oldRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + 10)
    client.setRoot(SignedPayload(Seq.empty, newUnsignedRoot, newUnsignedRoot.asJson))

    val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

    error shouldBe a[RootPullError]
    error.getMessage should include(s"role with version 2 not found")
  }

  reposerverTest("validates a root chain") { (repo, client) =>
    val oldRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    for(i <- 1 until 10) {
      val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + i)
      val newRoot = client.sign(newUnsignedRoot)
      client.pushSignedRoot(newRoot).futureValue
    }

    val newRoot = repo.pullRoot(client, userSkipsLocalValidation = false).futureValue

    newRoot shouldBe a[SignedPayload[_]]
    newRoot.signed shouldBe a[RootRole]
  }

  reposerverTest("pull fails when local root does not exist") { (repo, client) =>
    val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

    error shouldBe a[RoleMissing[_]]
  }

  reposerverTest("can push root.json") { (repo, client) =>
    repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    repo.pushRoot(client).futureValue

    val signed = repo.readSignedRole[RootRole]
    signed shouldBe a[Success[_]]
  }

  keyTypeTest("signs root") { keyType =>
    val repo = initRepo[RepoServerRepo](keyType)

    val keyname = KeyName("somekey")
    val pub = repo.genKeys(keyname, keyType).get.pubkey

    val keyname02 = KeyName("somekey02")
    val pub02 = repo.genKeys(keyname02, keyType).get.pubkey

    val path = repo.signRoot(Seq(keyname, keyname02), defaultExpiration).get
    val payload = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).valueOr(throw _)

    payload.isValidFor(pub) shouldBe true
    payload.isValidFor(pub02) shouldBe true
  }

  reposerverTest("signing root increases version") { (repo, _) =>
    val keyname = KeyName("somekey")
    val _ = repo.genKeys(keyname, KeyType.default).get.pubkey

    val path = repo.signRoot(Seq(keyname), defaultExpiration).get
    val payload = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).valueOr(throw _)

    payload.signed.version shouldBe 2
  }
}
