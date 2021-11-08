package com.advancedtelematic.tuf.keyserver.http


import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.RootManipulationOps._
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, _}
import com.advancedtelematic.tuf.keyserver.daemon.DefaultKeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.{KeyGenId, KeyGenRequest, KeyGenRequestStatus}
import com.advancedtelematic.tuf.keyserver.db.{KeyGenRequestSupport, KeyRepositorySupport, SignedRootRoleSupport}
import com.advancedtelematic.tuf.keyserver.roles.SignedRootRoles
import com.advancedtelematic.tuf.util.{KeyTypeSpecSupport, TufKeyserverSpec}
import io.circe.syntax._
import org.scalatest.Inspectors
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libats.test.MysqlDatabaseSpec

class SignedRootRolesSpec extends TufKeyserverSpec with MysqlDatabaseSpec
  with Inspectors with PatienceConfiguration
  with KeyGenRequestSupport
  with KeyTypeSpecSupport
  with SignedRootRoleSupport
  with KeyRepositorySupport {

  implicit val ec = ExecutionContext.global

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(10, Seconds))

  val signedRootRoles = new SignedRootRoles()
  val keyGenerationOp = DefaultKeyGenerationOp()

  keyTypeTest("root role payload must be signed with root key") { keyType =>
    val repoId = RepoId.generate()
    val rootKeyGenRequest = KeyGenRequest(KeyGenId.generate(),
      repoId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT, keyType.crypto.defaultKeySize, keyType)

    async {
      await(keyGenRepo.persist(rootKeyGenRequest))
      await(keyGenerationOp(rootKeyGenRequest))
      await(signedRootRoles.findFreshAndPersist(repoId))
      val signed = await(signedRootRoles.findLatest(repoId))

      val rootKeyId = signed.signed.roles(RoleType.ROOT).keyids.head
      val publicKey = signed.signed.keys(rootKeyId)

      val clientSignature = signed.signatures.head

      val sig = Signature(clientSignature.sig, clientSignature.method)

      TufCrypto.isValid(sig, publicKey, signed.signed.asJson.canonical.getBytes) shouldBe true
    }.futureValue
  }

  keyTypeTest("persists signed payload when finding") { keyType =>
    val repoId = RepoId.generate()
    val rootKeyGenRequest = KeyGenRequest(KeyGenId.generate(),
      repoId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT, keyType.crypto.defaultKeySize, keyType)

    async {
      await(keyGenRepo.persist(rootKeyGenRequest))
      await(keyGenerationOp(rootKeyGenRequest))

      val signed = await(signedRootRoles.findFreshAndPersist(repoId))

      await(signedRootRoleRepo.findLatest(repoId)).content.signed.asJson shouldBe signed.signed.asJson
      await(signedRootRoleRepo.findLatest(repoId)).content.asJson shouldBe signed.asJson
    }.futureValue
  }

  keyTypeTest("returns renewed root.json when old one expired") { keyType =>

    val expiredSignedRootRoles = new SignedRootRoles(Duration.ofMillis(1))
    val repoId = RepoId.generate()
    val rootKeyGenRequest = KeyGenRequest(KeyGenId.generate(),
      repoId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT, keyType.crypto.defaultKeySize, keyType)

    async {
      await(keyGenRepo.persist(rootKeyGenRequest))
      await(keyGenerationOp(rootKeyGenRequest))
      val oldRole = await(expiredSignedRootRoles.findFreshAndPersist(repoId))

      oldRole.signed.expires.isBefore(Instant.now.plus(1, ChronoUnit.HOURS)) shouldBe true

      val signedRootRole = await(signedRootRoles.findFreshAndPersist(repoId))

      signedRootRole.signed.expires.isAfter(Instant.now.plus(30, ChronoUnit.DAYS)) shouldBe true

    }.futureValue
  }

  keyTypeTest("persists renewed root.json when old one expired") { keyType =>
    val expiredSignedRootRoles = new SignedRootRoles(Duration.ofMillis(1))
    val repoId = RepoId.generate()
    val rootKeyGenRequest = KeyGenRequest(KeyGenId.generate(),
      repoId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT, keyType.crypto.defaultKeySize, keyType)

    async {
      await(keyGenRepo.persist(rootKeyGenRequest))
      await(keyGenerationOp(rootKeyGenRequest))
      await(expiredSignedRootRoles.findFreshAndPersist(repoId))
      val signedRootRole = await(signedRootRoles.findFreshAndPersist(repoId))

      signedRootRole.signed.expires.isAfter(Instant.now.plus(30, ChronoUnit.DAYS)) shouldBe true

      await(signedRootRoleRepo.findLatest(repoId)).content.asJson shouldBe signedRootRole.asJson

    }.futureValue
  }

  keyTypeTest("returns old role when when keys are not online") { keyType =>
    val expiredSignedRootRoles = new SignedRootRoles(Duration.ofMillis(1))
    val repoId = RepoId.generate()
    val rootKeyGenRequest = KeyGenRequest(KeyGenId.generate(),
      repoId, KeyGenRequestStatus.REQUESTED, RoleType.ROOT, keyType.crypto.defaultKeySize, keyType)

    val rootRole = for {
      _ <- keyGenRepo.persist(rootKeyGenRequest)
      _ <- keyGenerationOp(rootKeyGenRequest)
      role <- expiredSignedRootRoles.findFreshAndPersist(repoId)
    } yield role

    val keyIds = rootRole.futureValue.signed.roleKeys(RoleType.ROOT).map(_.id)

    val fetchedRole = for {
      _ <- Future.sequence(keyIds.map(keyRepo.delete))
      role <- signedRootRoles.findFreshAndPersist(repoId)
    } yield role

    fetchedRole.futureValue.signed.asJson shouldBe rootRole.futureValue.signed.asJson
  }
}
