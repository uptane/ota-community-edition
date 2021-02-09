package com.advancedtelematic.director.data

import java.security.PublicKey
import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri
import cats.implicits._
import com.advancedtelematic.director.data.DbDataType.Ecu
import com.advancedtelematic.director.data.UptaneDataType.{Hashes, TargetImage}
import com.advancedtelematic.libats.data.DataType.{Checksum, CorrelationId, HashMethod, Namespace, ValidChecksum}
import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.libats.data.{EcuIdentifier, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.advancedtelematic.libats.messaging_datatype.Messages.EcuAndHardwareId
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientHashes, TufRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, JsonSignedPayload, KeyType, SignedPayload, TargetFilename, TargetName, TufKey}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import eu.timepit.refined.api.Refined
import io.circe.Json
import io.circe.syntax._

object DbDataType {
  case class AutoUpdateDefinitionId(uuid: UUID) extends UUIDKey
  object AutoUpdateDefinitionId extends UUIDKeyObj[AutoUpdateDefinitionId]

  final case class AutoUpdateDefinition(id: AutoUpdateDefinitionId, namespace: Namespace, deviceId: DeviceId, ecuId: EcuIdentifier, targetName: TargetName)

  final case class DeviceKnownState(deviceId: DeviceId,
                                    primaryEcu: EcuIdentifier,
                                    ecuStatus: Map[EcuIdentifier, Option[EcuTargetId]],
                                    ecuTargets: Map[EcuTargetId, EcuTarget],
                                    currentAssignments: Set[Assignment],
                                    processedAssignments: Set[ProcessedAssignment],
                                    generatedMetadataOutdated: Boolean)

  final case class Device(ns: Namespace, id: DeviceId, primaryEcuId: EcuIdentifier,
                          generatedMetadataOutdated: Boolean, deleted: Boolean)

  final case class Ecu(ecuSerial: EcuIdentifier, deviceId: DeviceId, namespace: Namespace,
                       hardwareId: HardwareIdentifier, publicKey: TufKey, installedTarget: Option[EcuTargetId]) {
    def asEcuAndHardwareId: EcuAndHardwareId = EcuAndHardwareId(ecuSerial, hardwareId.value)
  }

  final case class DbSignedRole(role: RoleType, device: DeviceId, checksum: Option[Checksum], length: Option[Long], version: Int, expires: Instant, content: JsonSignedPayload)

  implicit class DbDSignedRoleToSignedPayload(value: DbSignedRole) {
    def toSignedRole[T : TufRole]: SignedRole[T] = {
      val (checksum, length) = value.checksum.product(value.length).getOrElse {
        val canonicalJson = value.content.asJson.canonical
        val checksum = Sha256Digest.digest(canonicalJson.getBytes)
        val length = canonicalJson.length
        checksum -> length.toLong
      }

      SignedRole[T](value.content, checksum, length, value.version, value.expires)
    }
  }

  implicit class SignedPayloadToDbSignedRole[_](value: SignedRole[_]) {
    def toDbSignedRole(deviceId: DeviceId): DbSignedRole =
      DbDataType.DbSignedRole(value.tufRole.roleType, deviceId, value.checksum.some, value.length.some, value.version, value.expiresAt, value.content)
  }

  final case class HardwareUpdate(namespace: Namespace,
                                  id: UpdateId,
                                  hardwareId: HardwareIdentifier,
                                  fromTarget: Option[EcuTargetId],
                                  toTarget: EcuTargetId)

  case class EcuTargetId(uuid: UUID) extends UUIDKey
  object EcuTargetId extends UUIDKeyObj[EcuTargetId]

  case class EcuTarget(ns: Namespace, id: EcuTargetId, filename: TargetFilename, length: Long,
                       checksum: Checksum,
                       sha256: SHA256Checksum,
                       uri: Option[Uri]) {
    def matches(other: EcuTarget): Boolean = {
      filename == other.filename &&
        length == other.length &&
        sha256 == other.sha256
    }
  }

  case class Assignment(ns: Namespace, deviceId: DeviceId, ecuId: EcuIdentifier, ecuTargetId: EcuTargetId,
                        correlationId: CorrelationId, inFlight: Boolean) {

    def toProcessedAssignment(successful: Boolean, canceled: Boolean = false, result: Option[String] = None): ProcessedAssignment =
      ProcessedAssignment(ns, deviceId, ecuId, ecuTargetId, correlationId, successful, result, canceled)
  }

  case class ProcessedAssignment(ns: Namespace, deviceId: DeviceId, ecuId: EcuIdentifier, ecuTargetId: EcuTargetId,
                                 correlationId: CorrelationId, successful: Boolean, result: Option[String], canceled: Boolean)

  type SHA256Checksum = Refined[String, ValidChecksum]
}

object AdminDataType {
  final case class EcuInfoImage(filepath: TargetFilename, size: Long, hash: Hashes)
  final case class EcuInfoResponse(id: EcuIdentifier, hardwareId: HardwareIdentifier, primary: Boolean, image: EcuInfoImage)

  final case class TargetUpdateRequest(from: Option[TargetUpdate], to: TargetUpdate)

  final case class TargetUpdate(target: TargetFilename, checksum: Checksum, targetLength: Long, uri: Option[Uri])

  final case class MultiTargetUpdate(targets: Map[HardwareIdentifier, TargetUpdateRequest])

  final case class RegisterEcu(ecu_serial: EcuIdentifier, hardware_identifier: HardwareIdentifier, clientKey: TufKey) {
    def keyType: KeyType = clientKey.keytype
    def publicKey: PublicKey = clientKey.keyval

    def toEcu(ns: Namespace, deviceId: DeviceId): Ecu = Ecu(ecu_serial, deviceId, ns, hardware_identifier, clientKey, installedTarget = None)
  }

  final case class RegisterDevice(deviceId: Option[DeviceId], primary_ecu_serial: EcuIdentifier, ecus: Seq[RegisterEcu])

  final case class AssignUpdateRequest(correlationId: CorrelationId,
                                       devices: Seq[DeviceId],
                                       mtuId: UpdateId,
                                       dryRun: Option[Boolean] = None)

  final case class QueueResponse(correlationId: CorrelationId, targets: Map[EcuIdentifier, TargetImage], inFlight: Boolean)

  final case class FindImageCount(filepaths: Seq[TargetFilename])
}

object UptaneDataType {
  final case class Hashes(sha256: Refined[String, ValidChecksum]) {
    def toClientHashes: ClientHashes = Map(HashMethod.SHA256 -> sha256)
  }

  final case class FileInfo(hashes: Hashes, length: Long)
  final case class Image(filepath: TargetFilename, fileinfo: FileInfo)
  final case class TargetImage(image: Image, uri: Option[Uri])

  object Hashes {
    def apply(checksum: Checksum): Hashes = {
      require(checksum.method == HashMethod.SHA256)
      Hashes(checksum.hash)
    }
  }
}

// Move to libats-messaging if some service needs these messages
object Messages {
  import DeviceId._
  import cats.syntax.show._
  import com.advancedtelematic.libtuf.data.TufCodecs._
  import com.advancedtelematic.libats.codecs.CirceCodecs._
  import com.advancedtelematic.libtuf.data.TufCodecs._

  case class DeviceManifestReported(namespace: Namespace, deviceId: DeviceId, manifest: SignedPayload[Json], receivedAt: Instant)

  implicit val deviceManifestReportedCodecs = io.circe.generic.semiauto.deriveCodec[DeviceManifestReported]

  implicit val deviceManifestReportedMsgLike = MessageLike[DeviceManifestReported](_.deviceId.show)
}

object DataType {
  final case class TargetItemCustomEcuData(hardwareId: HardwareIdentifier)

  final case class TargetItemCustom(uri: Option[Uri],
                                    ecuIdentifiers: Map[EcuIdentifier, TargetItemCustomEcuData])

  final case class DeviceUpdateTarget(device: DeviceId, correlationId: Option[CorrelationId], updateId: Option[UpdateId], targetVersion: Int, inFlight: Boolean)

  final case class DeviceTargetsCustom(correlationId: Option[CorrelationId])
}

object ClientDataType {
  final case class Device(id: DeviceId, primaryEcu: EcuIdentifier, createdAt: Instant)

  implicit class DeviceOps(value: DbDataType.Device) {
    def toClient(createdAt: Instant): Device = Device(value.id, value.primaryEcuId, createdAt)
  }

  implicit class DevicePaginationOps(value: PaginationResult[(Instant, DbDataType.Device)]) {
    def toClient: PaginationResult[Device] = value.map { case (createdAt, device) => device.toClient(createdAt) }
  }
}
