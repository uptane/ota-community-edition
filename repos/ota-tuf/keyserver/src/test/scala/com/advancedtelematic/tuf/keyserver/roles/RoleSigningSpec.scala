package com.advancedtelematic.tuf.keyserver.roles

import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, RepoId, RoleType, Signature, TufKeyPair}
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType._
import com.advancedtelematic.tuf.util.{KeyTypeSpecSupport, TufKeyserverSpec}
import io.circe.syntax._
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.tuf.keyserver.db.KeyRepositorySupport
import org.bouncycastle.util.encoders.Base64
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import com.advancedtelematic.libats.test.MysqlDatabaseSpec

import scala.concurrent.ExecutionContext

case class TestPayload(propertyB: String = "some B", propertyA: String = "some A",
                       arrayMapProp: List[Map[String, Int]] = List(Map("bbb" -> 1, "aaa" -> 0)),
                       mapProp: Map[String, Int] = Map("bb" -> 1, "aa" -> 0))

class RoleSigningSpec extends TufKeyserverSpec with MysqlDatabaseSpec with PatienceConfiguration with KeyTypeSpecSupport with KeyRepositorySupport {

  implicit val ec = ExecutionContext.global

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(3, Seconds))

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[TestPayload]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[TestPayload]

  val roleSigning = new RoleSigning()

  def genKeys(keyType: KeyType): (TufKeyPair, Key) = {
    val keyPair = keyType.crypto.generateKeyPair (keyType.crypto.defaultKeySize)
    val dbKey = Key (keyPair.pubkey.id, RepoId.generate (), RoleType.ROOT, keyType, keyPair.pubkey, keyPair.privkey)

    (keyPair, dbKey)
  }

  val payload = TestPayload()

  def roleSignTest(name: String)(test: (TufKeyPair, Key) => Any): Unit = {
    val setupFor = (keyType: KeyType) => {
      val (keyPair, dbKey) = genKeys(keyType)
      keyRepo.persist(dbKey).futureValue
      (keyPair, dbKey)
    }

    keyTypeTest(name)(setupFor.andThen(test.tupled))
  }

  roleSignTest("signs a payload with a key ") { (_, dbKey) =>
    val signature = roleSigning.signForClient(payload.asJson)(dbKey.publicKey).futureValue
    new String(Base64.decode(signature.sig.value)) shouldBe a[String]
  }

  roleSignTest("generates valid signatures") { (keyPair, dbKey) =>
    val clientSignature = roleSigning.signForClient(payload.asJson)(dbKey.publicKey).futureValue
    val signature = Signature(clientSignature.sig, clientSignature.method)

    TufCrypto.isValid(signature, keyPair.pubkey, payload.asJson.canonical.getBytes) shouldBe true
  }

  roleSignTest("generates valid signatures when verifying with canonical representation") { (keyPair, dbKey) =>
    val clientSignature = roleSigning.signForClient(payload.asJson)(dbKey.publicKey).futureValue
    val signature = Signature(clientSignature.sig, clientSignature.method)

    val canonicalJson = """{"arrayMapProp":[{"aaa":0,"bbb":1}],"mapProp":{"aa":0,"bb":1},"propertyA":"some A","propertyB":"some B"}""".getBytes

    TufCrypto.isValid(signature, keyPair.pubkey, canonicalJson) shouldBe true
  }
}
