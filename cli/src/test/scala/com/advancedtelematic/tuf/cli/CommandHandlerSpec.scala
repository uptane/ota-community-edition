package com.advancedtelematic.tuf.cli

import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.data.Validated.Valid
import cats.syntax.either._
import cats.syntax.option._
import com.advancedtelematic.libats.data.DataType.{Checksum, HashMethod, ValidChecksum}
import com.advancedtelematic.libtuf.crypt.{Sha256FileDigest, TufCrypto}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedPathPattern._
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedRoleName._
import com.advancedtelematic.libtuf.data.ClientDataType.{
  DelegatedPathPattern,
  DelegatedRoleName,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  KeyType,
  SignedPayload,
  TargetName,
  TargetVersion
}
import com.advancedtelematic.libtuf.data.ValidatedString._
import com.advancedtelematic.tuf.cli.Commands._
import com.advancedtelematic.tuf.cli.DataType.{KeyName, MutualTlsConfig, RepoConfig, TreehubConfig}
import com.advancedtelematic.tuf.cli.repo.{CliKeyStorage, RepoServerRepo, TufRepo}
import com.advancedtelematic.tuf.cli.util.TufRepoInitializerUtil._
import com.advancedtelematic.tuf.cli.util.{
  CliSpec,
  FakeReposerverTufServerClient,
  KeyTypeSpecSupport
}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import io.circe.syntax._
import io.circe.{jawn, Json}
import org.scalatest.Inspectors
import org.scalatest.OptionValues._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommandHandlerSpec extends CliSpec with KeyTypeSpecSupport with Inspectors {

  val defaultConfig = Config(Help)

  lazy val tufRepo = initRepo[RepoServerRepo]()

  lazy val userKeyDir = Files.createTempDirectory("user-keys")

  lazy val userKeyStorage = CliKeyStorage.forUser(userKeyDir)

  lazy val reposerverClient = FakeReposerverTufServerClient(KeyType.default)

  lazy val handler: Config => Future[Unit] = config =>
    CommandHandler.handle(
      tufRepo,
      Future.successful(reposerverClient),
      Future.successful(reposerverClient),
      userKeyStorage,
      config
    )

  keyTypeTest("generates a key using the provided user key storage") { keyType =>
    val keyName01 = KeyName("mykey01")
    val keyName02 = KeyName("mykey02")
    val config = Config(GenUserKey, keyNames = List(keyName01, keyName02), keyType = keyType)

    handler(config).futureValue

    forAll(List(keyName01, keyName02)) { keyName =>
      val (pub, priv) = userKeyStorage.readKeyPair(keyName).get
      pub.keytype shouldBe keyType
      priv.keytype shouldBe keyType
    }
  }

  test("signs a delegation with the provided keys") {
    val keyName01 = KeyName("mykey01")
    val keyName02 = KeyName("mykey02")

    val in = Files.createTempFile("in", ".json")

    Delegations.writeNew(new FileOutputStream(in.toFile)).get

    val config = Config(
      SignDelegation,
      keyNames = List(keyName01, keyName02),
      inputPath = in.some,
      inplace = true
    )

    handler(config).futureValue

    val signedPayload =
      jawn.parseFile(in.toFile).flatMap(_.as[SignedPayload[TargetsRole]]).valueOr(throw _)

    forAll(List(keyName01, keyName02)) { keyName =>
      val (pub, _) = userKeyStorage.readKeyPair(keyName).get
      val keyMap = Map(pub.id -> pub)

      TufCrypto.payloadSignatureIsValid(
        keyMap,
        threshold = 1,
        signedPayload = signedPayload
      ) shouldBe a[Valid[_]]
    }
  }

  test("pushes a delegation to the server") {
    val in = Files.createTempFile("in", ".json")

    val empty = Delegations.empty

    val delegation = SignedPayload(Seq.empty, empty, empty.asJson)

    Files.write(in, delegation.asJson.spaces2.getBytes)

    val name = "delegation01".unsafeApply[DelegatedRoleName]

    val config = Config(PushDelegation, delegationName = name, inputPath = in.some)

    handler(config).futureValue

    reposerverClient
      .delegations()
      .toMap
      .apply(name)
      .asJsonSignedPayload shouldBe delegation.asJsonSignedPayload
  }

  test("adds a delegation to an existing targets role") {
    val pubkey = KeyType.default.crypto.generateKeyPair().pubkey
    val keyFile = Files.createTempFile("key01.pub", ".json")
    Files.write(keyFile, pubkey.asJson.spaces2.getBytes)

    val name = "delegation02".unsafeApply[DelegatedRoleName]
    val delegatedPath = "path01/*".unsafeApply[DelegatedPathPattern]
    val config = Config(
      AddDelegationToTarget,
      delegationName = name,
      delegatedPaths = List(delegatedPath),
      keyPaths = List(keyFile)
    )

    handler(config).futureValue

    val role = tufRepo.readUnsignedRole[TargetsRole].get

    role.delegations shouldNot be(empty)
    role.delegations.get.keys shouldBe Map(pubkey.id -> pubkey)

    role.delegations.get.roles shouldNot be(empty)
    val newDelegation = role.delegations.get.roles.head

    newDelegation shouldBe ClientDataType.Delegation(name, List(pubkey.id), List(delegatedPath))
  }

  test("pulls a delegation by name") {
    val out = Files.createTempFile("out", ".json")

    val name = "delegation03".unsafeApply[DelegatedRoleName]
    val delegation = TargetsRole(Instant.now, Map.empty, version = 1)
    val signedDelegation = SignedPayload(Seq.empty, delegation, delegation.asJson)

    reposerverClient.pushDelegation(name, signedDelegation).futureValue

    val config = Config(PullDelegation, delegationName = name, outputPath = out.some)

    handler(config).futureValue

    jawn
      .parseFile(out.toFile)
      .flatMap(_.as[SignedPayload[TargetsRole]])
      .valueOr(throw _)
      .asJsonSignedPayload shouldBe signedDelegation.asJsonSignedPayload
  }

  test("IdUserKey outputs a key id") {
    val out = Files.createTempFile("out", ".json")
    val keyFile = Files.createTempFile("key", ".pub")

    val keypair = Ed25519KeyType.crypto.generateKeyPair()
    Files.write(keyFile, keypair.pubkey.asJson.spaces2.getBytes)

    val config = Config(IdUserKey, inputPath = keyFile.some, outputPath = out.some)

    handler(config).futureValue

    val bytes = Files.readAllBytes(out)
    new String(bytes) should be(keypair.pubkey.id.value)
  }

  test("adds a target to a delegation") {
    val in = Files.createTempFile("in", ".json")

    Delegations.writeNew(new FileOutputStream(in.toFile)).get

    val config = Config(
      AddTargetToDelegation,
      inputPath = in.some,
      targetName = TargetName("mytarget").some,
      targetVersion = TargetVersion("0.1.1").some,
      checksum = Checksum(
        HashMethod.SHA256,
        refineV[ValidChecksum](
          "66bad8889a5193362cbe4c89d21688cf79310bfeb7eff67fe0f79c6c11c86d67"
        ).toOption.get
      ).some,
      inplace = true
    )

    handler(config).futureValue

    val output = io.circe.jawn
      .parse(new String(Files.readAllBytes(in)))
      .flatMap(_.as[TargetsRole])
      .valueOr(throw _)

    output.targets.keys.map(_.value) should contain("mytarget-0.1.1")
  }

  test("ImportClientTls adds certificates to a repository") {
    val client = Files.createTempFile("client", ".p12")
    Files.write(client, "myclientcert".getBytes())
    val server = Files.createTempFile("server", ".p12")
    Files.write(server, "myservercert".getBytes())

    val repo = initRepo[RepoServerRepo]()

    TufRepo.writeConfig(
      repo.repoPath,
      RepoConfig(
        new URI("http://test"),
        auth = None,
        TreehubConfig(None, no_auth = false, Json.Null)
      )
    )

    val config = Config(ImportClientTls, inputPath = client.some, serverCertPath = server.some)

    CommandHandler
      .handle(
        repo,
        Future.successful(reposerverClient),
        Future.successful(reposerverClient),
        userKeyStorage,
        config
      )
      .futureValue

    val newConfig = TufRepo.readConfig(repo.repoPath)

    val clientCertPath =
      repo.repoPath.resolve(newConfig.get.auth.get.asInstanceOf[MutualTlsConfig].certPath)
    val serverCertPath =
      repo.repoPath.resolve(newConfig.get.auth.get.asInstanceOf[MutualTlsConfig].serverCertPath.get)

    new String(Files.readAllBytes(clientCertPath)) shouldBe "myclientcert"
    new String(Files.readAllBytes(serverCertPath)) shouldBe "myservercert"
  }

  test("signs targets with a given expiration date") {
    val pubkey = KeyType.default.crypto.generateKeyPair().pubkey
    val keyFile = Files.createTempFile("key01.pub", ".json")
    Files.write(keyFile, pubkey.asJson.spaces2.getBytes)
    val expiration = Instant.now().plusSeconds(10000).truncatedTo(ChronoUnit.SECONDS)

    val config = Config(SignTargets, keyPaths = List(keyFile), expireOn = Some(expiration))

    handler(config).futureValue

    val role = tufRepo.readSignedRole[TargetsRole].get
    role.signed.expires shouldBe expiration
  }

  test("uploads a target to server") {
    val p = Files.createTempFile("s3upload-", ".txt")
    Files.write(
      p,
      "“You who read me, are You sure of understanding my language“".getBytes(
        StandardCharsets.UTF_8
      )
    )

    val config = Config(
      UploadTarget,
      targetName = TargetName("uploaded-target").some,
      targetVersion = TargetVersion("0.0.1").some,
      inputPath = p.some
    )

    handler(config).futureValue

    reposerverClient.uploaded.get(Refined.unsafeApply("uploaded-target-0.0.1")) shouldBe p
  }

  test("adds an uploaded target to targets.json") {
    val uploadFilePath = Files.createTempFile("s3upload-", ".txt")
    Files.write(
      uploadFilePath,
      "“You who read me, are You sure of understanding my language“".getBytes(
        StandardCharsets.UTF_8
      )
    )

    val config = Config(
      AddUploadedTarget,
      targetName = TargetName("uploaded-target-before").some,
      targetVersion = TargetVersion("0.0.1").some,
      inputPath = uploadFilePath.some
    )

    handler(config).futureValue

    val role = tufRepo.readUnsignedRole[TargetsRole].get
    val addedTarget = role.targets.get(Refined.unsafeApply("uploaded-target-before-0.0.1")).value

    addedTarget.length shouldBe uploadFilePath.toFile.length()
    val (method, checksum) = addedTarget.hashes.head
    method shouldBe HashMethod.SHA256
    checksum shouldBe Sha256FileDigest.from(uploadFilePath).hash
  }

  test("adds an uploaded target to targets.json with custom metadata") {
    val uploadFilePath = Files.createTempFile("s3upload-", ".txt")
    Files.write(
      uploadFilePath,
      "“You who read me, are You sure of understanding my language“".getBytes(
        StandardCharsets.UTF_8
      )
    )

    val clientMeta = Json.obj("myjsonkey" -> "somevalue".asJson)

    val config = Config(
      AddUploadedTarget,
      targetName = TargetName("uploaded-target-before").some,
      targetVersion = TargetVersion("0.0.2").some,
      inputPath = uploadFilePath.some,
      customMeta = clientMeta
    )

    handler(config).futureValue

    val role = tufRepo.readUnsignedRole[TargetsRole].get
    val addedTarget = role.targets.get(Refined.unsafeApply("uploaded-target-before-0.0.2")).value

    addedTarget.length shouldBe uploadFilePath.toFile.length()
    val (method, checksum) = addedTarget.hashes.head
    method shouldBe HashMethod.SHA256
    checksum shouldBe Sha256FileDigest.from(uploadFilePath).hash

    addedTarget.custom.value.hcursor.get[String]("myjsonkey") shouldBe Right("somevalue")
  }

}
