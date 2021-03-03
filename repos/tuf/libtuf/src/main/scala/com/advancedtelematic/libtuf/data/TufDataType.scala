package com.advancedtelematic.libtuf.data

import java.security.{PrivateKey, PublicKey}
import java.util.UUID

import cats.Show
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.TufDataType.SignatureMethod.SignatureMethod
import eu.timepit.refined.api.{Refined, Validate}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import net.i2p.crypto.eddsa.{EdDSAPrivateKey, EdDSAPublicKey}


object TufDataType {
  final case class ValidHardwareIdentifier()
  type HardwareIdentifier = Refined[String, ValidHardwareIdentifier]
  implicit val validHardwareIdentifier: Validate.Plain[String, ValidHardwareIdentifier] =
    ValidationUtils.validInBetween(min = 0, max = 200, ValidHardwareIdentifier())

  object TargetFormat extends Enumeration {
    type TargetFormat = Value

    val OSTREE, BINARY = Value
  }

  case class TargetName(value: String) extends AnyVal
  case class TargetVersion(value: String) extends AnyVal

  case class ValidTargetFilename()
  type TargetFilename = Refined[String, ValidTargetFilename]

  implicit val validTargetFilename: Validate.Plain[String, ValidTargetFilename] =
    Validate.fromPredicate(
      f => f.nonEmpty && f.length < 254 && !f.contains(".."),
      _ => "TargetFilename cannot be empty or bigger than 254 chars or contain `..`",
      ValidTargetFilename()
    )

  final case class OperationResult(target: TargetFilename, hashes: Map[HashMethod, Refined[String, ValidChecksum]],
                                   length: Long, resultCode: Int, resultText: String) {
    def isSuccess:Boolean = resultCode == 0 || resultCode == 1
  }

  case class ValidKeyId()
  type KeyId = Refined[String, ValidKeyId]
  implicit val validKeyId: Validate.Plain[String, ValidKeyId] =
    ValidationUtils.validHexValidation(ValidKeyId(), length = 64)

  case class ValidSignature()
  case class Signature(sig: Refined[String, ValidSignature], method: SignatureMethod = SignatureMethod.RSASSA_PSS_SHA256)
  implicit val validSignature: Validate.Plain[String, ValidSignature] =
    ValidationUtils.validBase64Validation(ValidSignature())

  object RoleType extends Enumeration {
    type RoleType = Value

    val ROOT, SNAPSHOT, TARGETS, TIMESTAMP = Value

    val ALL = List(ROOT, SNAPSHOT, TARGETS, TIMESTAMP)

    implicit val show = Show.show[Value](_.toString.toLowerCase)
  }

  object SignatureMethod extends Enumeration {
    type SignatureMethod = Value

    val RSASSA_PSS_SHA256 = Value("rsassa-pss-sha256")

    val ED25519 = Value("ed25519")

    val ECPrime256V1 = Value("ecPrime256v1")
  }

  case class RepoId(uuid: UUID) extends UUIDKey
  object RepoId extends UUIDKeyObj[RepoId]

  case class ClientSignature(keyid: KeyId, method: SignatureMethod, sig: Refined[String, ValidSignature]) {
    def toSignature: Signature = Signature(sig, method)
  }

  implicit class SignatureToClientSignatureOps(value: Signature) {
    def toClient(keyId: KeyId): ClientSignature = ClientSignature(keyId, value.method, value.sig)
  }

  object SignedPayload {
    def apply[T : Encoder](signatures: Seq[ClientSignature], signed: T, json: Json): SignedPayload[T] =
      new SignedPayload(signatures, signed, json)
  }

  class SignedPayload[T : Encoder](val signatures: Seq[ClientSignature], val signed: T, val json: Json) {
    def asJsonSignedPayload: JsonSignedPayload = JsonSignedPayload(signatures, json)

    def updated(signatures: Seq[ClientSignature] = signatures, signed: T = signed): SignedPayload[T] =
      new SignedPayload[T](signatures, signed, signed.asJson)


    override def toString: String = s"SignedPayload($signatures, $signed, ${json.asJson.noSpaces})"

    def canEqual(other: Any): Boolean = other.isInstanceOf[SignedPayload[T]]

    override def equals(other: Any): Boolean = other match {
      case that: SignedPayload[_] ⇒
        (that canEqual this) &&
          signatures == that.signatures &&
          signed == that.signed &&
          json == that.json
      case _ ⇒ false
    }

    override def hashCode(): Int = {
      val state = Seq(signatures, signed, json)
      state.map(_.hashCode()).foldLeft(0)((a, b) ⇒ 31 * a + b)
    }
  }

  case class JsonSignedPayload(signatures: Seq[ClientSignature], signed: Json)

  object KeyType {
    val default = RsaKeyType
  }

  sealed trait KeyType {
    type Pub <: TufKey
    type Priv <: TufPrivateKey
    type Pair <: TufKeyPair

    val crypto: TufCrypto[this.type]
  }

  case object RsaKeyType extends KeyType {
    type Pub = RSATufKey
    type Priv = RSATufPrivateKey
    type Pair = RSATufKeyPair

    val crypto = TufCrypto.rsaCrypto
  }
  case object Ed25519KeyType extends KeyType {
    type Pub = Ed25519TufKey
    type Priv = Ed25519TufPrivateKey
    type Pair = Ed25519TufKeyPair

    val crypto = TufCrypto.ed25519Crypto
  }
  case object EcPrime256KeyType extends KeyType {
    type Pub = EcPrime256TufKey
    type Priv = EcPrime256TufPrivateKey
    type Pair = EcPrime256TufKeyPair

    val crypto = TufCrypto.ecPrime256Crypto
  }

  sealed trait TufKey {
    val keyval: PublicKey
    lazy val id = keytype.crypto.keyId(this)
    def keytype: KeyType
  }
  case class RSATufKey(override val keyval: PublicKey) extends TufKey {
    override def keytype: KeyType = RsaKeyType
  }
  case class Ed25519TufKey(override val keyval: EdDSAPublicKey) extends TufKey {
    override def keytype: KeyType = Ed25519KeyType
  }
  case class EcPrime256TufKey(override val keyval: PublicKey) extends TufKey {
    override def keytype: KeyType = EcPrime256KeyType
  }

  sealed trait TufPrivateKey {
    val keyval: PrivateKey
    def keytype: KeyType
  }
  case class RSATufPrivateKey(override val keyval: PrivateKey) extends TufPrivateKey {
    override def keytype: KeyType = RsaKeyType
  }
  case class Ed25519TufPrivateKey(override val keyval: EdDSAPrivateKey) extends TufPrivateKey {
    override def keytype: KeyType = Ed25519KeyType
  }
  case class EcPrime256TufPrivateKey(override val keyval: PrivateKey) extends TufPrivateKey {
    override def keytype: KeyType = EcPrime256KeyType
  }

  sealed trait TufKeyPair {
    val pubkey: TufKey
    val privkey: TufPrivateKey
  }
  object TufKeyPair {
    def unapply(arg: TufKeyPair): Option[(TufKey, TufPrivateKey)] = Some(arg.pubkey -> arg.privkey)
  }
  case class RSATufKeyPair(override val pubkey: RSATufKey, override val privkey: RSATufPrivateKey) extends TufKeyPair
  case class Ed25519TufKeyPair(override val pubkey: Ed25519TufKey, override val privkey: Ed25519TufPrivateKey) extends TufKeyPair
  case class EcPrime256TufKeyPair(override val pubkey: EcPrime256TufKey, override val privkey: EcPrime256TufPrivateKey) extends TufKeyPair
}
