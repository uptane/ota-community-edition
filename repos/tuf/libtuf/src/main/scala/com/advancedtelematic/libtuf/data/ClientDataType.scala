package com.advancedtelematic.libtuf.data

import java.net.URI
import java.time.Instant

import cats.syntax.show._
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, KeyId, RoleType, TargetFilename, TargetName, TargetVersion, TufKey}
import com.advancedtelematic.libtuf.data.ValidatedString.{ValidatedString, ValidatedStringValidation}
import eu.timepit.refined.api.{Refined, Validate}
import io.circe.{Decoder, Json}


object ClientDataType {
  type ClientHashes = Map[HashMethod, Refined[String, ValidChecksum]]

  case class TargetCustom(name: TargetName, version: TargetVersion, hardwareIds: Seq[HardwareIdentifier],
                          targetFormat: Option[TargetFormat],
                          uri: Option[URI] = None,
                          cliUploaded: Option[Boolean] = None,
                          createdAt: Instant = Instant.now,
                          updatedAt: Instant = Instant.now,
                          proprietary: Json = Json.obj())

  case class ClientTargetItem(hashes: ClientHashes,
                              length: Long, custom: Option[Json]) {
    def customParsed[T : Decoder]: Option[T] = custom.flatMap(_.as[T].toOption)
  }

  case class RoleKeys(keyids: Seq[KeyId], threshold: Int)

  case class ValidMetaPath()
  type MetaPath = Refined[String, ValidMetaPath]

  implicit val validMetaPath: Validate.Plain[String, ValidMetaPath] =
    Validate.fromPredicate(
      _.endsWith(".json"),
      str => s"$str is not a valid meta path, it needs to end in .json",
      ValidMetaPath())

  case class MetaItem(hashes: ClientHashes, length: Long, version: Int)

  implicit class TufRoleOps[T](value: T)(implicit tufRole: TufRole[T]) {
    def metaPath: MetaPath = tufRole.metaPath

    def version: Int = tufRole.version(value)

    def expires: Instant = tufRole.expires(value)
  }

  implicit class RoleTypeOps(value: RoleType) {
    def metaPath: MetaPath = (value.show + ".json").refineTry[ValidMetaPath].get
  }

  trait TufRole[T] {
    def roleType: RoleType

    def typeStr: String = roleType.show.capitalize

    def metaPath: MetaPath = roleType.metaPath

    def checksumPath: String = metaPath.value + ".checksum"

    def version(v: T): Int

    def expires(v: T): Instant

    def refreshRole(v: T, versionBump: Int => Int, expiresAt: Instant): T
  }

  sealed trait VersionedRole {
    val version: Int
    val expires: Instant
    val _type: String
  }

  object TufRole {
    private def apply[T <: VersionedRole](r: RoleType)
                                         (updateFn: (T, Int, Instant) => T): TufRole[T] =
      new TufRole[T] {
        override def roleType: RoleType = r

        override def refreshRole(v: T, version: Int => Int, expiresAt: Instant): T =
          updateFn(v, version(v.version), expiresAt)

        override def version(v: T): Int = v.version

        override def expires(v: T): Instant = v.expires
      }

    implicit val targetsTufRole = apply[TargetsRole](RoleType.TARGETS)((r, v, e) => r.copy(version = v, expires = e))
    implicit val snapshotTufRole = apply[SnapshotRole](RoleType.SNAPSHOT)((r, v, e) => r.copy(version = v, expires = e))
    implicit val timestampTufRole = apply[TimestampRole](RoleType.TIMESTAMP)((r, v, e) => r.copy(version = v, expires = e))
    implicit val rootTufRole = apply[RootRole](RoleType.ROOT)((r, v, e) => r.copy(version = v, expires = e))
  }

  case class RootRole(keys: Map[KeyId, TufKey],
                      roles: Map[RoleType, RoleKeys],
                      version: Int,
                      expires: Instant,
                      consistent_snapshot: Boolean = false) extends VersionedRole {
    override val _type: String = "Root"
  }

  final class DelegatedPathPattern private (val value: String) extends ValidatedString

  object DelegatedPathPattern {
    implicit val delegatedPathPatternValidation = ValidatedStringValidation(new DelegatedPathPattern(_)) { v: String =>
      cats.data.Validated.condNel(
        v.nonEmpty && v.length < 254 && !v.contains(".."),
        new DelegatedPathPattern(v),
        "DelegatedPathPattern cannot be empty or bigger than 254 chars or contain `..`"
      )
    }
  }

  final class DelegatedRoleName private (val value: String) extends ValidatedString

  object DelegatedRoleName {
    implicit val delegatedRoleNameValidation = ValidatedStringValidation(new DelegatedRoleName(_)) { v: String =>
      cats.data.Validated.condNel(
        v.nonEmpty || v.length > 50,
        new DelegatedRoleName(v),
        "delegated role name cannot be empty or bigger than 50 characters"
      )
    }
  }

  case class Delegation(name: DelegatedRoleName, keyids: List[KeyId], paths: List[DelegatedPathPattern],
                        threshold: Int = 1,
                        terminating: Boolean = true)

  case class Delegations(keys: Map[KeyId, TufKey], roles: List[Delegation])

  case class TargetsRole(expires: Instant,
                         targets: Map[TargetFilename, ClientTargetItem],
                         version: Int,
                         delegations: Option[Delegations] = None,
                         custom: Option[Json] = None) extends VersionedRole {
    override val _type: String = "Targets"
  }

  case class SnapshotRole(meta: Map[MetaPath, MetaItem],
                          expires: Instant,
                          version: Int) extends VersionedRole {
    override val _type: String = "Snapshot"
  }

  case class TimestampRole(meta: Map[MetaPath, MetaItem],
                           expires: Instant,
                           version: Int) extends VersionedRole {
    override val _type: String = "Timestamp"
  }
}
