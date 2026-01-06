package com.advancedtelematic.tuf.reposerver.db

import java.time.Instant
import org.apache.pekko.http.scaladsl.model.Uri
import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libtuf.data.ClientDataType.{
  DelegatedRoleName,
  DelegationFriendlyName,
  TargetCustom
}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId, TargetFilename}
import slick.jdbc.MySQLProfile.api.*
import SlickMappings.*
import com.advancedtelematic.libtuf_server.data.Requests.TargetComment
import com.advancedtelematic.tuf.reposerver.db.DBDataType.{DbDelegation, DbSignedRole}
import SlickMappings.delegatedRoleNameMapper
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.StorageMethod.StorageMethod
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.{DelegatedTargetItem, TargetItem}
import io.circe.Json

object Schema {

  import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
  import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
  import com.advancedtelematic.libats.slick.db.SlickUriMapper.*
  import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
  import com.advancedtelematic.libats.slick.db.SlickCirceMapper.*
  import com.advancedtelematic.libtuf_server.data.TufSlickMappings.*
  import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping

  class DelegatedItemTable(tag: Tag) extends Table[DelegatedTargetItem](tag, "delegated_items") {
    def repoId = column[RepoId]("repo_id")
    def filename = column[TargetFilename]("filename")
    def roleName = column[DelegatedRoleName]("rolename")
    def custom = column[Option[Json]]("custom")
    def checksum = column[Checksum]("checksum")
    def length = column[Long]("length")
    def updatedAt = column[Instant]("updated_at")(javaInstantMapping)

    def targetCreatedAt =
      column[Option[Instant]]("target_created_at")(javaInstantMapping.optionType)

    def pk = primaryKey("delegated_items_pk", (repoId, roleName, filename))

    override def * = (repoId, filename, roleName, checksum, length, targetCreatedAt, custom) <> (
      (DelegatedTargetItem.apply _).tupled,
      DelegatedTargetItem.unapply
    )

  }

  val delegatedTargetItems = TableQuery[DelegatedItemTable]

  class TargetItemTable(tag: Tag) extends Table[TargetItem](tag, "target_items") {
    def repoId = column[RepoId]("repo_id")
    def filename = column[TargetFilename]("filename")
    def uri = column[Option[Uri]]("uri")
    def custom = column[Option[TargetCustom]]("custom")
    def checksum = column[Checksum]("checksum")
    def length = column[Long]("length")
    def storageMethod = column[StorageMethod]("storage_method")
    def updatedAt = column[Instant]("updated_at")(javaInstantMapping)

    def pk = primaryKey("target_items_pk", (repoId, filename))

    override def * = (repoId, filename, uri, checksum, length, custom, storageMethod) <> (
      (TargetItem.apply _).tupled,
      TargetItem.unapply
    )

  }

  protected[db] val targetItems = TableQuery[TargetItemTable]

  class SignedRoleTable(tag: Tag) extends Table[DbSignedRole](tag, "signed_roles") {
    def repoId = column[RepoId]("repo_id")
    def roleType = column[RoleType]("role_type")
    def content = column[JsonSignedPayload]("content")
    def checksum = column[Checksum]("checksum")
    def length = column[Long]("length")
    def version = column[Int]("version")
    def expiresAt = column[Instant]("expires_at")(javaInstantMapping)

    def pk = primaryKey("signed_role_pk", (repoId, roleType))

    override def * = (repoId, roleType, content, checksum, length, version, expiresAt) <> (
      (DbSignedRole.apply _).tupled,
      DbSignedRole.unapply
    )

  }

  protected[db] val signedRoles = TableQuery[SignedRoleTable]

  class RepoNamespaceTable(tag: Tag)
      extends Table[(RepoId, Namespace, Option[Instant])](tag, "repo_namespaces") {
    def repoId = column[RepoId]("repo_id")
    def namespace = column[Namespace]("namespace")

    def expiresNotBefore =
      column[Option[Instant]]("expires_not_before")(javaInstantMapping.optionType)

    def pk = primaryKey("repo_namespaces_pk", namespace)

    override def * = (repoId, namespace, expiresNotBefore)
  }

  protected[db] val repoNamespaces = TableQuery[RepoNamespaceTable]

  class PackageCommentTable(tag: Tag)
      extends Table[(RepoId, TargetFilename, TargetComment)](tag, "filename_comments") {
    def repoId = column[RepoId]("repo_id")
    def filename = column[TargetFilename]("filename")
    def comment = column[TargetComment]("comment")
    def updatedAt = column[Instant]("updated_at")(javaInstantMapping)

    def pk = primaryKey("repo_name_pk", (repoId, filename))

    def targetItemFk = foreignKey("target_item_fk", (repoId, filename), targetItems)(
      c => (c.repoId, c.filename),
      onDelete = ForeignKeyAction.Cascade
    )

    override def * = (repoId, filename, comment)
  }

  protected[db] val filenameComments = TableQuery[PackageCommentTable]

  class DelegationTable(tag: Tag) extends Table[DbDelegation](tag, "delegations") {

    implicit val remoteHeadersMapper: slick.jdbc.MySQLProfile.BaseColumnType[Map[String, String]] =
      SlickMappings.remoteHeadersMapper

    def repoId = column[RepoId]("repo_id")
    def roleName = column[DelegatedRoleName]("name")
    def content = column[JsonSignedPayload]("content")
    def remoteUri = column[Option[Uri]]("uri")
    def remoteHeaders = column[Map[String, String]]("remote_headers")
    def lastFetched = column[Option[Instant]]("last_fetched_at")(javaInstantMapping.optionType)
    def friendlyName = column[Option[DelegationFriendlyName]]("friendly_name")

    def pk = primaryKey("delegations_pk", (repoId, roleName))

    override def * =
      (repoId, roleName, content, remoteUri, lastFetched, remoteHeaders, friendlyName) <> (
        (DbDelegation.apply _).tupled,
        DbDelegation.unapply
      )

  }

  protected[db] val delegations = TableQuery[DelegationTable]
}
