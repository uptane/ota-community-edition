package com.advancedtelematic.libtuf.data

import java.net.URI
import java.time.Instant
import cats.syntax.show.*
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libtuf.data.ClientDataType.ClientPackage.TargetOrigin
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  KeyId,
  RoleType,
  TargetFilename,
  TargetName,
  TargetVersion,
  TufKey,
  ValidTargetFilename
}
import com.advancedtelematic.libtuf.data.ValidatedString.{
  ValidatedString,
  ValidatedStringValidation
}
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.predicates.all.NonEmpty
import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.*
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import enumeratum.*
import com.advancedtelematic.libats.codecs.CirceRefined.*
import com.advancedtelematic.libats.http.HttpCodecs.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import enumeratum.EnumEntry.{Camelcase, Hyphencase}
import io.circe.Codec
import io.circe.generic.semiauto.*

object ClientDataType {
  type TargetHash = Refined[String, ValidChecksum]
  type ClientHashes = Map[HashMethod, TargetHash]

  case class TargetCustom(name: TargetName,
                          version: TargetVersion,
                          hardwareIds: Seq[HardwareIdentifier],
                          targetFormat: Option[TargetFormat],
                          uri: Option[URI] = None,
                          cliUploaded: Option[Boolean] = None,
                          createdAt: Instant = Instant.now,
                          updatedAt: Instant = Instant.now,
                          proprietary: Json = Json.obj())

  case class ClientTargetItem(hashes: ClientHashes, length: Long, custom: Option[Json]) {
    def customParsed[T: Decoder]: Option[T] = custom.flatMap(_.as[T].toOption)
  }

  // is it dumb to have the targetFilename field since this can be formed from clientTargetItem.custom?
  case class DelegationClientTargetItem(targetFilename: TargetFilename,
                                        delegatedRoleName: DelegatedRoleName,
                                        clientTargetItem: ClientTargetItem)

  case class RoleKeys(keyids: Seq[KeyId], threshold: Int)

  case class ValidMetaPath()
  type MetaPath = Refined[String, ValidMetaPath]

  implicit val validMetaPath: Validate.Plain[String, ValidMetaPath] =
    Validate.fromPredicate(
      _.endsWith(".json"),
      str => s"$str is not a valid meta path, it needs to end in .json",
      ValidMetaPath()
    )

  case class MetaItem(hashes: ClientHashes, length: Long, version: Int)

  implicit class TufRoleOps[T](value: T)(implicit tufRole: TufRole[T]) {
    def metaPath: MetaPath = tufRole.metaPath

    def version: Int = tufRole.version(value)

    def expires: Instant = tufRole.expires(value)

    def roleType: RoleType = tufRole.roleType
  }

  implicit class RoleTypeOps(value: RoleType) {
    def metaPath: MetaPath = (value.show + ".json").refineTry[ValidMetaPath].get
  }

  trait TufRole[T] {
    def roleType: RoleType

    def decoderDiscriminator: String = roleType.show.split("-").map(_.capitalize).mkString("-")

    def metaPath: MetaPath = roleType.metaPath

    def checksumPath: String = metaPath.value + ".checksum"

    def version(v: T): Int

    def expires(v: T): Instant

    def refreshRole(v: T, versionBump: Int => Int, expiresAt: Instant): T
  }

  sealed trait VersionedRole {
    val version: Int
    val expires: Instant
  }

  object TufRole {

    private def apply[T <: VersionedRole](r: RoleType)(
      updateFn: (T, Int, Instant) => T): TufRole[T] =
      new TufRole[T] {
        override def roleType: RoleType = r

        override def refreshRole(v: T, version: Int => Int, expiresAt: Instant): T =
          updateFn(v, version(v.version), expiresAt)

        override def version(v: T): Int = v.version

        override def expires(v: T): Instant = v.expires
      }

    implicit val targetsTufRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.TargetsRole
    ] = apply[TargetsRole](RoleType.TARGETS)((r, v, e) => r.copy(version = v, expires = e))

    implicit val snapshotTufRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.SnapshotRole
    ] = apply[SnapshotRole](RoleType.SNAPSHOT)((r, v, e) => r.copy(version = v, expires = e))

    implicit val timestampTufRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.TimestampRole
    ] = apply[TimestampRole](RoleType.TIMESTAMP)((r, v, e) => r.copy(version = v, expires = e))

    implicit val rootTufRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.RootRole
    ] = apply[RootRole](RoleType.ROOT)((r, v, e) => r.copy(version = v, expires = e))

    implicit val offlineUpdatesRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.OfflineUpdatesRole
    ] = apply[OfflineUpdatesRole](RoleType.OFFLINE_UPDATES)((r, v, e) =>
      r.copy(version = v, expires = e)
    )

    implicit val offlineSnapshotRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.OfflineSnapshotRole
    ] = apply[OfflineSnapshotRole](RoleType.OFFLINE_SNAPSHOT)((r, v, e) =>
      r.copy(version = v, expires = e)
    )

    implicit val remoteSessionsRole: com.advancedtelematic.libtuf.data.ClientDataType.TufRole[
      com.advancedtelematic.libtuf.data.ClientDataType.RemoteSessionsRole
    ] = apply[RemoteSessionsRole](RoleType.REMOTE_SESSIONS)((r, v, e) =>
      r.copy(version = v, expires = e)
    )

  }

  case class RootRole(keys: Map[KeyId, TufKey],
                      roles: Map[RoleType, RoleKeys],
                      version: Int,
                      expires: Instant,
                      consistent_snapshot: Boolean = false)
      extends VersionedRole

  case class TargetsRole(expires: Instant,
                         targets: Map[TargetFilename, ClientTargetItem],
                         version: Int,
                         delegations: Option[Delegations] = None,
                         custom: Option[Json] = None)
      extends VersionedRole

  case class SnapshotRole(meta: Map[MetaPath, MetaItem], expires: Instant, version: Int)
      extends VersionedRole

  case class TimestampRole(meta: Map[MetaPath, MetaItem], expires: Instant, version: Int)
      extends VersionedRole

  case class OfflineUpdatesRole(targets: Map[TargetFilename, ClientTargetItem],
                                expires: Instant,
                                version: Int)
      extends VersionedRole

  case class OfflineSnapshotRole(meta: Map[MetaPath, MetaItem], expires: Instant, version: Int)
      extends VersionedRole

  case class RemoteSessionsRole(remote_sessions: RemoteSessionsPayload,
                                remote_commands: Option[RemoteCommandsPayload],
                                expires: Instant,
                                version: Int)
      extends VersionedRole

  final class DelegatedPathPattern private (val value: String) extends ValidatedString

  object DelegatedPathPattern {

    implicit val delegatedPathPatternValidation
      : com.advancedtelematic.libtuf.data.ValidatedString.ValidatedStringValidation[
        com.advancedtelematic.libtuf.data.ClientDataType.DelegatedPathPattern
      ] = ValidatedStringValidation(new DelegatedPathPattern(_)) { (v: String) =>
      cats.data.Validated.condNel(
        v.nonEmpty && v.length < 254 && !v.contains(".."),
        new DelegatedPathPattern(v),
        "DelegatedPathPattern cannot be empty or bigger than 254 chars or contain `..`"
      )
    }

    // TODO: Should be in cvodecs
    implicit val delegatedPatternCodec: io.circe.Codec.AsObject[
      com.advancedtelematic.libtuf.data.ClientDataType.DelegatedPathPattern
    ] = deriveCodec[DelegatedPathPattern]

  }

  final class DelegatedRoleName private (val value: String) extends ValidatedString

  object DelegatedRoleName {

    implicit val delegatedRoleNameValidation
      : com.advancedtelematic.libtuf.data.ValidatedString.ValidatedStringValidation[
        com.advancedtelematic.libtuf.data.ClientDataType.DelegatedRoleName
      ] = ValidatedStringValidation(new DelegatedRoleName(_)) { (v: String) =>
      cats.data.Validated.condNel(
        v.nonEmpty && v.length < 51 && v.matches("[a-zA-Z0-9_-][a-zA-Z0-9_.-]*"),
        new DelegatedRoleName(v),
        "delegated role name cannot be empty, bigger than 50 characters, or contain any special characters other than `_, -, .`"
      )
    }

  }

  final class DelegationFriendlyName private (val value: String) extends ValidatedString

  object DelegationFriendlyName {

    implicit val delegationFriendlyNameValidation
      : com.advancedtelematic.libtuf.data.ValidatedString.ValidatedStringValidation[
        com.advancedtelematic.libtuf.data.ClientDataType.DelegationFriendlyName
      ] = ValidatedStringValidation(new DelegationFriendlyName(_)) { (v: String) =>
      cats.data.Validated.condNel(
        v.nonEmpty && v.length < 81,
        new DelegationFriendlyName(v),
        "delegation friendly name name cannot be empty or longer than 80 characters"
      )
    }

  }

  case class DelegationInfo(lastFetched: Option[Instant],
                            remoteUri: Option[Uri],
                            friendlyName: Option[DelegationFriendlyName] = None,
                            expires: Option[Instant] = None)

  object DelegationInfo {

    implicit val delegationInfoCodec: io.circe.Codec[DelegationInfo] =
      io.circe.generic.semiauto.deriveCodec[DelegationInfo]

  }

  case class Delegation(name: DelegatedRoleName,
                        keyids: List[KeyId],
                        paths: List[DelegatedPathPattern],
                        threshold: Int = 1,
                        terminating: Boolean = true)

  case class Delegations(keys: Map[KeyId, TufKey], roles: List[Delegation])

  case class SshSessionProperties(properties_version: String,
                                  authorized_keys: Map[String, PubKeyInfo],
                                  ra_server_hosts: Vector[String],
                                  ra_server_ssh_pubkeys: Vector[String])

  case class RemoteSessionsPayload(ssh: SshSessionProperties, version: String)

  object RemoteSessionsPayload {

    def empty: RemoteSessionsPayload =
      RemoteSessionsPayload(
        SshSessionProperties(
          properties_version = "v1alpha",
          authorized_keys = Map.empty,
          ra_server_hosts = Vector.empty,
          ra_server_ssh_pubkeys = Vector.empty
        ),
        version = "v1alpha"
      )

  }

  sealed abstract class CommandName extends EnumEntry with Hyphencase

  object CommandName extends Enum[CommandName] {

    val values: IndexedSeq[CommandName] = findValues

    case object Reboot extends CommandName
    case object RestartService extends CommandName
    case object Echo extends CommandName
  }

  case class CommandParameters(args: List[CommandArg])

  type CommandArg = Refined[String, NonEmpty]

  case class RemoteCommandsPayload(allowed_commands: Map[CommandName, CommandParameters],
                                   version: String)

  case class PubKeyInfo(pubkey: String, meta: Option[PubKeyMeta])

  case class PubKeyMeta(name: Option[String])

  trait EnumeratumUnmarshaller[T <: EnumEntry] { this: Enum[T] =>

    implicit val unmarshaller: Unmarshaller[String, T] = Unmarshaller.strict { str =>
      this.withNameInsensitive(str)
    }

  }

  sealed abstract class SortDirection extends EnumEntry

  object SortDirection extends Enum[SortDirection] with EnumeratumUnmarshaller[SortDirection] {

    import scala.annotation.unused

    val values = findValues

    @unused
    case object Asc extends SortDirection

    case object Desc extends SortDirection
  }

  sealed abstract class TargetItemsSort(val column: String) extends EnumEntry

  object TargetItemsSort
      extends Enum[TargetItemsSort]
      with EnumeratumUnmarshaller[TargetItemsSort] {

    val values = findValues

    case object Filename extends TargetItemsSort("filename")

    case object CreatedAt extends TargetItemsSort("created_at")
  }

  sealed abstract class AggregatedTargetItemsSort(val column: String) extends EnumEntry

  object AggregatedTargetItemsSort
      extends Enum[AggregatedTargetItemsSort]
      with EnumeratumUnmarshaller[AggregatedTargetItemsSort] {
    val values = findValues
    case object Name extends AggregatedTargetItemsSort("name")

    case object LastVersionAt extends AggregatedTargetItemsSort("last_version_at")
  }

  object ClientPackage {

    type ValidTargetOrigin = NonEmpty
    type TargetOrigin = Refined[String, ValidTargetOrigin]

  }

  case class ClientPackage(name: TargetName,
                           version: TargetVersion,
                           filename: TargetFilename,
                           origin: TargetOrigin,
                           length: Long,
                           hashes: ClientHashes,
                           uri: Option[Uri],
                           hardwareIds: List[HardwareIdentifier],
                           createdAt: Instant,
                           customData: Option[Json])

  case class ClientAggregatedPackage(name: TargetName,
                                     versions: Seq[TargetVersion],
                                     hardwareIds: Seq[HardwareIdentifier],
                                     origins: Seq[TargetOrigin],
                                     lastVersionAt: Instant)

}

case class PackageSearchParameters(origin: Seq[String],
                                   originNot: Option[String],
                                   nameContains: Option[String],
                                   name: Option[String],
                                   version: Option[String],
                                   hardwareIds: Seq[HardwareIdentifier],
                                   hashes: Seq[Refined[String, ValidChecksum]],
                                   filenames: Seq[Refined[String, ValidTargetFilename]])

object PackageSearchParameters {

  def empty: PackageSearchParameters = PackageSearchParameters(
    origin = Seq.empty,
    originNot = None,
    nameContains = None,
    name = None,
    version = None,
    hardwareIds = Seq.empty,
    hashes = Seq.empty,
    filenames = Seq.empty
  )

}
