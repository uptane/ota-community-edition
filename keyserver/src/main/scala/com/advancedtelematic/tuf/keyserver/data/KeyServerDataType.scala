package com.advancedtelematic.tuf.keyserver.data

import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{
  KeyId,
  KeyType,
  RepoId,
  SignedPayload,
  TufKey,
  TufKeyPair,
  TufPrivateKey
}
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus.KeyGenRequestStatus

import java.time.Instant
import java.util.UUID
import scala.util.Try

object KeyServerDataType {

  object KeyGenRequestStatus extends Enumeration {
    type KeyGenRequestStatus = Value

    val REQUESTED, GENERATED, ERROR = Value
  }

  case class KeyGenId(uuid: UUID) extends UUIDKey
  object KeyGenId extends UUIDKeyObj[KeyGenId]

  case class KeyGenRequest(id: KeyGenId,
                           repoId: RepoId,
                           status: KeyGenRequestStatus,
                           roleType: RoleType,
                           keySize: Int,
                           keyType: KeyType,
                           threshold: Int = 1,
                           description: String = "") {
    require(keyType.crypto.validKeySize(keySize), s"Invalid keysize ($keySize) for $keyType")
  }

  implicit class SignedPayloadDbOps(value: SignedPayload[RootRole]) {

    def toDbSignedRole(repoId: RepoId): SignedRootRole =
      SignedRootRole(repoId, value, value.signed.expires, value.signed.version)

  }

  case class SignedRootRole(repoId: RepoId,
                            content: SignedPayload[RootRole],
                            expiresAt: Instant,
                            version: Int)

  case class Key(id: KeyId,
                 repoId: RepoId,
                 roleType: RoleType,
                 keyType: KeyType,
                 publicKey: TufKey,
                 privateKey: TufPrivateKey) {
    def toTufKeyPair: Try[TufKeyPair] = keyType.crypto.castToKeyPair(publicKey, privateKey)
  }

  implicit class TufKeyDbOps(value: TufKeyPair) {

    def toDbKey(repoId: RepoId, roleType: RoleType): Key =
      Key(value.pubkey.id, repoId, roleType, value.pubkey.keytype, value.pubkey, value.privkey)

  }

}
