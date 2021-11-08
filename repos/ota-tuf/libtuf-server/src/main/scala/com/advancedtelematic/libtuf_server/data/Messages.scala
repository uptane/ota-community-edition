package com.advancedtelematic.libtuf_server.data

import java.time.Instant

import com.advancedtelematic.libtuf.data.ClientDataType.TargetCustom
import com.advancedtelematic.libtuf.data.TufDataType.{OperationResult, TargetFilename}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libats.messaging_datatype.MessageLike

object Messages {
  final case class TufTargetAdded(
    namespace: Namespace,
    filename: TargetFilename,
    checksum: Checksum,
    length: Long,
    custom: Option[TargetCustom])

  implicit val tufTargetAddedEncoder: Encoder[TufTargetAdded] = deriveEncoder
  implicit val tufTargetAddedDecoder: Decoder[TufTargetAdded] = deriveDecoder

  implicit val tufTargetAddedMessageLike = MessageLike[TufTargetAdded](_.namespace.get)

  final case class PackageStorageUsage(namespace: String, timestamp: Instant, byteCount: Long)

  import com.advancedtelematic.libats.codecs.CirceCodecs.{dateTimeDecoder, dateTimeEncoder}

  implicit val packageStorageUsageEncoder: Encoder[PackageStorageUsage] = deriveEncoder
  implicit val packageStorageUsageDecoder: Decoder[PackageStorageUsage] = deriveDecoder

  implicit val packageStorageUsageMessageLike = MessageLike[PackageStorageUsage](_.namespace)

  case class DeviceUpdateReport(namespace: Namespace, device: DeviceId, updateId: UpdateId, timestampVersion: Int,
                                operationResult: Map[EcuIdentifier, OperationResult], resultCode: Int)

  implicit val operationResultEncoder: Encoder[OperationResult] = deriveEncoder
  implicit val operationResultDecoder: Decoder[OperationResult] = deriveDecoder

  implicit val deviceUpdateReportEncoder: Encoder[DeviceUpdateReport] = deriveEncoder
  implicit val deviceUpdateReportDecoder: Decoder[DeviceUpdateReport] = deriveDecoder

  implicit val deviceUpdateReportMessageLike = MessageLike[DeviceUpdateReport](_.device.toString)
}
