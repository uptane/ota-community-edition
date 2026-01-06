package com.advancedtelematic.tuf.keyserver.client

import java.security.interfaces.RSAPublicKey
import com.advancedtelematic.libats.http.Errors.RemoteServiceError
import com.advancedtelematic.libats.http.tracing.NullServerRequestTracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.{
  EcPrime256TufKey,
  Ed25519KeyType,
  KeyId,
  KeyType,
  RepoId,
  RoleType,
  RsaKeyType,
  SignedPayload,
  ValidKeyId
}
import com.advancedtelematic.libtuf_server.keyserver.{KeyserverClient, KeyserverHttpClient}
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.{
  Key,
  KeyGenId,
  KeyGenRequest,
  KeyGenRequestStatus
}
import com.advancedtelematic.tuf.keyserver.db.KeyGenRequestSupport
import com.advancedtelematic.tuf.util._
import eu.timepit.refined.refineV
import io.circe.Json
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Seconds, Span}

import java.time.Instant
import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class KeyserverHttpClientSpec
    extends TufKeyserverSpec
    with ResourceSpec
    with KeyGenRequestSupport
    with RootGenerationSpecSupport
    with PatienceConfiguration
    with KeyTypeSpecSupport
    with HttpClientSpecSupport {

  override val ec: scala.concurrent.ExecutionContextExecutor = this.executor

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(20, Seconds), interval = Span(500, Millis))

  implicit lazy val requestTracing: ServerRequestTracing = new NullServerRequestTracing

  val client = new KeyserverHttpClient("http://test-keyserver", testHttpClient)

  def createAndProcessRoot(repoId: RepoId,
                           keyType: KeyType): Future[(Seq[Key], SignedPayload[RootRole])] =
    for {
      _ <- client.createRoot(repoId, keyType, forceSync = false)
      keys <- processKeyGenerationRequest(repoId)
      rootRole <- client.fetchRootRole(repoId)
    } yield (keys, rootRole)

  // only makes sense for RSA
  test("minimum RSA key size when creating a repo") {
    val repoId = RepoId.generate()

    val f = for {
      _ <- client.createRoot(repoId, RsaKeyType, forceSync = false)
      _ <- processKeyGenerationRequest(repoId)
      rootRole <- client.fetchRootRole(repoId)
    } yield rootRole.signed.keys.values

    val keys = f.futureValue

    keys.foreach { key =>
      key.keyval match {
        case pubKey: RSAPublicKey =>
          pubKey.getModulus.bitLength() should be >= 2048
      }
    }
  }

  def manipulateSignedRsaKey(payload: SignedPayload[RootRole]): SignedPayload[RootRole] = {
    val kid: KeyId = refineV[ValidKeyId]("0" * 64).toOption.get
    // change type of one of the RSA keys to Ed25519:
    val key = EcPrime256TufKey(payload.signed.keys.values.head.keyval)
    val signedCopy = payload.signed.copy(keys = payload.signed.keys.updated(kid, key))
    payload.updated(signed = signedCopy)
  }

  keyTypeTest("creates a root") { keyType =>
    val repoId = RepoId.generate()
    client.createRoot(repoId, keyType, forceSync = false).futureValue shouldBe a[Json]
  }

  keyTypeTest("fetches unsigned root ") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      unsigned <- client.fetchUnsignedRoot(repoId)
    } yield unsigned

    f.futureValue shouldBe a[RootRole]
  }

  keyTypeTest("updates a root ") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      _ <- client.fetchRootRole(repoId)
      unsigned <- client.fetchUnsignedRoot(repoId)
      signedJsonPayload <- client.sign(repoId, unsigned)
      updated <- client.updateRoot(repoId, signedJsonPayload)
    } yield updated

    f.futureValue shouldBe (())
  }

  keyTypeTest("root update with invalid key returns expected error ") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      signed <- client.fetchRootRole(repoId)
      updated <- client.updateRoot(repoId, manipulateSignedRsaKey(signed))
    } yield updated

    val failure = f.failed.futureValue
    failure shouldBe a[RemoteServiceError]
  }

  keyTypeTest("fetches a root key pair ") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      root <- client.fetchRootRole(repoId)
      keyId = root.signed.roles(RoleType.TARGETS).keyids.head
      keyPair <- client.fetchKeyPair(repoId, keyId)
    } yield (keyId, keyPair)

    whenReady(f) { case (keyId, keyPair) =>
      keyPair.pubkey.keytype shouldBe keyType
      keyPair.privkey.keytype shouldBe keyType
      keyPair.pubkey.id shouldBe keyId
    }
  }

  keyTypeTest("deletes a key ") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      signed <- client.fetchRootRole(repoId)
      deleted <- client.deletePrivateKey(repoId, signed.signed.keys.keys.head)
    } yield deleted

    f.futureValue shouldBe (())
  }

  keyTypeTest("signing with removed key produces RoleKeyNotFound error ") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      signed <- client.fetchRootRole(repoId)
      _ <- client.deletePrivateKey(repoId, signed.signed.roles(RoleType.TARGETS).keyids.head)
      sig <- client.sign(repoId, TargetsRole(Instant.now, Map.empty, 0))
    } yield sig

    f.failed.futureValue shouldBe KeyserverClient.RoleKeyNotFound
  }

  keyTypeTest("fetches root role by version") { keyType =>
    val repoId = RepoId.generate()

    val f = for {
      _ <- createAndProcessRoot(repoId, keyType)
      _ <- client.fetchRootRole(repoId)
      signed <- client.fetchRootRole(repoId, 1)
    } yield signed

    f.futureValue shouldBe a[SignedPayload[_]]
    f.futureValue.signed shouldBe a[RootRole]
  }

  keyTypeTest("returns KeysNotReady when keys are not yet ready") { keyType =>
    val repoId = RepoId.generate()
    val keyGenRequest = KeyGenRequest(
      KeyGenId.generate(),
      repoId,
      KeyGenRequestStatus.REQUESTED,
      RoleType.TARGETS,
      keyType.crypto.defaultKeySize,
      keyType
    )
    val f = for {
      _ <- keyGenRepo.persist(keyGenRequest)
      root <- client.fetchRootRole(repoId)
    } yield root

    f.failed.futureValue shouldBe KeyserverClient.KeysNotReady
  }

  keyTypeTest("fetching target key pairs ") { keyType =>
    val repoId = RepoId.generate()
    val (keys, _) = createAndProcessRoot(repoId, keyType).futureValue

    async {
      val pairs = await(client.fetchTargetKeyPairs(repoId))
      pairs.map(_.pubkey) shouldBe keys.filter(_.roleType == RoleType.TARGETS).map(_.publicKey)
    }.futureValue
  }

  test("keyserver generates key synchronously when using `forceSync` is set") {
    val repoId = RepoId.generate()

    val rootF = for {
      _ <- client.createRoot(repoId, Ed25519KeyType)
      r <- client.fetchRootRole(repoId)
    } yield r.signed

    rootF.futureValue shouldBe a[RootRole]
  }

  test("adds offline targets role keys") {
    val repoId = RepoId.generate()

    val rootF = for {
      _ <- client.createRoot(repoId, Ed25519KeyType)
      _ <- client.fetchRootRole(repoId)
      _ <- client.addOfflineUpdatesRole(repoId)
      r <- client.fetchRootRole(repoId)
    } yield r.signed

    rootF.futureValue.roles(RoleType.OFFLINE_UPDATES).keyids shouldNot be(empty)
  }

}
