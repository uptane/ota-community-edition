package com.advancedtelematic.campaigner.db

import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper

object SlickMapping {
  implicit val metadataTypeMapper = SlickEnumMapper.enumMapper(MetadataType)
  implicit val deviceStatusMapper = SlickEnumMapper.enumMapper(DeviceStatus)
  implicit val cancelTaskStatusMapper = SlickEnumMapper.enumMapper(CancelTaskStatus)
  implicit val updateTypeMapper = SlickEnumMapper.enumMapper(UpdateType)
  implicit val campaignStatusMapper = SlickEnumMapper.enumMapper(CampaignStatus)
}
