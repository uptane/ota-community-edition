/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

import java.time.Instant
import java.util.UUID
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import slick.jdbc.MySQLProfile.api._

case class Group(id: GroupId,
                 groupName: GroupName,
                 namespace: Namespace,
                 createdAt: Instant,
                 groupType: GroupType,
                 expression: Option[GroupExpression] = None)

object GroupType extends Enumeration {
  type GroupType = Value

  val static, dynamic = Value

  implicit val groupTypeMapper: slick.jdbc.MySQLProfile.BaseColumnType[
    com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
  ] = MappedColumnType.base[GroupType, String](_.toString, GroupType.withName)

  implicit val groupTypeEncoder: Encoder[GroupType] = Encoder.encodeEnumeration(GroupType)
  implicit val groupTypeDecoder: Decoder[GroupType] = Decoder.decodeEnumeration(GroupType)

  implicit val groupTypeUnmarshaller: Unmarshaller[String, GroupType] =
    Unmarshaller.strict(GroupType.withName)

}

object Group {

  final case class GroupId(uuid: UUID) extends UUIDKey
  object GroupId extends UUIDKeyObj[GroupId]

  implicit val groupEncoder: Encoder[Group] = deriveEncoder[Group]
  implicit val groupDecoder: Decoder[Group] = deriveDecoder[Group]
}

object GroupSortBy {
  sealed trait GroupSortBy
  case object Name extends GroupSortBy
  case object CreatedAt extends GroupSortBy
}
