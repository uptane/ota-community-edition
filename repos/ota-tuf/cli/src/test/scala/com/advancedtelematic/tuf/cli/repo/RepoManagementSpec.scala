package com.advancedtelematic.tuf.cli.repo

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.time.Instant

import cats.syntax.either._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  KeyType,
  RsaKeyType,
  SignedPayload,
  TufKey,
  TufPrivateKey
}
import com.advancedtelematic.tuf.cli.DataType._
import com.advancedtelematic.tuf.cli.repo.TufRepo.{
  MissingCredentialsZipFile,
  RepoAlreadyInitialized
}
import com.advancedtelematic.tuf.cli.util.{CliSpec, KeyTypeSpecSupport}
import io.circe.jawn._
import io.circe.syntax._
import org.scalatest.OptionValues._
import org.scalatest.TryValues._

import scala.util.{Success, Try}

class RepoManagementSpec extends CliSpec with KeyTypeSpecSupport {

  lazy val credentialsZipNoTargets =
    Paths.get(this.getClass.getResource("/credentials_no_targets.zip").toURI)

  lazy val credentialsZipEd25519 =
    Paths.get(this.getClass.getResource("/credentials_ed25519.zip").toURI)

  lazy val credentialsZipNoTufRepoEd25519 =
    Paths.get(this.getClass.getResource("/credentials_no_tufrepo_ed25519.zip").toURI)

  lazy val credentialsZipNoAuthEd25519 =
    Paths.get(this.getClass.getResource("/credentials_no_auth_ed25519.zip").toURI)

  lazy val credentialsZipTlsAuthEd25519 =
    Paths.get(this.getClass.getResource("/credentials_tls-auth_ed25519.zip").toURI)

  lazy val credentialsZipAuthPlusLegacy =
    Paths.get(this.getClass.getResource("/credentials_auth_plus_legacy.zip").toURI)

  import scala.concurrent.ExecutionContext.Implicits.global

  val fakeRepoUri = Some(new URI("https://test-reposerver"))

  def randomRepoPath = Files.createTempDirectory("tuf-repo").resolve("repo")

  test("credentials.zip without tufrepo.url throws proper error") {
    val repoT =
      RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipNoTufRepoEd25519)
    repoT.failure.exception shouldBe MissingCredentialsZipFile("tufrepo.url")
  }

  test("legacy auth+ credentials.zip treehub.json are still parsed successfully") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipAuthPlusLegacy)
    repoT.success.value.treehubConfig.success.value.oauth2.value.scope shouldBe "none"
  }

  test("throws error for already initialized repos") {
    val path = randomRepoPath

    val repoT = RepoManagement.initialize(RepoServer, path, credentialsZipEd25519)
    repoT.success

    val repoF = RepoManagement.initialize(RepoServer, path, credentialsZipEd25519)
    repoF.failure.exception shouldBe RepoAlreadyInitialized(path)
  }

  test("can initialize repo from ZIP file") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipEd25519)
    repoT.success
  }

  test("can initialize repo from ZIP file specifying custom repo") {
    val repoT = RepoManagement.initialize(
      RepoServer,
      randomRepoPath,
      credentialsZipEd25519,
      repoUri = Some(new URI("https://ats.com"))
    )
    repoT.success.value.repoServerUri.get.toString shouldBe "https://ats.com"
  }

  test("can initialize repo from ZIP file without targets keys") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipNoTargets)
    repoT.success.value.repoPath.resolve("keys/targets.pub").toFile.exists() shouldBe false
  }

  test("can read auth config for an initialized repo") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipEd25519)
    repoT.success.value.authConfig.get.get shouldBe a[OAuthConfig]
  }

  test("skips auth when no_auth: true") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipNoAuthEd25519)
    repoT.success.value.authConfig.get shouldBe None
  }

  test("reads targets root.json from credentials.zip if present") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipEd25519)
    repoT.success.value.readSignedRole[RootRole].get.signed shouldBe a[RootRole]
  }

  def keyTypeZipTest(name: String)(fn: (Path, KeyType) => Any): Unit = {
    val setupFor = (keyType: KeyType) => {
      val zipName = keyType match {
        case Ed25519KeyType => "Ed25519"
        case RsaKeyType     => "RSA"
        case t => throw new IllegalArgumentException("[test]: Unknown key type for zip file: " + t)
      }

      Paths.get(this.getClass.getResource(s"/credentials_${zipName.toLowerCase}.zip").toURI)
    }

    keyTypeTest(name)(setupFor.andThen(fn.curried))
  }

  keyTypeZipTest("reads targets keys from credentials.zip if present") { (zip, keyType) =>
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, zip)
    repoT shouldBe a[Success[_]]

    val repo = repoT.get

    repo.authConfig.get.get shouldBe a[OAuthConfig]
    parseFile(repo.repoPath.resolve("keys/targets.pub").toFile)
      .flatMap(_.as[TufKey])
      .valueOr(throw _)
      .keytype shouldBe keyType
    parseFile(repo.repoPath.resolve("keys/targets.sec").toFile)
      .flatMap(_.as[TufPrivateKey])
      .valueOr(throw _)
      .keytype shouldBe keyType
  }

  keyTypeZipTest("export includes root.json") { (zip, keyType) =>
    val repo = RepoManagement.initialize(RepoServer, randomRepoPath, zip).get
    val tempPath = Files.createTempFile("tuf-repo-spec-export", ".zip")

    repo.genKeys(KeyName("targets"), keyType).get

    val rootRole = RootRole(Map.empty, Map.empty, 2, expires = Instant.now())

    val signedPayload = SignedPayload(Seq.empty, rootRole, rootRole.asJson)

    repo.writeSignedRole(signedPayload).get

    RepoManagement.export(repo, KeyName("targets"), tempPath) shouldBe a[Success[_]]
    val repoFromExported = RepoManagement.initialize(RepoServer, randomRepoPath, tempPath).get

    repoFromExported.readSignedRole[RootRole].get.asJson shouldBe signedPayload.asJson
  }

  keyTypeZipTest("can export zip file ") { (zip, keyType) =>
    val repo = RepoManagement.initialize(RepoServer, randomRepoPath, zip).get
    repo.genKeys(KeyName("default-key"), keyType)

    val tempPath = Files.createTempFile("tuf-repo-spec-export", ".zip")
    RepoManagement.export(repo, KeyName("default-key"), tempPath) shouldBe Try(())

    // test the exported zip file by creating another repo from it:
    val repoFromExported = RepoManagement.initialize(RepoServer, randomRepoPath, tempPath).get
    repoFromExported.authConfig.get
      .map(_.asInstanceOf[OAuthConfig].client_id)
      .get shouldBe "8f505046-bf38-4e17-a0bc-8a289bbd1403"
    val server = repoFromExported.treehubConfig.get.ostree.hcursor
      .downField("server")
      .as[String]
      .valueOr(throw _)
    server shouldBe "https://treehub-pub.gw.staging.atsgarage.com/api/v3"
  }

  keyTypeZipTest("export uses configured tuf url, not what came in the original file") {
    (zip, keyType) =>
      val repo = RepoManagement
        .initialize(
          RepoServer,
          randomRepoPath,
          zip,
          repoUri = Some(new URI("https://someotherrepo.com"))
        )
        .get
      repo.genKeys(KeyName("default-key"), keyType)

      val tempPath = Files.createTempFile("tuf-repo-spec-export", ".zip")
      RepoManagement.export(repo, KeyName("default-key"), tempPath) shouldBe Try(())

      // test the exported zip file by creating another repo from it:
      val repoFromExported = RepoManagement.initialize(RepoServer, randomRepoPath, tempPath).get
      repoFromExported.repoServerUri.get.toString shouldBe "https://someotherrepo.com"
  }

  keyTypeZipTest("creates base credentials.zip if one does not exist") { (zip, keyType) =>
    val repo = RepoManagement.initialize(RepoServer, randomRepoPath, zip).get
    val tempPath = Files.createTempFile("cli-export", ".zip")

    Files.delete(repo.repoPath.resolve("credentials.zip"))

    repo.genKeys(KeyName("targets"), keyType).get

    RepoManagement.export(repo, KeyName("targets"), tempPath) shouldBe a[Success[_]]

    val repoFromExported = RepoManagement.initialize(RepoServer, randomRepoPath, tempPath).get

    repoFromExported.repoPath.toFile.exists() shouldBe true
  }

  test("initializes a repository with oauth if p12 file is not present in zip") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipEd25519)

    repoT shouldBe a[Success[_]]
    val repo = repoT.get

    val oauthConfig = repo.authConfig.get.get.asInstanceOf[OAuthConfig]

    oauthConfig.client_id shouldBe "8f505046-bf38-4e17-a0bc-8a289bbd1403"
  }

  test("initializes a repository with mutual tls if p12 file present in zip") {
    val repoT = RepoManagement.initialize(RepoServer, randomRepoPath, credentialsZipTlsAuthEd25519)

    repoT shouldBe a[Success[_]]
    val repo = repoT.get

    val tlsConfig = repo.authConfig.get.get.asInstanceOf[MutualTlsConfig]
    Files.exists(repo.repoPath.resolve(tlsConfig.certPath)) shouldBe true
    Files.exists(repo.repoPath.resolve(tlsConfig.serverCertPath.get)) shouldBe true
  }

}
