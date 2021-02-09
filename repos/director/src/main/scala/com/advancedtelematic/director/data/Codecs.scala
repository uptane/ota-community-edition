package com.advancedtelematic.director.data

import com.advancedtelematic.director.data.DataType._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.http.HttpCodecs._
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs._
import com.advancedtelematic.libtuf.data.TufCodecs._
import UptaneDataType._
import io.circe._
import AdminDataType._

object Codecs {
  import DeviceRequest._
  import io.circe.generic.semiauto._
  import JsonDropNullValues._

  implicit val decoderFileInfo: Decoder[FileInfo] = deriveDecoder
  implicit val encoderFileInfo: Encoder[FileInfo] = deriveEncoder

  implicit val decoderHashes: Decoder[Hashes] = deriveDecoder
  implicit val encoderHashes: Encoder[Hashes] = deriveEncoder

  implicit val decoderImage: Decoder[Image] = deriveDecoder
  implicit val encoderImage: Encoder[Image] = deriveEncoder

  implicit val targetItemCustomEcuDataEncoder: Encoder[TargetItemCustomEcuData] = deriveEncoder
  implicit val targetItemCustomEcuDataDecoder: Decoder[TargetItemCustomEcuData] = deriveDecoder

  implicit val targetItemCustomEncoder: Encoder[TargetItemCustom] = deriveEncoder
  implicit val targetItemCustomDecoder: Decoder[TargetItemCustom] = deriveDecoder

  implicit val decoderCustomImage: Decoder[TargetImage] = deriveDecoder
  implicit val encoderCustomImage: Encoder[TargetImage] = deriveEncoder

  implicit val decoderEcuManifest: Decoder[EcuManifest] = deriveDecoder
  implicit val encoderEcuManifest: Encoder[EcuManifest] = deriveEncoder[EcuManifest].dropNullValues

  implicit val deviceManifestEcuSignedEncoder: Encoder[DeviceManifest] = deriveEncoder
  implicit val deviceManifestEcuSignedDecoder: Decoder[DeviceManifest] = deriveDecoder

  implicit val decoderInstallationItem: Decoder[InstallationItem] = deriveDecoder
  implicit val encoderInstallationItem: Encoder[InstallationItem] = deriveEncoder

  implicit val decoderInstallationReport: Decoder[InstallationReport] = deriveDecoder
  implicit val encoderInstallationReport: Encoder[InstallationReport] = deriveEncoder

  implicit val decoderInstallationReportEntity: Decoder[InstallationReportEntity] = deriveDecoder
  implicit val encoderInstallationReportEntity: Encoder[InstallationReportEntity] = deriveEncoder

  implicit val decoderRegisterEcu: Decoder[RegisterEcu] = deriveDecoder
  implicit val encoderRegisterEcu: Encoder[RegisterEcu] = deriveEncoder[RegisterEcu].dropNullValues

  implicit val decoderRegisterDevice: Decoder[RegisterDevice] = deriveDecoder
  implicit val encoderRegisterDevice: Encoder[RegisterDevice] = deriveEncoder

  implicit val decoderTargetUpdate: Decoder[TargetUpdate] = deriveDecoder[TargetUpdate]
  implicit val encoderTargetUpdate: Encoder[TargetUpdate] = deriveEncoder

  implicit val decoderTargetUpdateRequest: Decoder[TargetUpdateRequest] = deriveDecoder
  implicit val encoderTargetUpdateRequest: Encoder[TargetUpdateRequest] = deriveEncoder

  implicit val multiTargetUpdateCreatedEncoder: Encoder[MultiTargetUpdate] = deriveEncoder
  implicit val multiTargetUpdateCreatedDecoder: Decoder[MultiTargetUpdate] = deriveDecoder

  implicit val assignUpdateRequestEncoder: Encoder[AssignUpdateRequest] = deriveEncoder
  implicit val assignUpdateRequestDecoder: Decoder[AssignUpdateRequest] = deriveDecoder

  implicit val findImageCountEncoder: Encoder[FindImageCount] = deriveEncoder
  implicit val findImageCountDecoder: Decoder[FindImageCount] = deriveDecoder

  implicit val ecuInfoImageEncoder: Encoder[EcuInfoImage] = deriveEncoder
  implicit val ecuInfoImageDecoder: Decoder[EcuInfoImage] = deriveDecoder

  implicit val ecuInfoResponseEncoder: Encoder[EcuInfoResponse] = deriveEncoder
  implicit val ecuInfoResponseDecoder: Decoder[EcuInfoResponse] = deriveDecoder

  implicit val queueResponseEncoder: Encoder[QueueResponse] = deriveEncoder
  implicit val queueResponseDecoder: Decoder[QueueResponse] = deriveDecoder

  implicit val targetsCustomEncoder: Encoder[DeviceTargetsCustom] = deriveEncoder
  implicit val targetsCustomDecoder: Decoder[DeviceTargetsCustom] = deriveDecoder

  implicit val operationResultCodec: Codec[OperationResult] = deriveCodec
  implicit val ecuManifestCustomCodec: Codec[EcuManifestCustom] = deriveCodec
  implicit val clientDeviceCodec: Codec[ClientDataType.Device] = deriveCodec
}

