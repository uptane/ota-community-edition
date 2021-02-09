package com.advancedtelematic.director.db

import java.time.Instant

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.director.data.DbDataType._
import com.advancedtelematic.libats.data
import com.advancedtelematic.libats.data.DataType.{Checksum, CorrelationId, Namespace}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, JsonSignedPayload, RepoId, SignedPayload, TargetFilename, TargetName, TufKey}
import io.circe.Json
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.jsonMapper
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import slick.ast.Subquery.Default
import slick.sql.SqlProfile.ColumnOption


object Schema {
  import SlickMapping._
  import com.advancedtelematic.libats.slick.codecs.SlickRefined._
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._
  import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping
  import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
  import com.advancedtelematic.libats.slick.db.SlickUrnMapper._
  import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric._
  import com.advancedtelematic.libtuf_server.data.TufSlickMappings._
  import com.advancedtelematic.libats.slick.db.SlickUriMapper._

  class DevicesTable(tag: Tag) extends Table[Device](tag, "devices") {
    def namespace = column[Namespace]("namespace")
    def id = column[DeviceId]("id")
    def primaryEcu = column[EcuIdentifier]("primary_ecu_id")
    def generatedMetadataOutdated = column[Boolean]("generated_metadata_outdated")
    def deleted = column[Boolean]("deleted", O.Default(false))
    def createdAt = column[Instant]("created_at")

    def pk = primaryKey("devices_pk", id)

    override def * = (namespace, id, primaryEcu, generatedMetadataOutdated, deleted) <> ((Device.apply _).tupled, Device.unapply)
  }

  protected [db] val allDevices = TableQuery[DevicesTable]

  protected [db] val activeDevices = TableQuery[DevicesTable].filter(_.deleted === false)

  class EcusTable(tag: Tag) extends Table[Ecu](tag, "ecus") {
    def namespace = column[Namespace]("namespace")
    def ecuSerial = column[EcuIdentifier]("ecu_serial")
    def deviceId = column[DeviceId]("device_id")
    def hardwareId = column[HardwareIdentifier]("hardware_identifier")
    def publicKey = column[TufKey]("public_key")
    def installedTarget = column[Option[EcuTargetId]]("current_target")
    def deleted = column[Boolean]("deleted", O.Default(false))

    def primKey = primaryKey("ecus_pk", (deviceId, ecuSerial))

    override def * = (ecuSerial, deviceId, namespace, hardwareId, publicKey, installedTarget) <> ((Ecu.apply _).tupled, Ecu.unapply)
  }

  protected [db] val allEcus = TableQuery[EcusTable]

  protected [db] val activeEcus = TableQuery[EcusTable].filter(_.deleted === false)

  protected [db] val deletedEcus = TableQuery[EcusTable].filter(_.deleted === true)

  class EcuTargetsTable(tag: Tag) extends Table[EcuTarget](tag, "ecu_targets") {
    def id = column[EcuTargetId]("id")
    def namespace = column[Namespace]("namespace")
    def filename = column[TargetFilename]("filename")
    def length = column[Long]("length")
    def checksum = column[Checksum]("checksum")
    def sha256 = column[SHA256Checksum]("sha256")
    def uri = column[Option[Uri]]("uri")

    def primKey = primaryKey("ecu_targets_pk", id)

    override def * = (namespace, id, filename, length, checksum, sha256, uri) <> ((EcuTarget.apply _).tupled, EcuTarget.unapply)
  }

  protected [db] val ecuTargets = TableQuery[EcuTargetsTable]

  class SignedRolesTable(tag: Tag) extends Table[DbSignedRole](tag, "signed_roles") {
    def role = column[RoleType]("role")
    def device  = column[DeviceId]("device_id")
    def version = column[Int]("version")
    def content = column[JsonSignedPayload]("content")
    def expires = column[Instant]("expires_at")
    def createdAt = column[Instant]("created_at")
    def checksum = column[Option[Checksum]]("checksum")
    def length = column[Option[Long]]("length")

    def primKey = primaryKey("signed_roles_pk", (role, version, device))

    override def * = (role, device, checksum, length, version, expires, content) <> ((DbSignedRole.apply _).tupled, DbSignedRole.unapply)
  }

  protected [db] val signedRoles = TableQuery[SignedRolesTable]

  class AssignmentsTable(tag: Tag) extends Table[Assignment](tag, "assignments") {
    def namespace = column[Namespace]("namespace")
    def deviceId = column[DeviceId]("device_id")
    def ecuId = column[EcuIdentifier]("ecu_serial")
    def ecuTargetId = column[EcuTargetId]("ecu_target_id")
    def correlationId = column[CorrelationId]("correlation_id")
    def createdAt = column[Instant]("created_at")
    def inFlight = column[Boolean]("in_flight")

    def * = (namespace, deviceId, ecuId, ecuTargetId, correlationId, inFlight) <> ((Assignment.apply _).tupled, Assignment.unapply)

    def pk = primaryKey("assignments_pk", (deviceId, ecuId))
  }

  protected [db] val assignments = TableQuery[AssignmentsTable]

  class ProcessedAssignmentsTable(tag: Tag) extends Table[ProcessedAssignment](tag, "processed_assignments") {
    def namespace = column[Namespace]("namespace")
    def deviceId = column[DeviceId]("device_id")
    def ecuId = column[EcuIdentifier]("ecu_serial")
    def ecuTargetId = column[EcuTargetId]("ecu_target_id")
    def correlationId = column[CorrelationId]("correlation_id")
    def canceled = column[Boolean]("canceled")
    def successful = column[Boolean]("successful")
    def resultDesc = column[Option[String]]("result_desc")
    def createdAt = column[Instant]("created_at")

    def * = (namespace, deviceId, ecuId, ecuTargetId, correlationId, successful, resultDesc, canceled) <> ((ProcessedAssignment.apply _).tupled, ProcessedAssignment.unapply)
  }

  protected [db] val processedAssignments = TableQuery[ProcessedAssignmentsTable]

  class HardwareUpdatesTable(tag: Tag) extends Table[HardwareUpdate](tag, "hardware_updates") {
    def namespace = column[Namespace]("namespace")
    def id = column[UpdateId]("id")
    def hardwareId = column[HardwareIdentifier]("hardware_identifier")
    def toTarget = column[EcuTargetId]("to_target_id")
    def fromTarget = column[Option[EcuTargetId]]("from_target_id")

    def * = (namespace, id, hardwareId, fromTarget, toTarget) <> ((HardwareUpdate.apply _).tupled, HardwareUpdate.unapply)

    def pk = primaryKey("mtu_pk", (id, hardwareId))
  }

  protected [db] val hardwareUpdates = TableQuery[HardwareUpdatesTable]

  class RepoNameTable(tag: Tag) extends Table[(RepoId, Namespace)](tag, "repo_namespaces") {
    def namespace = column[Namespace]("namespace", O.PrimaryKey)
    def repoId = column[RepoId]("repo_id")

    override def * = (repoId, namespace)
  }

  protected [db] val repoNamespaces = TableQuery[RepoNameTable]

  class AutoUpdateDefinitionTable(tag: Tag) extends Table[AutoUpdateDefinition](tag, "auto_update_definitions") {
    def namespace = column[Namespace]("namespace")
    def id = column[AutoUpdateDefinitionId]("id")
    def deviceId = column[DeviceId]("device_id")
    def ecuId = column[EcuIdentifier]("ecu_serial")
    def targetName = column[TargetName]("target_name")
    def deleted = column[Boolean]("deleted")

    override def * = (id, namespace, deviceId, ecuId, targetName) <> ((AutoUpdateDefinition.apply _).tupled, AutoUpdateDefinition.unapply)
  }

  protected [db] val autoUpdates = TableQuery[AutoUpdateDefinitionTable]

  class DeviceManifestsTable(tag: Tag) extends Table[(DeviceId, Json, SHA256Checksum, Instant)](tag, "device_manifests") {
    def deviceId = column[DeviceId]("device_id")
    def targetName = column[TargetName]("target_name")
    def receivedAt = column[Instant]("received_at")
    def sha256 = column[SHA256Checksum]("sha256")
    def manifest = column[Json]("manifest")

    def pk = primaryKey("device-manifests-pk", (deviceId, sha256))

    override def * = (deviceId, manifest, sha256, receivedAt)
  }

  protected [db] val deviceManifests = TableQuery[DeviceManifestsTable]
}
