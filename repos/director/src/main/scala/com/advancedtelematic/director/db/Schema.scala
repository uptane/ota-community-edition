package com.advancedtelematic.director.db

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.director.data.DataType.{
  AdminRoleName,
  ScheduledUpdate,
  ScheduledUpdateId
}
import com.advancedtelematic.director.data.DbDataType.*
import com.advancedtelematic.libats.data.DataType.{Checksum, CorrelationId, Namespace}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.jsonMapper
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  JsonSignedPayload,
  RepoId,
  TargetFilename,
  TargetName,
  TufKey
}
import io.circe.Json
import slick.jdbc.MySQLProfile.api.*
import SlickMapping.*

import java.time.Instant

//noinspection TypeAnnotation
object Schema {

  import SlickMapping.adminRoleNameMapper
  import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
  import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
  import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping
  import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
  import com.advancedtelematic.libats.slick.db.SlickUriMapper.*
  import com.advancedtelematic.libats.slick.db.SlickUrnMapper.*
  import com.advancedtelematic.libtuf_server.data.TufSlickMappings.*

  class ProvisionedDevicesTable(tag: Tag) extends Table[Device](tag, "provisioned_devices") {
    def namespace = column[Namespace]("namespace")
    def id = column[DeviceId]("id")
    def primaryEcu = column[EcuIdentifier]("primary_ecu_id")
    def generatedMetadataOutdated = column[Boolean]("generated_metadata_outdated")
    def deleted = column[Boolean]("deleted", O.Default(false))
    def createdAt = column[Instant]("created_at")(javaInstantMapping)

    def pk = primaryKey("devices_pk", id)

    override def * = (namespace, id, primaryEcu, generatedMetadataOutdated, deleted) <> (
      (Device.apply _).tupled,
      Device.unapply
    )

  }

  protected[db] val allProvisionedDevices = TableQuery[ProvisionedDevicesTable]

  protected[db] val activeProvisionedDevices =
    TableQuery[ProvisionedDevicesTable].filter(_.deleted === false)

  class EcusTable(tag: Tag) extends Table[Ecu](tag, "ecus") {
    def namespace = column[Namespace]("namespace")
    def ecuSerial = column[EcuIdentifier]("ecu_serial")
    def deviceId = column[DeviceId]("device_id")
    def hardwareId = column[HardwareIdentifier]("hardware_identifier")
    def publicKey = column[TufKey]("public_key")
    def installedTarget = column[Option[EcuTargetId]]("current_target")
    def deleted = column[Boolean]("deleted", O.Default(false))

    def primKey = primaryKey("ecus_pk", (deviceId, ecuSerial))

    override def * = (ecuSerial, deviceId, namespace, hardwareId, publicKey, installedTarget) <> (
      (Ecu.apply _).tupled,
      Ecu.unapply
    )

  }

  protected[db] val allEcus = TableQuery[EcusTable]

  protected[db] val activeEcus = TableQuery[EcusTable].filter(_.deleted === false)

  protected[db] val deletedEcus = TableQuery[EcusTable].filter(_.deleted === true)

  class EcuTargetsTable(tag: Tag) extends Table[EcuTarget](tag, "ecu_targets") {
    def id = column[EcuTargetId]("id")
    def namespace = column[Namespace]("namespace")
    def filename = column[TargetFilename]("filename")
    def length = column[Long]("length")
    def checksum = column[Checksum]("checksum")
    def sha256 = column[SHA256Checksum]("sha256")
    def uri = column[Option[Uri]]("uri")
    def userDefinedCustom = column[Option[Json]]("user_custom")

    def primKey = primaryKey("ecu_targets_pk", id)

    override def * =
      (namespace, id, filename, length, checksum, sha256, uri, userDefinedCustom) <> (
        (EcuTarget.apply _).tupled,
        EcuTarget.unapply
      )

  }

  protected[db] val ecuTargets = TableQuery[EcuTargetsTable]

  class AdminRolesTable(tag: Tag) extends Table[DbAdminRole](tag, "admin_roles") {
    def repoId = column[RepoId]("repo_id")
    def role = column[RoleType]("role")
    def name = column[AdminRoleName]("name")
    def version = column[Int]("version")
    def content = column[JsonSignedPayload]("content")
    def expires = column[Instant]("expires_at")(javaInstantMapping)
    def createdAt = column[Instant]("created_at")(javaInstantMapping)
    def checksum = column[Checksum]("checksum")
    def length = column[Long]("length")
    def deleted = column[Boolean]("deleted")

    def primKey = primaryKey("offline_targets_pk", (repoId, name, version))

    override def * = (repoId, role, name, checksum, length, version, expires, content) <> (
      (DbAdminRole.apply _).tupled,
      DbAdminRole.unapply
    )

  }

  protected[db] val adminRoles = TableQuery[AdminRolesTable]
  protected[db] val notDeletedAdminRoles = adminRoles.filter(_.deleted === false)

  class DeviceRolesTable(tag: Tag) extends Table[DbDeviceRole](tag, "device_roles") {
    def role = column[RoleType]("role")
    def device = column[DeviceId]("device_id")
    def version = column[Int]("version")
    def content = column[JsonSignedPayload]("content")
    def expires = column[Instant]("expires_at")(javaInstantMapping)
    def createdAt = column[Instant]("created_at")(javaInstantMapping)
    def checksum = column[Option[Checksum]]("checksum")
    def length = column[Option[Long]]("length")

    def primKey = primaryKey("device_roles_pk", (role, version, device))

    override def * = (role, device, checksum, length, version, expires, content) <> (
      (DbDeviceRole.apply _).tupled,
      DbDeviceRole.unapply
    )

  }

  protected[db] val deviceRoles = TableQuery[DeviceRolesTable]

  class AssignmentsTable(tag: Tag) extends Table[Assignment](tag, "assignments") {
    def namespace = column[Namespace]("namespace")
    def deviceId = column[DeviceId]("device_id")
    def ecuId = column[EcuIdentifier]("ecu_serial")
    def ecuTargetId = column[EcuTargetId]("ecu_target_id")
    def correlationId = column[CorrelationId]("correlation_id")
    def createdAt = column[Instant]("created_at")(javaInstantMapping)
    def inFlight = column[Boolean]("in_flight")

    def * = (namespace, deviceId, ecuId, ecuTargetId, correlationId, inFlight, createdAt) <> (
      (Assignment.apply _).tupled,
      Assignment.unapply
    )

    def pk = primaryKey("assignments_pk", (deviceId, ecuId))
  }

  protected[db] val assignments = TableQuery[AssignmentsTable]

  class ProcessedAssignmentsTable(tag: Tag)
      extends Table[ProcessedAssignment](tag, "processed_assignments") {
    def namespace = column[Namespace]("namespace")
    def deviceId = column[DeviceId]("device_id")
    def ecuId = column[EcuIdentifier]("ecu_serial")
    def ecuTargetId = column[EcuTargetId]("ecu_target_id")
    def correlationId = column[CorrelationId]("correlation_id")
    def canceled = column[Boolean]("canceled")
    def successful = column[Boolean]("successful")
    def resultDesc = column[Option[String]]("result_desc")
    def createdAt = column[Instant]("created_at")(javaInstantMapping)

    def * = (
      namespace,
      deviceId,
      ecuId,
      ecuTargetId,
      correlationId,
      successful,
      resultDesc,
      canceled
    ) <> ((ProcessedAssignment.apply _).tupled, ProcessedAssignment.unapply)

  }

  protected[db] val processedAssignments = TableQuery[ProcessedAssignmentsTable]

  class HardwareUpdatesTable(tag: Tag) extends Table[HardwareUpdate](tag, "hardware_updates") {
    def namespace = column[Namespace]("namespace")
    def id = column[UpdateId]("id")
    def hardwareId = column[HardwareIdentifier]("hardware_identifier")
    def toTarget = column[EcuTargetId]("to_target_id")
    def fromTarget = column[Option[EcuTargetId]]("from_target_id")

    def * = (namespace, id, hardwareId, fromTarget, toTarget) <> (
      (HardwareUpdate.apply _).tupled,
      HardwareUpdate.unapply
    )

    def pk = primaryKey("mtu_pk", (id, hardwareId))
  }

  protected[db] val hardwareUpdates = TableQuery[HardwareUpdatesTable]

  class RepoNameTable(tag: Tag) extends Table[(RepoId, Namespace)](tag, "repo_namespaces") {
    def namespace = column[Namespace]("namespace", O.PrimaryKey)
    def repoId = column[RepoId]("repo_id")

    override def * = (repoId, namespace)
  }

  protected[db] val repoNamespaces = TableQuery[RepoNameTable]

  class AutoUpdateDefinitionTable(tag: Tag)
      extends Table[AutoUpdateDefinition](tag, "auto_update_definitions") {
    def namespace = column[Namespace]("namespace")
    def id = column[AutoUpdateDefinitionId]("id")
    def deviceId = column[DeviceId]("device_id")
    def ecuId = column[EcuIdentifier]("ecu_serial")
    def targetName = column[TargetName]("target_name")
    def deleted = column[Boolean]("deleted")

    override def * = (id, namespace, deviceId, ecuId, targetName) <> (
      (AutoUpdateDefinition.apply _).tupled,
      AutoUpdateDefinition.unapply
    )

  }

  protected[db] val autoUpdates = TableQuery[AutoUpdateDefinitionTable]

  class DeviceManifestsTable(tag: Tag)
      extends Table[(DeviceId, Json, SHA256Checksum, Instant)](tag, "device_manifests") {
    def deviceId = column[DeviceId]("device_id")
    def targetName = column[TargetName]("target_name")
    def receivedAt = column[Instant]("received_at")(javaInstantMapping)
    def sha256 = column[SHA256Checksum]("sha256")
    def manifest = column[Json]("manifest")

    def pk = primaryKey("device-manifests-pk", (deviceId, sha256))

    override def * = (deviceId, manifest, sha256, receivedAt)
  }

  protected[db] val deviceManifests = TableQuery[DeviceManifestsTable]

  class ScheduledUpdatesTable(tag: Tag) extends Table[ScheduledUpdate](tag, "scheduled_updates") {
    def namespace = column[Namespace]("namespace")
    def id = column[ScheduledUpdateId]("id")
    def deviceId = column[DeviceId]("device_id")
    def updateId = column[UpdateId]("hardware_update_id")
    def scheduledAt = column[Instant]("scheduled_at")(javaInstantMapping)
    def status = column[ScheduledUpdate.Status]("status")
    def statusInfo = column[Option[Json]]("status_info")

    def createdAt = column[Instant]("created_at")(javaInstantMapping)
    def updatedAt = column[Instant]("updated_at")(javaInstantMapping)

    def pk = primaryKey("scheduled-updates-pk", id)

    override def * = (namespace, id, deviceId, updateId, scheduledAt, status) <> (
      (ScheduledUpdate.apply _).tupled,
      ScheduledUpdate.unapply
    )

  }

  protected[db] val scheduledUpdates = TableQuery[ScheduledUpdatesTable]
}
