package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.AdminDataType.TargetUpdate
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.libats.data.DataType.HashMethod
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import com.advancedtelematic.libats.slick.db.SlickCirceMapper
import slick.jdbc.MySQLProfile.api._

object SlickMapping {
  import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper
  import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat

  implicit val hashMethodColumn = MappedColumnType.base[HashMethod, String](_.toString, HashMethod.withName)
  implicit val targetFormatMapper = SlickEnumMapper.enumMapper(TargetFormat)

  implicit val targetUpdateMapper = SlickCirceMapper.circeMapper[TargetUpdate]
}
