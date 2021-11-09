package com.advancedtelematic.tuf.reposerver.http

import java.io.RandomAccessFile
import java.net.URI
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

import cats.syntax.option._
import com.advancedtelematic.libats.data.DataType.{Namespace, ValidChecksum}
import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedRoleName, Delegation, RootRole, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, RepoId, RoleType, SignedPayload, TufKey, TufPrivateKey, ValidTargetFilename}
import com.advancedtelematic.libtuf.data.ValidatedString._
import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpClientError
import com.advancedtelematic.libtuf.http.ReposerverHttpClient
import com.advancedtelematic.libtuf.http.TufServerHttpClient.{RoleChecksumNotValid, UploadTargetTooBig}
import com.advancedtelematic.tuf.reposerver.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.tuf.reposerver.util._
import io.circe.syntax._
import org.scalatest.BeforeAndAfter
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class UserReposerverClientSpec extends TufReposerverSpec
  with ResourceSpec
  with FakeCliHttpClient
  with RepoNamespaceRepositorySupport
  with BeforeAndAfter {

  override def executor: ExecutionContextExecutor = super.executor

  implicit val ec: ExecutionContext = system.dispatcher

  override implicit def patienceConfig: PatienceConfig = PatienceConfig().copy(timeout = Span(60, Seconds))

  val repoId = RepoId.generate()

  val client = new ReposerverHttpClient(URI.create("http://test-reposerver"), testBackend)

  override def beforeAll(): Unit = {
    super.beforeAll()
    repoNamespaceRepo.persist(repoId, Namespace("default")).futureValue
  }

  before {
    fakeKeyserverClient.createRoot(repoId, KeyType.default).futureValue
  }

  after {
    fakeKeyserverClient.resetKeyServer()
  }

  test("fetches a root") {
    val signedRoot = client.root().futureValue
    signedRoot shouldBe a[SignedPayload[_]]
    signedRoot.signed shouldBe a[RootRole]
  }

  test("fetches a root by version") {
    val oldRoot = client.root().futureValue
    val newRoot = client.root(Some(oldRoot.signed.version)).futureValue
    newRoot.signed.version shouldBe oldRoot.signed.version
  }

  test("accepts old root") {
    val signedRoot = client.root().futureValue
    client.pushSignedRoot(signedRoot).futureValue
  }

  test("accepts a new targets role") {
    val targets = TargetsRole(Instant.now, Map.empty, 20)
    val signedPayload = fakeKeyserverClient.sign(repoId, targets).futureValue
    client.pushTargets(SignedPayload(signedPayload.signatures, targets, targets.asJson), None).futureValue
  }

  test("moves key offline") {
    val signedRoot = client.root().futureValue
    val keyPair = client.fetchKeyPair(signedRoot.signed.roles(RoleType.ROOT).keyids.head).futureValue
    client.deleteKey(signedRoot.signed.roles(RoleType.ROOT).keyids.head).futureValue

    keyPair.privkey shouldBe a[TufPrivateKey]
    keyPair.pubkey shouldBe a[TufKey]

    client.fetchKeyPair(keyPair.pubkey.id).failed.futureValue shouldBe a[CliHttpClientError]
  }

  test("returns specific exception when previous checksum is not valid") {
    val targets = TargetsRole(Instant.now, Map.empty, 20)
    val signedTargets = fakeKeyserverClient.sign(repoId, targets).futureValue
    val invalidChecksum = "11c3599621d7edc417c795363767754b431404e8f9fd6fb85f78b2b45423b00b".refineTry[ValidChecksum].get
    client.pushTargets(SignedPayload(signedTargets.signatures, targets, targets.asJson), Option(invalidChecksum)).failed.futureValue shouldBe RoleChecksumNotValid
  }

  test("returns specific exception when no previous checksum is present at all") {
    val targets = TargetsRole(Instant.now, Map.empty, 20)
    val signedTargets = fakeKeyserverClient.sign(repoId, targets).futureValue
    client.pushTargets(SignedPayload(signedTargets.signatures, targets, targets.asJson), None).failed.futureValue shouldBe RoleChecksumNotValid
  }

  test("can update with proper checksum header") {
    val targetsResponse = client.targets().futureValue

    val targets = TargetsRole(Instant.now, Map.empty, targetsResponse.targets.signed.version + 1)
    val signedTargets = fakeKeyserverClient.sign(repoId, targets).futureValue

    client.pushTargets(SignedPayload(signedTargets.signatures, targets, targets.asJson), targetsResponse.checksum).futureValue shouldBe (())
  }

  test("can pull targets") {
    val targetsResponse = client.targets().futureValue
    targetsResponse.targets.signatures shouldNot be(empty)
    targetsResponse.checksum shouldNot be(empty)
  }

  test("can push and pull a delegation to/from server") {
    val name = "delegation01".unsafeApply[DelegatedRoleName]
    val delegationKey = KeyType.default.crypto.generateKeyPair()

    val existingTargets = client.targets().futureValue
    val delegation = Delegation(name, List(delegationKey.pubkey.id), List.empty)
    val targets = TargetsRole(Instant.now, Map.empty, existingTargets.targets.signed.version + 1,
      delegations = ClientDataType.Delegations(Map(delegationKey.pubkey.id -> delegationKey.pubkey), List(delegation)).some)
    val signedTargets = fakeKeyserverClient.sign(repoId, targets).futureValue

    client.pushTargets(SignedPayload(signedTargets.signatures, targets, targets.asJson), existingTargets.checksum).futureValue

    val delegationTargets = TargetsRole(Instant.now.plus(1, ChronoUnit.HOURS), Map.empty, version = 1)
    val delegationSig = TufCrypto.signPayload(delegationKey.privkey, delegationTargets.asJson)
    val signedDelegation = SignedPayload(Seq(delegationSig.toClient(delegationKey.pubkey.id)), delegationTargets, delegationTargets.asJson)

    client.pushDelegation(name, signedDelegation).futureValue shouldBe (())

    client.pullDelegation(name).futureValue.asJsonSignedPayload shouldBe signedDelegation.asJsonSignedPayload
  }

  test("returns specific exception when uploaded file is too large") {
    val uploadFilePath = Files.createTempFile("s3upload", "txt")
    val f = new RandomAccessFile(uploadFilePath.toFile, "rw")
    f.setLength(3 * Math.pow(10, 9).toLong + 1)

    try {
      val targetFilename = eu.timepit.refined.refineV[ValidTargetFilename]("filesizetest-0.0.1").right.get

      val err = client.uploadTarget(targetFilename, uploadFilePath, 10.seconds).failed.futureValue

      err shouldBe a[CliHttpClientError]
      err.getMessage should endWith("File being uploaded is too large (3000000001), maximum size is 3000000000")

    } finally {
      Files.delete(uploadFilePath)
    }
  }
}
