package com.advancedtelematic.libtuf.data

import cats.data.*
import cats.data.Validated.Invalid
import cats.implicits.*
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TufRole, TufRoleOps}
import com.advancedtelematic.libtuf.data.TufDataType.{
  JsonSignedPayload,
  KeyId,
  SignedPayload,
  TufKey
}
import io.circe.Codec
import io.circe.syntax.*

object RoleValidation {
  private sealed trait RoleValidatedSig
  private case class ValidSignature(keyId: KeyId) extends RoleValidatedSig
  private case class InvalidSignature(msg: String) extends RoleValidatedSig

  def rawJsonIsValid[T: Codec](signedPayloadJson: JsonSignedPayload)(
    implicit tufRole: TufRole[T]): ValidatedNel[String, SignedPayload[T]] = {
    val parsedE = signedPayloadJson.signed.as[T].leftMap(_.message).toValidatedNel

    parsedE
      .andThen { (parsed: T) =>
        Validated.condNel(
          signedPayloadJson.signed.canonical == parsed.asJson.canonical,
          parsed,
          s"an incompatible encoder was used to encode ${tufRole.metaPath.value}"
        )
      }
      .map { parsed =>
        SignedPayload(signedPayloadJson.signatures, parsed, signedPayloadJson.signed)
      }
  }

  def roleIsValid[T: TufRole](newSignedRole: SignedPayload[T],
                              rootRole: RootRole): ValidatedNel[String, SignedPayload[T]] = {
    val roleRootKeys = rootRole.roles
      .get(newSignedRole.signed.roleType)
      .toValidNel(
        s"root.json version ${rootRole.version} does not contain keys for ${newSignedRole.signed.roleType}"
      )

    roleRootKeys.andThen { roleKeys =>
      val publicKeys = rootRole.keys.view.filterKeys(roleKeys.keyids.contains).toMap
      val sigs = validateSignatures(newSignedRole, publicKeys)

      val validCount = sigs.count {
        case ValidSignature(_) => true
        case _                 => false
      }

      val errors = sigs.collect { case InvalidSignature(msg) => msg }

      if (roleKeys.threshold <= 0)
        s"invalid threshold for root role version ${rootRole.version}".invalidNel
      else if (validCount < roleKeys.threshold) {
        val base = NonEmptyList.of(
          s"root.json version ${rootRole.version} requires ${roleKeys.threshold} valid signatures for ${newSignedRole.signed.metaPath} version ${newSignedRole.signed.version}, $validCount supplied"
        )
        Invalid(base ++ errors)
      } else
        newSignedRole.validNel
    }
  }

  private def validateSignatures[T: TufRole](
    signedPayload: SignedPayload[T],
    publicKeys: Map[KeyId, TufKey]): List[RoleValidatedSig] = {
    val roleSignatures = signedPayload.signatures.map(sig => sig.keyid -> sig).toMap

    publicKeys.toList.map { case (keyId, tufKey) =>
      roleSignatures.get(keyId) match {
        case Some(sig) =>
          if (TufCrypto.isValid(sig, tufKey, signedPayload.json))
            ValidSignature(keyId)
          else
            InvalidSignature(
              s"Invalid signature for key $keyId in ${signedPayload.signed.metaPath} version ${signedPayload.signed.version}"
            )
        case None =>
          InvalidSignature(
            s"No signature found for key $keyId in ${signedPayload.signed.metaPath} version ${signedPayload.signed.version}"
          )
      }
    }
  }

}

object RootRoleValidation {

  def newRootIsValid(
    newSignedRoot: SignedPayload[RootRole],
    oldRoot: SignedPayload[RootRole]): ValidatedNel[String, SignedPayload[RootRole]] = {
    val validationWithOldRoot = RoleValidation.roleIsValid(newSignedRoot, oldRoot.signed)
    val validationWithNewRoot = RoleValidation.roleIsValid(newSignedRoot, newSignedRoot.signed)

    val newRoleValidation =
      (
        validateVersionBump(oldRoot.signed, newSignedRoot.signed),
        validationWithOldRoot,
        validationWithNewRoot
      )
        .mapN((_, _, _) => newSignedRoot)

    newRoleValidation
  }

  def rootIsValid(
    signedRoot: SignedPayload[RootRole]): ValidatedNel[String, SignedPayload[RootRole]] =
    RoleValidation.roleIsValid(signedRoot, signedRoot.signed)

  private def validateVersionBump[T: TufRole](oldRole: T, newRole: T): ValidatedNel[String, T] =
    Validated
      .cond(
        newRole.version == oldRole.version + 1,
        newRole,
        s"Invalid version bump from ${oldRole.version} to ${newRole.version}"
      )
      .toValidatedNel

}
