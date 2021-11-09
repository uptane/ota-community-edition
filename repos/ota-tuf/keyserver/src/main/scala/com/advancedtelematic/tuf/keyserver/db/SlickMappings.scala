package com.advancedtelematic.tuf.keyserver.db

import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus

object SlickMappings {
  implicit val keyGenRequestStatusMapper = SlickEnumMapper.enumMapper(KeyGenRequestStatus)
}
