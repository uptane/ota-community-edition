package com.advancedtelematic.libtuf_server.data

import akka.http.scaladsl.server.PathMatchers
import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.advancedtelematic.libtuf.data.TufDataType.{RoleType, TargetFormat, ValidKeyId}
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedRoleName

import scala.util.Try

object Marshalling {
  implicit val targetFormatFromStringUnmarshaller = Unmarshaller.strict[String, TargetFormat](s => TargetFormat.withName(s.toUpperCase))

  val KeyIdPath = PathMatchers.Segment.flatMap(_.refineTry[ValidKeyId].toOption)

  val RoleTypePath = PathMatchers.Segment.flatMap(v => Try(RoleType.withName(v.toUpperCase)).toOption)

  val JsonRoleTypeMetaPath = PathMatchers.Segment.flatMap { str =>
    val (roleTypeStr, _) = str.splitAt(str.indexOf(".json"))
    Try(RoleType.withName(roleTypeStr.toUpperCase)).toOption
  }

  val DelegatedRoleUriPath = PathMatchers.Segment.flatMap { str =>
    val (roleName, _) = str.splitAt(str.indexOf(".json"))
    DelegatedRoleName.delegatedRoleNameValidation(roleName).toOption
  }
}
