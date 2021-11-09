package com.advancedtelematic.tuf.keyserver.data

import java.security.PublicKey
import java.time.Instant
import java.util.UUID

import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.libats.slick.db.SlickEncryptedColumn.EncryptedColumn
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, KeyType, RepoId, JsonSignedPayload, SignedPayload, TufKey, TufKeyPair, TufPrivateKey}
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus.KeyGenRequestStatus
import com.advancedtelematic.tuf.keyserver.http.Errors
import io.circe.{Decoder, Json}

import scala.concurrent.Future
import scala.util.Try

object KeyServerDataType {
  object KeyGenRequestStatus extends Enumeration {
    type KeyGenRequestStatus = Value

    val REQUESTED, GENERATED, ERROR = Value
  }

  case class KeyGenId(uuid: UUID) extends UUIDKey
  object KeyGenId extends UUIDKeyObj[KeyGenId]

  case class KeyGenRequest(id: KeyGenId, repoId: RepoId,
                           status: KeyGenRequestStatus, roleType: RoleType,
                           keySize: Int,
                           keyType: KeyType,
                           threshold: Int = 1,
                           description: String = "") {
    require(keyType.crypto.validKeySize(keySize), s"Invalid keysize ($keySize) for $keyType")
  }

  object SignedRootRole {
    import com.advancedtelematic.libtuf.data.ClientCodecs._

    def fromSignedPayload(repoId: RepoId, payload: SignedPayload[RootRole]): SignedRootRole = {
      val content = SignedPayload(payload.signatures, payload.signed, payload.json)
      SignedRootRole(repoId, content, payload.signed.expires, payload.signed.version)
    }
  }

  case class SignedRootRole(repoId: RepoId, content: SignedPayload[RootRole], expiresAt: Instant, version: Int)

  case class Key(id: KeyId, repoId: RepoId, roleType: RoleType, keyType: KeyType, publicKey: TufKey, privateKey: TufPrivateKey) {
    def toTufKeyPair: Try[TufKeyPair] = keyType.crypto.castToKeyPair(publicKey, privateKey)
  }
}
