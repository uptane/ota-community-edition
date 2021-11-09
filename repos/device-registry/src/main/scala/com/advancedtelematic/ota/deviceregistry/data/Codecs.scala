package com.advancedtelematic.ota.deviceregistry.data

import com.advancedtelematic.libats.data.DataType.ResultCode
import io.circe.{Codec, Decoder, Encoder}
import com.advancedtelematic.ota.deviceregistry.data.DataType._
import com.advancedtelematic.libats.codecs.CirceAts.{namespaceDecoder, namespaceEncoder}

object Codecs {

  implicit val deviceOemIdEncoder = Encoder.encodeString.contramap[Device.DeviceOemId](_.underlying)
  implicit val deviceOemIdDecoder = Decoder.decodeString.map(Device.DeviceOemId.apply)

  implicit val deviceTEncoder = io.circe.generic.semiauto.deriveEncoder[DeviceT]
  implicit val deviceTDecoder = io.circe.generic.semiauto.deriveDecoder[DeviceT]

  implicit val updateDeviceEncoder = io.circe.generic.semiauto.deriveEncoder[UpdateDevice]
  implicit val updateDeviceDecoder = io.circe.generic.semiauto.deriveDecoder[UpdateDevice]

  implicit val resultCodeCodec = io.circe.generic.semiauto.deriveCodec[ResultCode]

  implicit val installationStatEncoder = io.circe.generic.semiauto.deriveEncoder[InstallationStat]
  implicit val installationStatDecoder = io.circe.generic.semiauto.deriveDecoder[InstallationStat]

  implicit val packageListItemCodec = io.circe.generic.semiauto.deriveCodec[PackageListItem]

  implicit val packageListItemCountCodec = io.circe.generic.semiauto.deriveCodec[PackageListItemCount]

  implicit val renameTagIdCodec = io.circe.generic.semiauto.deriveCodec[RenameTagId]

  implicit val updateTagValueCodec = io.circe.generic.semiauto.deriveCodec[UpdateTagValue]

  implicit val tagInfoCodec = io.circe.generic.semiauto.deriveCodec[TagInfo]

  implicit val deviceUuidsCodec = io.circe.generic.semiauto.deriveCodec[DeviceUuids]
}
