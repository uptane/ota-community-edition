package com.advancedtelematic.libtuf.data

import cats.data.Validated.Invalid
import cats.data.{ValidatedNel, _}
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, RoleType, JsonSignedPayload, SignedPayload, TufKey}
import cats.implicits._
import io.circe.syntax._
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import cats.implicits._


object RootRoleValidation {
  private sealed trait RootRoleValidatedSig
  private case class ValidSignature(keyId: KeyId) extends RootRoleValidatedSig
  private case class InvalidSignature(msg: String) extends RootRoleValidatedSig

  def rootRawJsonIsValid(signedPayloadJson: JsonSignedPayload): ValidatedNel[String, SignedPayload[RootRole]] = {
    val parsedE = signedPayloadJson.signed.as[RootRole].leftMap(_.message)

    Validated.fromEither(parsedE).toValidatedNel.andThen { parsed: RootRole =>
      Validated.condNel(signedPayloadJson.signed.canonical == parsed.asJson.canonical, parsed, "an incompatible encoder was used to encode root.json")
    }.map { parsed =>
      SignedPayload(signedPayloadJson.signatures, parsed, signedPayloadJson.signed)
    }
  }

  def newRootIsValid(newSignedRoot: SignedPayload[RootRole], oldRoot: SignedPayload[RootRole]): ValidatedNel[String, SignedPayload[RootRole]] = {
    val validationWithOldRoot = validateThresholdWithRole(newSignedRoot, oldRoot.signed)
    val validationWithNewRoot = validateThresholdWithRole(newSignedRoot, newSignedRoot.signed)

    val newRoleValidation =
      (validateVersionBump(oldRoot.signed, newSignedRoot.signed), validationWithOldRoot, validationWithNewRoot)
        .mapN { (_, _, _) => newSignedRoot }

    newRoleValidation
  }

  def rootIsValid(signedRoot: SignedPayload[RootRole]): ValidatedNel[String, SignedPayload[RootRole]] =
    validateThresholdWithRole(signedRoot, signedRoot.signed).map(_ => signedRoot)



  private def validateThresholdWithRole(newSignedRoot: SignedPayload[RootRole], roleForValidation: RootRole): ValidatedNel[String, Int] = {

    val roleRootKeys = roleForValidation.roles.get(RoleType.ROOT)
      .toValidNel(s"root.json version ${roleForValidation.version} does not contain keys for ROOT")

    roleRootKeys.andThen { roleKeys =>
      val publicKeys = roleForValidation.keys.filterKeys(roleKeys.keyids.contains)
      val sigs = validateSignatures(newSignedRoot, publicKeys)
      thresholdIsSatisfied(newSignedRoot.signed.version, roleForValidation.version, roleKeys.threshold, sigs)
    }
  }

  private def validateVersionBump(oldRoot: RootRole, newRoot: RootRole): ValidatedNel[String, RootRole] =
    Validated.cond(newRoot.version == oldRoot.version + 1, newRoot, s"Invalid version bump from ${oldRoot.version} to ${newRoot.version}").toValidatedNel

  private def thresholdIsSatisfied(underValidationVersion: Int, keysFromVersion: Int,
                                   threshold: Int, signatures: List[RootRoleValidatedSig]): ValidatedNel[String, Int] = {
    import cats.syntax.validated._

    val validCount = signatures.count {
      case ValidSignature(_) => true
      case _ => false
    }

    val errors = signatures.collect { case InvalidSignature(msg) => msg }

    if(threshold <= 0)
      s"invalid threshold for root role version $keysFromVersion".invalidNel
    else if (validCount < threshold) {
      val base = NonEmptyList.of(s"Root role version $keysFromVersion requires $threshold valid signatures in version $underValidationVersion, $validCount supplied")
      Invalid(base ++ errors)
    } else if(validCount >= threshold)
      threshold.validNel
    else
      Invalid(NonEmptyList.fromListUnsafe(errors))
  }

  private def validateSignatures(signedPayload: SignedPayload[RootRole], publicKeys: Map[KeyId, TufKey]): List[RootRoleValidatedSig] = {
    val roleSignatures = signedPayload.signatures.map { sig => sig.keyid -> sig }.toMap

    publicKeys.toList.map { case (keyId, tufKey) =>
      roleSignatures.get(keyId) match {
        case Some(sig) =>
          if (TufCrypto.isValid(sig, tufKey, signedPayload.json))
            ValidSignature(keyId)
          else
            InvalidSignature(s"Invalid signature for key $keyId in root.json version ${signedPayload.signed.version}")
        case None =>
          InvalidSignature(s"No signature found for key $keyId in root.json version ${signedPayload.signed.version}")
      }
    }
  }
}
