package com.advancedtelematic.director.data

import com.advancedtelematic.director.data.DataType.*
import com.advancedtelematic.libats.codecs.CirceCodecs.*
import com.advancedtelematic.libats.http.HttpCodecs.*
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.*
import com.advancedtelematic.libtuf.data.TufCodecs.*
import UptaneDataType.*
import io.circe.*
import AdminDataType.*
import com.advancedtelematic.director.http.DeviceAssignments.AssignmentCreateResult
import com.advancedtelematic.director.http.{OfflineUpdateRequest, RemoteSessionRequest}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import cats.syntax.either.*
import com.advancedtelematic.director.data.ClientDataType.{CreateScheduledUpdateRequest, DeviceEcus}
import com.advancedtelematic.director.data.DbDataType.{
  Assignment,
  DeviceKnownState,
  EcuTarget,
  ProcessedAssignment
}
import io.circe.syntax.*

object Codecs {

  import DeviceRequest.*
  import io.circe.generic.semiauto.*
  import JsonDropNullValues.*

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

  implicit val deviceManifestEcuSignedEncoder: Encoder[DeviceManifest] =
    Encoder.encodeJson.contramap { deviceManifest =>
      val report = deviceManifest.installation_report match {
        case Left(InvalidInstallationReport(_, payload)) => payload.asJson
        case Left(MissingInstallationReport)             => Json.Null
        case Right(r)                                    => r.asJson
      }

      Map(
        "primary_ecu_serial" -> deviceManifest.primary_ecu_serial.asJson,
        "installation_report" -> report,
        "ecu_version_manifests" -> deviceManifest.ecu_version_manifests.asJson
      ).asJson
    }

  implicit val deviceManifestEcuSignedDecoder: Decoder[DeviceManifest] =
    Decoder.decodeHCursor.emapTry { cursor =>
      val installationReportCursor = cursor.downField("installation_report")

      val installation_report: Either[InvalidInstallationReportError, InstallationReportEntity] =
        installationReportCursor
          .as[Option[InstallationReportEntity]]
          .leftMap(err => InvalidInstallationReport(err.message, installationReportCursor.focus))
          .flatMap {
            case Some(r) => Right(r)
            case None    => Left(MissingInstallationReport)
          }

      for {
        primaryEcuSerial <- cursor.downField("primary_ecu_serial").as[EcuIdentifier].toTry
        ecuVersionManifests <- cursor
          .downField("ecu_version_manifests")
          .as[Map[EcuIdentifier, SignedPayload[EcuManifest]]]
          .toTry
      } yield DeviceManifest(primaryEcuSerial, ecuVersionManifests, installation_report)
    }

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

  implicit val multiTargetUpdateEncoder: Encoder[MultiTargetUpdate] = deriveEncoder
  implicit val multiTargetUpdateDecoder: Decoder[MultiTargetUpdate] = deriveDecoder

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
  implicit val clientEcuTargetCodec: Codec[ClientDataType.EcuTarget] = deriveCodec
  implicit val clientDevicesCurrentTarget: Codec[ClientDataType.DevicesCurrentTarget] = deriveCodec
  implicit val codecEcuTargetId: Codec[ClientDataType.EcuTargetId] = deriveCodec
  implicit val codecEcu: Codec[ClientDataType.Ecu] = deriveCodec
  implicit val codecDeviceEcus: Codec[DeviceEcus] = deriveCodec

  implicit val offlineUpdateRequestEncoder: Encoder[OfflineUpdateRequest] = deriveEncoder
  implicit val offlineUpdateRequestDecoder: Decoder[OfflineUpdateRequest] = deriveDecoder

  implicit val remoteSessionRequestCodec: Codec[RemoteSessionRequest] = deriveCodec

  implicit val assignmentCreateResultCodec: Codec[AssignmentCreateResult] = deriveCodec

  implicit val scheduledUpdateStatusDecoder: Decoder[ScheduledUpdate.Status] =
    enumeratum.Circe.decoder(ScheduledUpdate.Status)

  implicit val scheduledUpdateStatusEncoder: Encoder[ScheduledUpdate.Status] =
    enumeratum.Circe.encoder(ScheduledUpdate.Status)

  implicit val scheduledUpdateCodec: Codec[ScheduledUpdate] = deriveCodec

  implicit val createScheduledUpdateRequestCodec: Codec[CreateScheduledUpdateRequest] = deriveCodec

  implicit val ecuTargetCodec: Codec[EcuTarget] = deriveCodec[EcuTarget]
  implicit val assignmentCodec: Codec[Assignment] = deriveCodec[Assignment]

  implicit val processedAssignmentCodec: Codec[ProcessedAssignment] =
    deriveCodec[ProcessedAssignment]

  implicit val deviceKnownStateCodec: Codec[DeviceKnownState] = deriveCodec[DeviceKnownState]
}
