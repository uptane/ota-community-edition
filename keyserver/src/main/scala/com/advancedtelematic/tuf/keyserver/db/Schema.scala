package com.advancedtelematic.tuf.keyserver.db

import java.time.Instant

import com.advancedtelematic.libats.slick.db.SlickEncryptedColumn.EncryptedColumn
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.{
  JsonSignedPayload,
  KeyId,
  KeyType,
  RepoId,
  SignedPayload,
  TufKey,
  TufPrivateKey
}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType._
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus.KeyGenRequestStatus
import slick.jdbc.MySQLProfile.api._
import cats.syntax.either._
import com.advancedtelematic.libtuf.data.ClientCodecs._

object Schema {

  import com.advancedtelematic.libats.slick.codecs.SlickRefined._
  import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
  import com.advancedtelematic.libats.slick.db.SlickCirceMapper._
  import com.advancedtelematic.libats.slick.db.SlickUriMapper._
  import com.advancedtelematic.libats.slick.db.SlickExtensions._
  import com.advancedtelematic.libtuf_server.data.TufSlickMappings._
  import SlickMappings._

  class KeyGenRequestTable(tag: Tag) extends Table[KeyGenRequest](tag, "key_gen_requests") {
    def id = column[KeyGenId]("id", O.PrimaryKey)
    def repoId = column[RepoId]("repo_id")
    def status = column[KeyGenRequestStatus]("status")
    def roleType = column[RoleType]("role_type")
    def keyType = column[KeyType]("key_type")
    def keySize = column[Int]("key_size")
    def threshold = column[Int]("threshold")
    def description = column[String]("description")

    def uniqueRepoIdRoleTypeIdx =
      index("key_gen_requests_unique_idx", (repoId, roleType), unique = true)

    override def * = (id, repoId, status, roleType, keySize, keyType, threshold, description) <> (
      (KeyGenRequest.apply _).tupled,
      KeyGenRequest.unapply
    )

  }

  protected[db] val keyGenRequests = TableQuery[KeyGenRequestTable]

  class KeyTable(tag: Tag) extends Table[Key](tag, "keys") {
    def id = column[KeyId]("key_id", O.PrimaryKey)
    def repoId = column[RepoId]("repo_id")
    def roleType = column[RoleType]("role_type")
    def keyType = column[KeyType]("key_type") // TODO: remove `key_type`, then remove KeyType Enum ?
    def publicKey = column[TufKey]("public_key")
    def privateKey = column[EncryptedColumn[TufPrivateKey]]("private_key")

    override def * = (id, repoId, roleType, keyType, publicKey, privateKey.decrypted) <> (
      (Key.apply _).tupled,
      Key.unapply
    )

  }

  protected[db] val keys = TableQuery[KeyTable]

  class SignedRootRoleTable(tag: Tag) extends Table[SignedRootRole](tag, "signed_root_roles") {
    def repoId = column[RepoId]("repo_id")
    def expiresAt = column[Instant]("expires_at")(javaInstantMapping)
    def version = column[Int]("version")
    def content = column[JsonSignedPayload]("signed_payload")

    def pk = primaryKey("pk_signed_root_roles", (repoId, version))

    private def content_parsed = content <>
      (
        { c => SignedPayload(c.signatures, c.signed.as[RootRole].valueOr(throw _), c.signed) },
        (x: SignedPayload[RootRole]) => Some(JsonSignedPayload(x.signatures, x.json))
      )

    override def * = (repoId, content_parsed, expiresAt, version) <> (
      (SignedRootRole.apply _).tupled,
      SignedRootRole.unapply
    )

  }

  protected[db] val signedRootRoles = TableQuery[SignedRootRoleTable]
}
