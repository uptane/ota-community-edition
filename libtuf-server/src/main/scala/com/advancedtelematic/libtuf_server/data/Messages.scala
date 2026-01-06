package com.advancedtelematic.libtuf_server.data

import java.time.Instant

import com.advancedtelematic.libtuf.data.ClientDataType.TargetCustom
import com.advancedtelematic.libtuf.data.TufDataType.{OperationResult, TargetFilename}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libats.messaging_datatype.MessageLike

object Messages {

  final case class TufTargetAdded(namespace: Namespace,
                                  filename: TargetFilename,
                                  checksum: Checksum,
                                  length: Long,
                                  custom: Option[TargetCustom])

  implicit val tufTargetAddedEncoder: Encoder[TufTargetAdded] = deriveEncoder
  implicit val tufTargetAddedDecoder: Decoder[TufTargetAdded] = deriveDecoder

  implicit val tufTargetAddedMessageLike
    : com.advancedtelematic.libats.messaging_datatype.MessageLike[
      com.advancedtelematic.libtuf_server.data.Messages.TufTargetAdded
    ] = MessageLike[TufTargetAdded](_.namespace.get)

  final case class PackageStorageUsage(namespace: String, timestamp: Instant, byteCount: Long)

  import com.advancedtelematic.libats.codecs.CirceCodecs.{dateTimeDecoder, dateTimeEncoder}

  implicit val packageStorageUsageEncoder: Encoder[PackageStorageUsage] = deriveEncoder
  implicit val packageStorageUsageDecoder: Decoder[PackageStorageUsage] = deriveDecoder

  implicit val packageStorageUsageMessageLike
    : com.advancedtelematic.libats.messaging_datatype.MessageLike[
      com.advancedtelematic.libtuf_server.data.Messages.PackageStorageUsage
    ] = MessageLike[PackageStorageUsage](_.namespace)

  implicit val operationResultEncoder: Encoder[OperationResult] = deriveEncoder
  implicit val operationResultDecoder: Decoder[OperationResult] = deriveDecoder

  final case class TufTargetsModified(namespace: Namespace)
  implicit val tufTargetsModifiedEncoder: Encoder[TufTargetsModified] = deriveEncoder
  implicit val tufTargetsModifiedDecoder: Decoder[TufTargetsModified] = deriveDecoder

  implicit val tufTargetsModifiedMessageLike
    : com.advancedtelematic.libats.messaging_datatype.MessageLike[
      com.advancedtelematic.libtuf_server.data.Messages.TufTargetsModified
    ] = MessageLike[TufTargetsModified](_.namespace.get)

}
