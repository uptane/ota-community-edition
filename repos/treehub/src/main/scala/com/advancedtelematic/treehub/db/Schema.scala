package com.advancedtelematic.treehub.db

import java.time.Instant
import com.advancedtelematic.data.DataType.ObjectStatus.ObjectStatus
import com.advancedtelematic.data.DataType.StaticDeltaMeta
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import com.advancedtelematic.libats.slick.codecs.SlickEnumeratum
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping
import io.circe.Json
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.jsonMapper
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object Schema {
  import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
  import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
  import com.advancedtelematic.data.DataType.*
  import SlickMappings.*

  implicit val staticDeltaStatusMapper: JdbcType[StaticDeltaMeta.Status] with BaseTypedType[StaticDeltaMeta.Status] = SlickEnumeratum.enumeratumMapper(StaticDeltaMeta.Status)

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "object") {
    def namespace = column[Namespace]("namespace")
    def id = column[ObjectId]("object_id")
    def size = column[Long]("size")
    def status = column[ObjectStatus]("status")
    def createdAt = column[Instant]("created_at")(javaInstantMapping)

    def pk = primaryKey("pk_object", (namespace, id))

    def uniqueNsId = index("object_unique_namespace", (namespace, id), unique = true)

    override def * = (namespace, id, size, status) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  val objects = TableQuery[TObjectTable]

  class ArchivedObjectsTable(tag: Tag) extends Table[(Namespace, ObjectId, Long, Instant, String)](tag, "archived_object") {
    def namespace = column[Namespace]("namespace")
    def id = column[ObjectId]("object_id")
    def reason = column[String]("reason")
    def clientCreatedAt = column[Instant]("client_created_at")(javaInstantMapping)
    def size = column[Long]("size")

    def pk = primaryKey("pk_archived_object", (namespace, id))

    override def * = (namespace, id, size, clientCreatedAt, reason)
  }

  val archivedObjects = TableQuery[ArchivedObjectsTable]

  case class RefTable(tag: Tag) extends Table[Ref](tag, "ref") {
    def namespace = column[Namespace]("namespace")
    def name = column[RefName]("name")
    def value = column[Commit]("value")
    def objectId = column[ObjectId]("object_id")
    def published = column[Boolean]("published")

    def pk = primaryKey("pk_ref", (namespace, name))

    def fk = foreignKey("fk_ref_object", objectId, objects)(_.id)

    override def * = (namespace, name, value, objectId) <> ((Ref.apply _).tupled, Ref.unapply)
  }

  protected[db] val refs = TableQuery[RefTable]

  case class ManifestTable(tag: Tag) extends Table[CommitManifest](tag, "manifest") {
    def namespace = column[Namespace]("namespace")
    def commit: Rep[ObjectId] = column[ObjectId]("object_id")
    def content = column[Json]("content")

    def pk = primaryKey("pk_ref", (namespace, commit))

    override def * = (namespace, commit, content) <> ((CommitManifest.apply _).tupled, CommitManifest.unapply)
  }

  protected[db] val manifests = TableQuery[ManifestTable]

  case class StaticDeltaMetaTable(tag: Tag) extends Table[StaticDeltaMeta](tag, "static_deltas") {
    def namespace = column[Namespace]("namespace")
    def id = column[DeltaId]("id")
    def superblockHash = column[SuperBlockHash]("superblock_hash")
    def to = column[Commit]("to")
    def from = column[Commit]("from")
    def size = column[Long]("size")

    def status = column[StaticDeltaMeta.Status]("status")

    def pk = primaryKey("pk_ref", (namespace, id))

    override def * = (namespace, id, from, to, superblockHash, size, status) <> ((StaticDeltaMeta.apply _).tupled, StaticDeltaMeta.unapply)
  }

  protected[db] val staticDeltas = TableQuery[StaticDeltaMetaTable]
}
