package com.advancedtelematic.director.deviceregistry.data

import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.libats.data.DataType.ResultCode
import io.circe.{Codec, Decoder, Encoder}
import com.advancedtelematic.director.deviceregistry.data.DataType.*
import com.advancedtelematic.libats.codecs.CirceAts.{namespaceDecoder, namespaceEncoder}
import com.advancedtelematic.libats.messaging_datatype.Messages.deviceMetricsObservationMessageLike.*

object Codecs {

  implicit val deviceOemIdEncoder: io.circe.Encoder[DeviceOemId] =
    Encoder.encodeString.contramap[Device.DeviceOemId](_.underlying)

  implicit val deviceOemIdDecoder
    : io.circe.Decoder[com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId] =
    Decoder.decodeString.map(Device.DeviceOemId.apply)

  implicit val deviceTEncoder: io.circe.Encoder.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.DeviceT
  ] =
    io.circe.generic.semiauto.deriveEncoder[DeviceT]

  implicit val deviceTDecoder
    : io.circe.Decoder[com.advancedtelematic.director.deviceregistry.data.DataType.DeviceT] =
    io.circe.generic.semiauto.deriveDecoder[DeviceT]

  implicit val setDeviceEncoder: io.circe.Encoder.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.SetDevice
  ] =
    io.circe.generic.semiauto.deriveEncoder[SetDevice]

  implicit val setDeviceDecoder
    : io.circe.Decoder[com.advancedtelematic.director.deviceregistry.data.DataType.SetDevice] =
    io.circe.generic.semiauto.deriveDecoder[SetDevice]

  implicit val updateDeviceEncoder: Encoder[UpdateDevice] =
    io.circe.generic.semiauto.deriveEncoder[UpdateDevice]

  implicit val updateDeviceDecoder: Decoder[UpdateDevice] =
    io.circe.generic.semiauto.deriveDecoder[UpdateDevice]

  implicit val resultCodeCodec
    : io.circe.Codec.AsObject[com.advancedtelematic.libats.data.DataType.ResultCode] =
    io.circe.generic.semiauto.deriveCodec[ResultCode]

  implicit val installationStatEncoder: io.circe.Encoder.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.InstallationStat
  ] = io.circe.generic.semiauto.deriveEncoder[InstallationStat]

  implicit val installationStatDecoder: io.circe.Decoder[
    com.advancedtelematic.director.deviceregistry.data.DataType.InstallationStat
  ] =
    io.circe.generic.semiauto.deriveDecoder[InstallationStat]

  implicit val packageListItemCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.PackageListItem
  ] =
    io.circe.generic.semiauto.deriveCodec[PackageListItem]

  implicit val packageListItemCountCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.PackageListItemCount
  ] = io.circe.generic.semiauto.deriveCodec[PackageListItemCount]

  implicit val renameTagIdCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.RenameTagId
  ] =
    io.circe.generic.semiauto.deriveCodec[RenameTagId]

  implicit val updateTagValueCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.UpdateTagValue
  ] =
    io.circe.generic.semiauto.deriveCodec[UpdateTagValue]

  implicit val tagInfoCodec
    : io.circe.Codec.AsObject[com.advancedtelematic.director.deviceregistry.data.DataType.TagInfo] =
    io.circe.generic.semiauto.deriveCodec[TagInfo]

  implicit val deviceUuidsCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.DeviceUuids
  ] =
    io.circe.generic.semiauto.deriveCodec[DeviceUuids]

  implicit val deviceQueryCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.DevicesQuery
  ] =
    io.circe.generic.semiauto.deriveCodec[DevicesQuery]

  implicit val updateHibernationStatusRequestCodec: Codec[UpdateHibernationStatusRequest] =
    io.circe.generic.semiauto.deriveCodec[UpdateHibernationStatusRequest]

  implicit val ObservationPublishResultCodec: io.circe.Codec.AsObject[
    com.advancedtelematic.director.deviceregistry.data.DataType.ObservationPublishResult
  ] = io.circe.generic.semiauto.deriveCodec[ObservationPublishResult]

  implicit val deviceStatusCountsCodec: Codec[DeviceStatusCounts] =
    io.circe.generic.semiauto.deriveCodec[DeviceStatusCounts]

}
