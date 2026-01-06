package com.advancedtelematic.treehub.db

import com.advancedtelematic.data.DataType
import com.advancedtelematic.data.DataType.ObjectStatus
import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object SlickMappings {
  implicit val objectStatusMapping: JdbcType[DataType.ObjectStatus.Value] with BaseTypedType[DataType.ObjectStatus.Value] = SlickEnumMapper.enumMapper(ObjectStatus)
}
