package com.advancedtelematic.libtuf_server.data

import com.advancedtelematic.libats.codecs.CirceAts._
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper
import com.advancedtelematic.libats.slick.db.{SlickCirceMapper, SlickEncryptedColumn}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedRoleName, TargetCustom}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{EcPrime256KeyType, Ed25519KeyType, JsonSignedPayload, KeyType, RoleType, RsaKeyType, TufKey, TufPrivateKey}
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.slick.db.SlickCirceMapper
import com.advancedtelematic.libtuf.data.ValidatedString
import com.advancedtelematic.libtuf.data.ValidatedString.{ValidatedString, ValidatedStringValidation}
import slick.jdbc.MySQLProfile.api._

import scala.reflect.ClassTag

object TufSlickMappings {

  implicit val keyTypeMapper = MappedColumnType.base[KeyType, String](
    {
      case RsaKeyType ⇒ "RSA"
      case Ed25519KeyType ⇒ "ED25519"
      case EcPrime256KeyType ⇒ "ECPRIME256V1"
    },
    {
      case "RSA" ⇒ RsaKeyType
      case "ED25519" ⇒ Ed25519KeyType
      case "ECPRIME256V1" ⇒ EcPrime256KeyType
    }
  )

  implicit val roleTypeMapper = SlickEnumMapper.enumMapper(RoleType)

  implicit val checksumMapper = SlickCirceMapper.circeMapper[Checksum]

  implicit val targetCustomMapper = SlickCirceMapper.circeMapper[TargetCustom]

  implicit val jsonSignedPayloadMapper = SlickCirceMapper.circeMapper[JsonSignedPayload]

  implicit val tufKeyMapper = SlickCirceMapper.circeMapper[TufKey]

  implicit val encryptedTufPrivateKeyMapper = SlickEncryptedColumn.encryptedColumnJsonMapper[TufPrivateKey]
}
