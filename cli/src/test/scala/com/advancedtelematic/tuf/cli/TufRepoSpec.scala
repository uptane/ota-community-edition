package com.advancedtelematic.tuf.cli

import java.net.URI
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import com.advancedtelematic.libats.data.DataType.{HashMethod, ValidChecksum}
import com.advancedtelematic.libtuf.crypt.SignedPayloadSignatureOps._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole._
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  RootRole,
  TargetCustom,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.RoleType._
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  KeyType,
  RoleType,
  RsaKeyType,
  SignedPayload,
  TargetFormat,
  TargetName,
  TargetVersion,
  ValidSignature,
  ValidTargetFilename
}
import com.advancedtelematic.tuf.cli.DataType.KeyName
import com.advancedtelematic.tuf.cli.repo.{CliKeyStorage, RepoServerRepo}
import com.advancedtelematic.tuf.cli.repo.TufRepo.{RoleMissing, RootPullError}
import com.advancedtelematic.tuf.cli.util.{
  CliSpec,
  FakeReposerverTufServerClient,
  KeyTypeSpecSupport
}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import io.circe.jawn._
import io.circe.syntax._
import org.scalactic.source.Position
import org.scalatest.{EitherValues, TryValues}
import cats.implicits._
import EitherValues._
import scala.util.Success

class TufRepoSpec extends CliSpec with KeyTypeSpecSupport with TryValues with EitherValues {

  import com.advancedtelematic.tuf.cli.util.TufRepoInitializerUtil._

  def reposerverTest(name: String)(fn: (RepoServerRepo, FakeReposerverTufServerClient) => Any)(
    implicit pos: Position): Unit =
    keyTypeTest(name) { keyType =>
      fn(initRepo[RepoServerRepo](keyType), new FakeReposerverTufServerClient(keyType))
    }

  val fakeTargetFilename = refineV[ValidTargetFilename]("fake-one-1.2.3").toOption.get

  val fakeTargetItem: ClientTargetItem = {
    val name = TargetName("fake-one")
    val version = TargetVersion("1.2.3")

    val custom = TargetCustom(
      name,
      version,
      Seq.empty,
      Option(TargetFormat.BINARY),
      Option(URI.create("https://ats.com"))
    )
    val clientHashes = Map(
      HashMethod.SHA256 -> refineV[ValidChecksum](
        "03aa3f5e2779b625a455651b54866447f995a2970d164581b4073044435359ed"
      ).toOption.get
    )

    ClientTargetItem(clientHashes, length = 100, custom = Option(custom.asJson))
  }

  reposerverTest("adds a key to a repo ") { (repo, _) =>
    repo.genKeys(KeyName("newkey"), KeyType.default)
    Files.exists(repo.repoPath.resolve("keys").resolve("newkey.pub")) shouldBe true
  }

  test("initTargets creates an empty target") {
    val now = Instant.now
    val repo = initRepo[RepoServerRepo]()

    val path = repo.initTargets(20, now.plusSeconds(1)).get
    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    role.targets should be(empty)

    role.expires.isAfter(now) shouldBe true
    role.version shouldBe 20
  }

  test("fails if target does not exist") {
    val repo = initRepo[RepoServerRepo]()

    val targetFilename = refineV[ValidTargetFilename]("fake-one-1.2.3").toOption.get

    val path = repo.deleteTarget(targetFilename)

    path.failed.get shouldBe a[IllegalArgumentException]
  }

  test("deletes an existing target targets") {
    val repo = initRepo[RepoServerRepo]()

    repo.addTarget(fakeTargetFilename, fakeTargetItem).get

    val path = repo.deleteTarget(fakeTargetFilename).get

    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).value

    role.targets.keys shouldNot contain(fakeTargetFilename)
  }

  keyTypeTest("adds a target to an existing targets") { keyType =>
    val repo = initRepo[RepoServerRepo](keyType)

    val path = repo.addTarget(fakeTargetFilename, fakeTargetItem).get
    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    role.targets.keys.map(_.value) should contain("fake-one-1.2.3")
    role.targets.values.head.customParsed[TargetCustom].flatMap(_.uri) should contain(
      new URI("https://ats.com")
    )
  }

  test("adds a target to an existing targets with specified format") {
    val repo = initRepo[RepoServerRepo]()

    val custom = fakeTargetItem
      .customParsed[TargetCustom]
      .get
      .copy(targetFormat = TargetFormat.OSTREE.some)
      .asJson
    val path = repo.addTarget(fakeTargetFilename, fakeTargetItem.copy(custom = custom.some)).get
    val role = parseFile(path.toFile).flatMap(_.as[TargetsRole]).valueOr(throw _)

    val format = role.targets
      .get(fakeTargetFilename)
      .flatMap(_.customParsed[TargetCustom])
      .flatMap(_.targetFormat)
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
    val payload = parseFile(path.toFile).flatMap(_.as[SignedPayload[TargetsRole]]).value

    payload.signatures.map(_.keyid) should contain(pub.id)

    payload.isValidFor(pub) shouldBe true
  }

  test("add external RSA signature to targets") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val targetsKeyName = KeyName("somekey")
    val targetsKeyPair = repo.genKeys(targetsKeyName, RsaKeyType).get

    val unsignedTargets = repo.readUnsignedRole[TargetsRole].get
    val signature = TufCrypto.signPayload(targetsKeyPair.privkey, unsignedTargets.asJson).sig

    // signTargets() below expects a signed root
    repo.addRoleKeys(RoleType.ROOT, List(targetsKeyName)).success
    repo.signRoot(Seq(KeyName("root")), defaultExpiration).success

    repo
      .signTargets(
        Seq.empty,
        defaultExpiration,
        signatures = Some(Map(targetsKeyName -> signature))
      )
      .get
  }

  test("add external ED25519 signature to targets") {
    val repo = initRepo[RepoServerRepo](Ed25519KeyType)
    val targetsKeyName = KeyName("ed25519key")
    val targetsKeyPair = repo.genKeys(targetsKeyName, Ed25519KeyType).get

    val unsignedTargets = repo.readUnsignedRole[TargetsRole].get
    val signature = TufCrypto.signPayload(targetsKeyPair.privkey, unsignedTargets.asJson).sig

    // signTargets() below expects a signed root
    repo.addRoleKeys(RoleType.ROOT, List(targetsKeyName)).success
    repo.signRoot(Seq(KeyName("root")), defaultExpiration).success

    repo
      .signTargets(
        Seq.empty,
        defaultExpiration,
        signatures = Some(Map(targetsKeyName -> signature))
      )
      .get
  }

  test("add multiple external RSA signatures to targets") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val targetsKey1Name = KeyName("somekey")
    val targetsKey2Name = KeyName("someotherkey")
    val targetsKey1Pair = repo.genKeys(targetsKey1Name, RsaKeyType).success.value
    val targetsKey2Pair = repo.genKeys(targetsKey2Name, RsaKeyType).success.value

    val unsignedTargets = repo.readUnsignedRole[TargetsRole].success.value
    val signature1 = TufCrypto.signPayload(targetsKey1Pair.privkey, unsignedTargets.asJson).sig
    val signature2 = TufCrypto.signPayload(targetsKey2Pair.privkey, unsignedTargets.asJson).sig

    // signTargets() below expects a signed root
    repo.addRoleKeys(RoleType.ROOT, List(targetsKey1Name)).success
    repo.signRoot(Seq(KeyName("root")), defaultExpiration).success

    val signatures = Map(targetsKey1Name -> signature1, targetsKey2Name -> signature2)
    repo.signTargets(Seq.empty, defaultExpiration, signatures = Some(signatures)).success
  }

  test("add multiple external ED25519 signatures to targets") {
    val repo = initRepo[RepoServerRepo](Ed25519KeyType)
    val targetsKey1Name = KeyName("somekey")
    val targetsKey2Name = KeyName("someotherkey")
    val targetsKey1Pair = repo.genKeys(targetsKey1Name, Ed25519KeyType).success.value
    val targetsKey2Pair = repo.genKeys(targetsKey2Name, Ed25519KeyType).success.value

    val unsignedTargets = repo.readUnsignedRole[TargetsRole].success.value
    val signature1 = TufCrypto.signPayload(targetsKey1Pair.privkey, unsignedTargets.asJson).sig
    val signature2 = TufCrypto.signPayload(targetsKey2Pair.privkey, unsignedTargets.asJson).sig

    // signTargets() below expects a signed root
    repo.addRoleKeys(RoleType.ROOT, List(targetsKey1Name)).success
    repo.signRoot(Seq(KeyName("root")), defaultExpiration).success

    val signatures = Map(targetsKey1Name -> signature1, targetsKey2Name -> signature2)
    repo.signTargets(Seq.empty, defaultExpiration, signatures = Some(signatures)).success
  }

  test("compare old and new 'targets sign'") {
    // sign targets the old way
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val targetsKeyName = KeyName("somekey")
    repo.genKeys(targetsKeyName, RsaKeyType).success.value

    val path = repo.signTargets(Seq(targetsKeyName), defaultExpiration).get
    val targetsJson = parseFile(path.toFile).flatMap(_.as[SignedPayload[TargetsRole]]).value
    // signing has bumped the version, so update the unsigned targets.json
    repo.writeUnsignedRole[TargetsRole](targetsJson.signed)

    targetsJson.signatures.length shouldBe 1
    val signature = targetsJson.signatures.head.sig

    // signTargets() below expects a signed root
    repo.addRoleKeys(RoleType.ROOT, List(targetsKeyName)).get
    repo.signRoot(Seq(KeyName("root")), defaultExpiration).get

    val newPath = repo
      .signTargets(
        Seq.empty,
        defaultExpiration,
        signatures = Some(Map(targetsKeyName -> signature))
      )
      .get
    newPath shouldBe path
    val newTargetsJson = parseFile(path.toFile).flatMap(_.as[SignedPayload[TargetsRole]]).value
    newTargetsJson shouldBe targetsJson
  }

  test("cannot add invalid signature to targets") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val targetsKey1Name = KeyName("somekey")
    val targetsKey2Name = KeyName("someotherkey")
    val targetsKey1Pair = repo.genKeys(targetsKey1Name, RsaKeyType).success.value
    val targetsKey2Pair = repo.genKeys(targetsKey2Name, RsaKeyType).success.value

    val wrongSignature = Refined.unsafeApply[String, ValidSignature](
      Base64.getEncoder.encodeToString("wrong signature".getBytes)
    )
    val unsignedTargets = repo.readUnsignedRole[TargetsRole].success.value
    val signature2 = TufCrypto.signPayload(targetsKey2Pair.privkey, unsignedTargets.asJson).sig

    // signTargets() below expects a signed root
    repo.addRoleKeys(RoleType.ROOT, List(targetsKey1Name)).get
    repo.signRoot(Seq(KeyName("root")), defaultExpiration).get

    val signatures = Map(targetsKey1Name -> wrongSignature, targetsKey2Name -> signature2)
    val path = repo.signTargets(Seq.empty, defaultExpiration, signatures = Some(signatures))
    path.failure.exception.getMessage shouldBe s"Wrong signature: keyId: ${targetsKey1Pair.pubkey.id} signature: ${wrongSignature.value}"
  }

  test("get canonical unsigned root") {
    val repo = initRepo[RepoServerRepo]()
    val unsignedJson = repo.canonicalRoot

    import com.advancedtelematic.libtuf.crypt.CanonicalJson._

    unsignedJson.get shouldBe repo.readUnsignedRole[RootRole].get.asJson.canonical
  }

  test("canonical unsigned root doesn't end with new line") {
    val repo = initRepo[RepoServerRepo]()
    val unsignedJson = repo.canonicalRoot

    unsignedJson.get shouldNot endWith("\n")
  }

  test("get canonical unsigned targets") {
    val repo = initRepo[RepoServerRepo]()
    val unsignedJson = repo.canonicalTargets

    import com.advancedtelematic.libtuf.crypt.CanonicalJson._
    unsignedJson.success.value shouldBe repo
      .readUnsignedRole[TargetsRole]
      .success
      .value
      .asJson
      .canonical
  }

  test("add external RSA signature to root") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val rootKeyName = KeyName("root")
    val privateKeyPath = repo.repoPath.resolve("keys").resolve(rootKeyName.privateKeyName)
    val privateRootKey = CliKeyStorage.readPrivateKey(privateKeyPath).success.value

    repo.signRoot(Seq(rootKeyName), defaultExpiration).success.value
    val unsignedRoot = repo.readUnsignedRole[RootRole].success.value
    val signature = TufCrypto.signPayload(privateRootKey, unsignedRoot.asJson).sig

    repo
      .signRoot(
        Seq.empty,
        defaultExpiration,
        keyName = Some(rootKeyName),
        sigs = Some(Map(rootKeyName -> signature))
      )
      .success
      .value
  }

  test("compare old and new 'root sign'") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val rootKeyName = KeyName("root")

    val path = repo.signRoot(Seq(rootKeyName), defaultExpiration).success.value
    val rootJson = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).value

    rootJson.signatures.length shouldBe 1

    val signature = rootJson.signatures.head.sig

    // signing has bumped the version, so update the unsigned root.json
    repo.writeUnsignedRole[RootRole](rootJson.signed)

    val newPath = repo
      .signRoot(Seq.empty, defaultExpiration, sigs = Some(Map(rootKeyName -> signature)))
      .success
      .value

    newPath shouldBe path

    val newRootJson = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).value

    newRootJson shouldBe rootJson
  }

  test("sign and add same signature to root") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val rootKeyName = KeyName("root")

    val path = repo.signRoot(Seq(rootKeyName), defaultExpiration).success.value
    val rootJson = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).value

    rootJson.signatures.length shouldBe 1

    // signing has bumped the version, so update the unsigned root.json
    repo.writeUnsignedRole[RootRole](rootJson.signed)

    val signature = rootJson.signatures.head.sig
    val newPath = repo
      .signRoot(
        Seq.empty,
        identity,
        keyName = Some(rootKeyName),
        sigs = Some(Map(rootKeyName -> signature))
      )
      .success
      .value

    newPath shouldBe path

    val newRootJson = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).value
    newRootJson.signatures.length shouldBe 2

    val publicKeyPath = repo.repoPath.resolve("keys").resolve(rootKeyName.publicKeyName)
    val publicRootKey = CliKeyStorage.readPublicKey(publicKeyPath).success.value

    // the 2 signatures are different, but they are both valid
    newRootJson.signatures.foreach { sig =>
      TufCrypto.isValid(sig, publicRootKey, newRootJson.signed.asJson)
    }
  }

  test("cannot add invalid signature to root") {
    val repo = initRepo[RepoServerRepo](RsaKeyType)
    val rootKeyName = KeyName("root")
    val wrongSignature = Refined.unsafeApply[String, ValidSignature](
      Base64.getEncoder.encodeToString("wrong signature".getBytes)
    )

    repo
      .signRoot(
        Seq.empty,
        defaultExpiration,
        keyName = Some(rootKeyName),
        sigs = Some(Map(rootKeyName -> wrongSignature))
      )
      .failure
      .exception
      .getMessage
      .startsWith("wrong signature")
  }

  reposerverTest("saves targets.json and checksum to file when pulling") { (repo, client) =>
    val rootRole = client.root().futureValue

    repo.pullVerifyTargets(client, rootRole.signed).futureValue

    repo.readUnsignedRole[TargetsRole].get shouldBe a[TargetsRole]

    Files.readAllLines(repo.repoPath.resolve("roles/targets.json.checksum")).get(0) shouldNot be(
      empty
    )
  }

  reposerverTest("can pull a root.json when no local root is available, when forcing") {
    (repo, client) =>
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

    repo.addRoleKeys(RoleType.ROOT, List(keyname)).get

    val rootRole = repo.readUnsignedRole[RootRole].get

    rootRole.keys(keyPair.pubkey.id) shouldBe keyPair.pubkey
    rootRole.roles(RoleType.ROOT).keyids should contain(keyPair.pubkey.id)
  }

  private def removeRoleKeyTest(repo: RepoServerRepo, roleType: RoleType) = {
    val keyname = KeyName("somekey")
    val keyPair = repo.genKeys(keyname, KeyType.default).get

    repo.addRoleKeys(roleType, List(keyname)).get
    repo.readUnsignedRole[RootRole].get.roles(roleType).keyids.length shouldBe 2

    // role type name is "targets", key name is "target"
    val keyIds = repo.keyIdsByName(List(KeyName(roleType.show.stripSuffix("s")))).get
    repo.removeRoleKeys(roleType, keyIds).get

    val rootRole = repo.readUnsignedRole[RootRole].get

    rootRole.roles(roleType).keyids shouldBe Seq(keyPair.pubkey.id)
  }

  reposerverTest("removes root key from unsigned root") { (repo, _) =>
    removeRoleKeyTest(repo, RoleType.ROOT)
  }

  reposerverTest("removes root key from unsigned targets") { (repo, _) =>
    removeRoleKeyTest(repo, RoleType.TARGETS)
  }

  def removeKeyIdTest(repo: RepoServerRepo, roleType: RoleType): Unit = {
    val keyname = KeyName("somekey")
    val keyPair = repo.genKeys(keyname, KeyType.default).get
    val othername = KeyName("otherkey")
    val otherKeyPair = repo.genKeys(othername, KeyType.default).get

    repo.addRoleKeys(roleType, List(keyname, othername)).get
    repo.removeRoleKeys(roleType, List(keyPair.pubkey.id)).get

    val rootRole = repo.readUnsignedRole[RootRole].get

    rootRole.roles(roleType).keyids should contain(otherKeyPair.pubkey.id)
    rootRole.roles(roleType).keyids shouldNot contain(keyPair.pubkey.id)
  }

  reposerverTest("can remove root keys using key ids") { (repo, _) =>
    removeKeyIdTest(repo, RoleType.ROOT)
  }

  reposerverTest("can remove target keys using key ids") { (repo, _) =>
    removeKeyIdTest(repo, RoleType.TARGETS)
  }

  reposerverTest("pull succeeds when new root.json is valid against local root.json") {
    (repo, server) =>
      val oldRoot = repo.pullRoot(server, userSkipsLocalValidation = true).futureValue

      val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + 1)
      val newRoot = server.sign(newUnsignedRoot)

      server.pushSignedRoot(newRoot).futureValue

      repo.pullRoot(server, userSkipsLocalValidation = false).futureValue
  }

  reposerverTest(
    "pull fails when new root.json is not the same as old root but has same version numbers"
  ) { (repo, client) =>
    val oldSignedRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    val newRoot = oldSignedRoot.signed.copy(expires = Instant.now().plus(100, ChronoUnit.DAYS))
    client.setRoot(client.sign(newRoot))

    val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

    error shouldBe a[RootPullError]
    error
      .asInstanceOf[RootPullError]
      .errors
      .head shouldBe "New root has same version as old root but is not the same root.json"
  }

  reposerverTest("pull succeeds when new root.json is the same as old json") { (repo, client) =>
    repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    repo.pullRoot(client, userSkipsLocalValidation = false).futureValue shouldBe a[SignedPayload[_]]
  }

  reposerverTest("pull fails when new root.json is not valid against local root.json") {
    (repo, client) =>
      val oldRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

      val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + 1)
      client.setRoot(SignedPayload(Seq.empty, newUnsignedRoot, newUnsignedRoot.asJson))

      val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

      val oldKeyId = oldRoot.signed.roles(RoleType.ROOT).keyids.head

      error shouldBe a[RootPullError]
      error.getMessage should include(s"No signature found for key $oldKeyId")
      error.getMessage should include(
        s"root.json version 1 requires 1 valid signatures for root.json version 2, 0 supplied"
      )
  }

  reposerverTest("fails with proper error when cannot find root at specified version") {
    (repo, client) =>
      val oldRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

      val newUnsignedRoot = oldRoot.signed.copy(version = oldRoot.signed.version + 10)
      client.setRoot(SignedPayload(Seq.empty, newUnsignedRoot, newUnsignedRoot.asJson))

      val error = repo.pullRoot(client, userSkipsLocalValidation = false).failed.futureValue

      error shouldBe a[RootPullError]
      error.getMessage should include(s"role with version 2 not found")
  }

  reposerverTest("validates a root chain") { (repo, client) =>
    val oldRoot = repo.pullRoot(client, userSkipsLocalValidation = true).futureValue

    for (i <- 1 until 10) {
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
    val payload = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).value

    payload.isValidFor(pub) shouldBe true
    payload.isValidFor(pub02) shouldBe true
  }

  reposerverTest("signing root increases version") { (repo, _) =>
    val keyname = KeyName("somekey")
    val _ = repo.genKeys(keyname, KeyType.default).get.pubkey

    val path = repo.signRoot(Seq(keyname), defaultExpiration).get
    val payload = parseFile(path.toFile).flatMap(_.as[SignedPayload[RootRole]]).value

    payload.signed.version shouldBe 2
  }

  reposerverTest("inc-version for unsigned root") { (repo, _) =>
    val currRootRole = repo.readUnsignedRole[RootRole].success.value

    currRootRole.version shouldBe 1

    repo.incrementRootVersion.success.value

    val resultRootRole = repo.readUnsignedRole[RootRole].success.value

    resultRootRole.version shouldBe 2
  }

  reposerverTest("inc-version for unsigned target") { (repo, _) =>
    val currTargetRole = repo.readUnsignedRole[TargetsRole].success.value

    currTargetRole.version shouldBe 11

    repo.incrementTargetVersion.success.value

    val resultTargetRole = repo.readUnsignedRole[TargetsRole].success.value

    resultTargetRole.version shouldBe 12
  }

}
