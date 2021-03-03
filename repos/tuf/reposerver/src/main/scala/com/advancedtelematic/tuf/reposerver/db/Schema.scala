package com.advancedtelematic.tuf.reposerver.db

import java.time.Instant

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedRoleName, TargetCustom}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId, TargetFilename}
import slick.jdbc.MySQLProfile.api._
import SlickMappings._
import com.advancedtelematic.libtuf_server.data.Requests.TargetComment
import com.advancedtelematic.tuf.reposerver.db.DBDataType.{DbDelegation, DbSignedRole}
import SlickValidatedString._
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.StorageMethod.StorageMethod
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.TargetItem

object Schema {
  import com.advancedtelematic.libats.slick.codecs.SlickRefined._
  import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
  import com.advancedtelematic.libats.slick.db.SlickUriMapper._
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._
  import com.advancedtelematic.libats.slick.db.SlickCirceMapper._
  import com.advancedtelematic.libtuf_server.data.TufSlickMappings._
  import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping

  class TargetItemTable(tag: Tag) extends Table[TargetItem](tag, "target_items") {
    def repoId = column[RepoId]("repo_id")
    def filename = column[TargetFilename]("filename")
    def uri = column[Option[Uri]]("uri")
    def custom = column[Option[TargetCustom]]("custom")
    def checksum = column[Checksum]("checksum")
    def length = column[Long]("length")
    def storageMethod = column[StorageMethod]("storage_method")

    def pk = primaryKey("target_items_pk", (repoId, filename))

    override def * = (repoId, filename, uri, checksum, length, custom, storageMethod) <> ((TargetItem.apply _).tupled, TargetItem.unapply)
  }

  protected [db] val targetItems = TableQuery[TargetItemTable]

  class SignedRoleTable(tag: Tag) extends Table[DbSignedRole](tag, "signed_roles") {
    def repoId = column[RepoId]("repo_id")
    def roleType = column[RoleType]("role_type")
    def content = column[JsonSignedPayload]("content")
    def checksum = column[Checksum]("checksum")
    def length = column[Long]("length")
    def version = column[Int]("version")
    def expiresAt = column[Instant]("expires_at")

    def pk = primaryKey("signed_role_pk", (repoId, roleType))

    override def * = (repoId, roleType, content, checksum, length, version, expiresAt) <> ((DbSignedRole.apply _).tupled, DbSignedRole.unapply)
  }

  protected [db] val signedRoles = TableQuery[SignedRoleTable]

  class RepoNamespaceTable(tag: Tag) extends Table[(RepoId, Namespace)](tag, "repo_namespaces") {
    def repoId = column[RepoId]("repo_id")
    def namespace = column[Namespace]("namespace")

    def pk = primaryKey("repo_namespaces_pk", namespace)

    override def * = (repoId, namespace)
  }

  protected [db] val repoNamespaces = TableQuery[RepoNamespaceTable]

  class PackageCommentTable(tag: Tag) extends Table[(RepoId, TargetFilename, TargetComment)](tag, "filename_comments") {
    def repoId = column[RepoId]("repo_id")
    def filename = column[TargetFilename]("filename")
    def comment = column[TargetComment]("comment")

    def pk = primaryKey("repo_name_pk", (repoId, filename))

    def targetItemFk = foreignKey("target_item_fk", (repoId, filename), targetItems)(c => (c.repoId, c.filename), onDelete = ForeignKeyAction.Cascade)

    override def * = (repoId, filename, comment)
  }

  protected [db] val filenameComments = TableQuery[PackageCommentTable]

  class DelegationTable(tag: Tag) extends Table[DbDelegation](tag, "delegations") {
    def repoId = column[RepoId]("repo_id")
    def roleName = column[DelegatedRoleName]("name")
    def content = column[JsonSignedPayload]("content")

    def pk = primaryKey("delegations_pk", (repoId, roleName))

    override def * = (repoId, roleName, content) <> ((DbDelegation.apply _).tupled, DbDelegation.unapply)
  }

  protected [db] val delegations = TableQuery[DelegationTable]
}
