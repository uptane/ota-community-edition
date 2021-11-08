package com.advancedtelematic.libtuf.data

import java.net.URI
import java.time.Instant
import com.advancedtelematic.libtuf.data.ClientDataType._
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, RoleType, TargetFormat, TargetName, TargetVersion}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import ClientDataType.TufRole._
import io.circe.syntax._
import cats.data.StateT
import io.circe._
import io.circe.{ACursor, Decoder, Json}
import cats.implicits._

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

  implicit val targetNameEncoder: Encoder[TargetName] = Encoder.encodeString.contramap(_.value)
  implicit val targetNameDecoder: Decoder[TargetName] = Decoder.decodeString.map(TargetName)

  implicit val targetVersionEncoder: Encoder[TargetVersion] = Encoder.encodeString.contramap(_.value)
  implicit val targetVersionDecoder: Decoder[TargetVersion] = Decoder.decodeString.map(TargetVersion)

  implicit val targetCustomDecoder: Decoder[TargetCustom] = Decoder.fromState {
    import Decoder.state.decodeField
    import cats.implicits._

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
  implicit val rootRoleCodec: Codec[RootRole] = Codec.from(rootRoleDecoder, rootRoleEncoder)

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
  implicit val targetsRoleCodec: Codec[TargetsRole] = Codec.from(targetsRoleDecoder, targetsRoleEncoder)

  implicit val offlineUpdatesRoleEncoder: Encoder[OfflineUpdatesRole] = deriveEncoder[OfflineUpdatesRole].encodeRoleType
  implicit val offlineUpdatesRoleDecoder: Decoder[OfflineUpdatesRole] = deriveDecoder[OfflineUpdatesRole].validateRoleType
  implicit val offlineUpdatesRoleCodec: Codec[OfflineUpdatesRole] = Codec.from(offlineUpdatesRoleDecoder, offlineUpdatesRoleEncoder)

  implicit val offlineSnapshotRoleEncoder: Encoder[OfflineSnapshotRole] = deriveEncoder[OfflineSnapshotRole].encodeRoleType
  implicit val offlineSnapshotRoleDecoder: Decoder[OfflineSnapshotRole] = deriveDecoder[OfflineSnapshotRole].validateRoleType
  implicit val offlineSnapshotRoleCodec: Codec[OfflineSnapshotRole] = Codec.from(offlineSnapshotRoleDecoder, offlineSnapshotRoleEncoder)


  implicit val snapshotRoleEncoder: Encoder[SnapshotRole] = deriveEncoder[SnapshotRole].encodeRoleType
  implicit val snapshotRoleDecoder: Decoder[SnapshotRole] = deriveDecoder[SnapshotRole].validateRoleType
  implicit val snapshotRoleCodec: Codec[SnapshotRole] = Codec.from(snapshotRoleDecoder, snapshotRoleEncoder)

  implicit val timestampRoleEncoder: Encoder[TimestampRole] = deriveEncoder[TimestampRole].encodeRoleType
  implicit val timestampRoleDecoder: Decoder[TimestampRole] = deriveDecoder[TimestampRole].validateRoleType
  implicit val timestampRoleCodec: Codec[TimestampRole] = Codec.from(timestampRoleDecoder, timestampRoleEncoder)

  implicit private class EncodeRoleTypeOp[T](encoder: Encoder[T])(implicit tr: TufRole[T]) {
    def encodeRoleType: Encoder[T] = encoder.mapJson(_.mapObject(_.add("_type", tr.decoderDiscriminator.asJson)))
  }

  implicit private class ValidateRoleOp[T](decoder: Decoder[T])(implicit tr: TufRole[T]) {
    def validateRoleType: Decoder[T] = decoder.validate({ c =>
      val _type = c.downField("_type").as[String].getOrElse("invalid json tuf role")
      _type.equalsIgnoreCase(tr.decoderDiscriminator)
    },
      s"Invalid type for role: ${tr.decoderDiscriminator}"
    )
  }
}
