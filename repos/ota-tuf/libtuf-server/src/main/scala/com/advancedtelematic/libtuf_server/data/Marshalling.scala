package com.advancedtelematic.libtuf_server.data

import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.server.PathMatchers
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko
import com.advancedtelematic.libats.data.RefinedUtils.*
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedRoleName
import com.advancedtelematic.libtuf.data.TufCodecs
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{
  JsonSignedPayload,
  RoleType,
  TargetFormat,
  ValidKeyId
}
import com.advancedtelematic.libtuf_server.repo.server.Errors

import scala.util.Try

object Marshalling {

  implicit val targetFormatFromStringUnmarshaller: pekko.http.scaladsl.unmarshalling.Unmarshaller[
    String,
    com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
  ] = Unmarshaller.strict[String, TargetFormat](s => TargetFormat.withName(s.toUpperCase))

  val KeyIdPath = PathMatchers.Segment.flatMap(_.refineTry[ValidKeyId].toOption)

  val RoleTypePath =
    PathMatchers.Segment.flatMap(v => Try(RoleType.withName(v.toUpperCase)).toOption)

  val JsonRoleTypeMetaPath = PathMatchers.Segment.flatMap { str =>
    val (roleTypeStr, _) = str.splitAt(str.indexOf(".json"))
    Try(RoleType.withName(roleTypeStr.toUpperCase)).toOption
  }

  val DelegatedRoleNamePath = PathMatchers.Segment.flatMap { str =>
    DelegatedRoleName.delegatedRoleNameValidation(str).toOption
  }

  val DelegatedRoleUriPath = PathMatchers.Segment.flatMap { str =>
    val (roleName, _) = str.splitAt(str.indexOf(".json"))
    roleName.isEmpty() match {
      case false => Some(roleName)
      case _     => None
    }
  }

  implicit val jsonSignedPayloadMarshaller: ToEntityMarshaller[JsonSignedPayload] = Marshaller
    .stringMarshaller(MediaTypes.`application/json`)
    .compose[JsonSignedPayload](jsonSignedPayload =>
      TufCodecs.jsonSignedPayloadEncoder.apply(jsonSignedPayload).canonical
    )

}
