package com.advancedtelematic.libtuf.data

import java.net.URI
import java.time.Instant

import com.advancedtelematic.libtuf.data.ClientDataType._
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, RoleType, TargetFormat, TargetName, TargetVersion}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import ClientDataType.TufRole._
import io.circe.syntax._
import cats.syntax.either._
import cats.data.StateT
import cats.instances.either._
import io.circe._
import io.circe.{ ACursor, Decoder, Json }

object ClientCodecs {
  import TufCodecs._
  import io.circe.generic.semiauto._
  import com.advancedtelematic.libats.codecs.CirceCodecs._

  implicit val targetFormatEncoder: Encoder[TargetFormat] = Encoder.encodeEnumeration(TargetFormat)
  implicit val targetFormatDecoder: Decoder[TargetFormat] = Decoder.decodeEnumeration(TargetFormat)

  implicit val roleTypeKeyEncoder: KeyEncoder[RoleType] = KeyEncoder.encodeKeyString.contramap[RoleType](_.toString.toLowerCase)
  implicit val roleTypeKeyDecoder: KeyDecoder[RoleType] = KeyDecoder.decodeKeyString.map[RoleType](s => RoleType.withName(s.toUpperCase))

  implicit val roleKeyEncoder: Encoder[RoleKeys] = deriveEncoder
  implicit val roleKeyDecoder: Decoder[RoleKeys] = deriveDecoder


  implicit val targetNameEncoder: Encoder[TargetName] = anyValStringEncoder
  implicit val targetNameDecoder: Decoder[TargetName] = anyValStringDecoder

  implicit val targetVersionEncoder: Encoder[TargetVersion] = anyValStringEncoder
  implicit val targetVersionDecoder: Decoder[TargetVersion] = anyValStringDecoder

  implicit val targetCustomDecoder: Decoder[TargetCustom] = Decoder.fromState {
    import Decoder.state.decodeField

    for {
      name <- decodeField[TargetName]("name")
      version <- decodeField[TargetVersion]("version")
      hardwareIds <- decodeField[Seq[HardwareIdentifier]]("hardwareIds")
      targetFormat <- decodeField[Option[TargetFormat]]("targetFormat")
      uri <- decodeField[Option[URI]]("uri")
      createdAt <- decodeField[Instant]("createdAt")
      updatedAt <- decodeField[Instant]("updatedAt")
      cliUploaded <- decodeField[Option[Boolean]]("cliUploaded")
      proprietary <- StateT.inspectF((_: ACursor).as[Json])
    } yield TargetCustom(name, version, hardwareIds, targetFormat, uri, cliUploaded, createdAt, updatedAt, proprietary)
  }

  implicit val targetCustomEncoder: Encoder[TargetCustom] = Encoder.instance { targetCustom =>
    val main = List(
      ("name", Json.fromString(targetCustom.name.value)),
      ("version", Json.fromString(targetCustom.version.value)),
      ("hardwareIds", targetCustom.hardwareIds.asJson),
      ("targetFormat", targetCustom.targetFormat.asJson),
      ("uri", targetCustom.uri.asJson),
      ("createdAt", targetCustom.createdAt.asJson),
      ("updatedAt", targetCustom.updatedAt.asJson))

    val withCliUploaded = if(targetCustom.cliUploaded.isDefined)
      ("cliUploaded", targetCustom.cliUploaded.asJson) :: main
    else
      main

    targetCustom.proprietary.deepMerge(Json.fromFields(withCliUploaded))
  }

  implicit val clientTargetItemEncoder: Encoder[ClientTargetItem] = deriveEncoder
  implicit val clientTargetItemDecoder: Decoder[ClientTargetItem] = deriveDecoder

  implicit val metaItemEncoder: Encoder[MetaItem] = deriveEncoder
  implicit val metaItemDecoder: Decoder[MetaItem] = deriveDecoder

  implicit val rootRoleEncoder: Encoder[RootRole] = deriveEncoder[RootRole].encodeRoleType
  implicit val rootRoleDecoder: Decoder[RootRole] = deriveDecoder[RootRole].validateRoleType

  implicit val delegatedRoleNameEncoder: Encoder[DelegatedRoleName] = ValidatedString.validatedStringEncoder
  implicit val delegatedRoleNameDecoder: Decoder[DelegatedRoleName] = ValidatedString.validatedStringDecoder

  implicit val delegatedPathPatternEncoder: Encoder[DelegatedPathPattern] = ValidatedString.validatedStringEncoder
  implicit val delegatedPathPatternDecoder: Decoder[DelegatedPathPattern] = ValidatedString.validatedStringDecoder

  implicit val delegatedRoleEncoder: Encoder[Delegation] = deriveEncoder
  implicit val delegatedRoleDecoder: Decoder[Delegation] = deriveDecoder

  implicit val delegationsEncoder: Encoder[Delegations] = deriveEncoder
  implicit val delegationsDecoder: Decoder[Delegations] = deriveDecoder[Delegations]

  implicit val targetsRoleEncoder: Encoder[TargetsRole] = deriveEncoder[TargetsRole].encodeRoleType.mapJson(_.dropNullValues)
  implicit val targetsRoleDecoder: Decoder[TargetsRole] = deriveDecoder[TargetsRole].validateRoleType

  implicit val snapshotRoleEncoder: Encoder[SnapshotRole] = deriveEncoder[SnapshotRole].encodeRoleType
  implicit val snapshotRoleDecoder: Decoder[SnapshotRole] = deriveDecoder[SnapshotRole].validateRoleType

  implicit val timestampRoleEncoder: Encoder[TimestampRole] = deriveEncoder[TimestampRole].encodeRoleType
  implicit val timestampRoleDecoder: Decoder[TimestampRole] = deriveDecoder[TimestampRole].validateRoleType

  implicit private class EncodeRoleTypeOp[T](encoder: Encoder[T])(implicit tr: TufRole[T]) {
    def encodeRoleType: Encoder[T] = encoder.mapJson(_.deepMerge(Json.obj("_type" -> Json.fromString(tr.typeStr))))
  }

  implicit private class ValidateRoleOp[T](decoder: Decoder[T])(implicit tr: TufRole[T]) {
    def validateRoleType: Decoder[T] = decoder.validate({ c =>
      val _type = c.downField("_type").as[String].valueOr(throw _).toLowerCase.capitalize
      _type == tr.typeStr
    },
      s"Invalid type for role: ${tr.roleType}"
    )
  }
}
