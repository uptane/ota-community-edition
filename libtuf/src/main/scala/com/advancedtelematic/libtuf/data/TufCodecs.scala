package com.advancedtelematic.libtuf.data

import io.circe.syntax._
import cats.syntax.either._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.TufDataType.{EcPrime256KeyType, _}
import io.circe._
import cats.syntax.functor._
import com.advancedtelematic.libats.codecs.CirceAnyVal
import com.advancedtelematic.libtuf.data.TufDataType.SignatureMethod.SignatureMethod

import scala.util.Try

object TufCodecs {

  import io.circe.generic.semiauto._
  import com.advancedtelematic.libats.codecs.CirceCodecs._

  implicit val signatureMethodEncoder: Encoder[SignatureMethod] =
    Encoder.encodeEnumeration(SignatureMethod)

  implicit val signatureMethodLenientDecoder: Decoder[SignatureMethod] =
    Decoder.decodeString.flatMap {
      case "rsassa-pss" => Decoder.const(SignatureMethod.RSASSA_PSS_SHA256)
      case _            => Decoder.decodeEnumeration(SignatureMethod)
    }

  implicit val signatureEncoder: Encoder[Signature] = deriveEncoder
  implicit val signatureDecoder: Decoder[Signature] = deriveDecoder

  implicit val clientSignatureEncoder: Encoder[ClientSignature] = deriveEncoder
  implicit val clientSignatureDecoder: Decoder[ClientSignature] = deriveDecoder

  implicit def signedPayloadEncoder[T: Encoder]: Encoder[SignedPayload[T]] =
    jsonSignedPayloadEncoder.contramap[SignedPayload[T]] { e =>
      JsonSignedPayload(e.signatures, e.json)
    }

  implicit def signedPayloadDecoder[T: Encoder: Decoder]: Decoder[SignedPayload[T]] =
    jsonSignedPayloadDecoder.emapTry { e =>
      e.signed
        .as[T]
        .map { ee =>
          SignedPayload[T](e.signatures, ee, e.signed)
        }
        .toTry
    }

  implicit def signedPayloadCodec[T: Encoder: Decoder]: Codec[SignedPayload[T]] =
    io.circe.Codec.from(signedPayloadDecoder, signedPayloadEncoder)

  // This encoder is not implicit to force custom http marshallers to be used instead, to marshall responses as canonical json
  val jsonSignedPayloadEncoder: Encoder[JsonSignedPayload] = deriveEncoder
  implicit val jsonSignedPayloadDecoder: Decoder[JsonSignedPayload] = deriveDecoder

  implicit val rsaKeyTypeEncoder: Encoder[RsaKeyType.type] = Encoder[String].contramap(_ => "RSA")

  implicit val rsaKeyTypeDecoder: Decoder[RsaKeyType.type] =
    Decoder[String].emap(str => Either.cond(str == "RSA", RsaKeyType, s"Unknown KeyType: $str"))

  implicit val ed25519KeyTypeEncoder: Encoder[Ed25519KeyType.type] =
    Encoder[String].contramap(_ => "ED25519")

  implicit val ed25519KeyTypeDecoder: Decoder[Ed25519KeyType.type] = Decoder[String].emap(str =>
    Either.cond(str == "ED25519", Ed25519KeyType, s"Unknown KeyType: $str")
  )

  implicit val ecPrime256KeyTypeEncoder: Encoder[EcPrime256KeyType.type] =
    Encoder[String].contramap(_ => "ECPRIME256V1")

  implicit val ecPrime256KeyTypeDecoder: Decoder[EcPrime256KeyType.type] =
    Decoder[String].emap(str =>
      Either.cond(str == "ECPRIME256V1", EcPrime256KeyType, s"Unknown KeyType: $str")
    )

  implicit val keyTypeDecoder: Decoder[KeyType] = List[Decoder[KeyType]](
    rsaKeyTypeDecoder.widen,
    ed25519KeyTypeDecoder.widen,
    ecPrime256KeyTypeDecoder.widen
  ).reduceLeft(_ or _)

  implicit val keyTypeEncoder: Encoder[KeyType] = Encoder.instance {
    case RsaKeyType        => RsaKeyType.asJson
    case Ed25519KeyType    => Ed25519KeyType.asJson
    case EcPrime256KeyType => EcPrime256KeyType.asJson
  }

  implicit val tufKeyEncoder: Encoder[TufKey] = Encoder.instance {
    case key @ RSATufKey(_) =>
      Json.obj(
        "keyval" -> Json.obj("public" -> RsaKeyType.crypto.encode(key).asJson),
        "keytype" -> RsaKeyType.asJson
      )
    case key @ Ed25519TufKey(_) =>
      Json.obj(
        "keyval" -> Json.obj("public" -> Ed25519KeyType.crypto.encode(key).asJson),
        "keytype" -> Ed25519KeyType.asJson
      )
    case key @ EcPrime256TufKey(_) =>
      Json.obj(
        "keyval" -> Json.obj("public" -> EcPrime256KeyType.crypto.encode(key).asJson),
        "keytype" -> EcPrime256KeyType.asJson
      )
  }

  implicit val tufPrivateKeyEncoder: Encoder[TufPrivateKey] = Encoder.instance {
    case key @ RSATufPrivateKey(_) =>
      Json.obj(
        "keyval" -> Json.obj("private" -> RsaKeyType.crypto.encode(key).asJson),
        "keytype" -> RsaKeyType.asJson
      )
    case key @ Ed25519TufPrivateKey(_) =>
      Json.obj(
        "keyval" -> Json.obj("private" -> Ed25519KeyType.crypto.encode(key).asJson),
        "keytype" -> Ed25519KeyType.asJson
      )
    case key @ EcPrime256TufPrivateKey(_) =>
      Json.obj(
        "keyval" -> Json.obj("private" -> EcPrime256KeyType.crypto.encode(key).asJson),
        "keytype" -> EcPrime256KeyType.asJson
      )
  }

  private def tufKeyDecoder[T](field: String, decodeFn: (KeyType, String) => Try[T]): Decoder[T] =
    Decoder.instance { cursor =>
      for {
        keyType <- cursor.downField("keytype").as[KeyType]
        keyVal <- cursor.downField("keyval").downField(field).as[String]
        key <- Either
          .fromTry(decodeFn(keyType, keyVal))
          .leftMap(ex => DecodingFailure(ex.getMessage, cursor.history))
      } yield key
    }

  implicit val tufKeyDecoder: Decoder[TufKey] = tufKeyDecoder("public", TufCrypto.parsePublic)

  implicit val tufPrivateKeyDecoder: Decoder[TufPrivateKey] =
    tufKeyDecoder("private", TufCrypto.parsePrivate)

  implicit val tufKeyPairEncoder: Encoder[TufKeyPair] = Encoder.instance {
    case RSATufKeyPair(pub, priv) =>
      Json.obj(
        "keytype" -> RsaKeyType.asJson,
        "keyval" -> Json.obj(
          "public" -> RsaKeyType.crypto.encode(pub).asJson,
          "private" -> RsaKeyType.crypto.encode(priv).asJson
        )
      )
    case Ed25519TufKeyPair(pub, priv) =>
      Json.obj(
        "keytype" -> Ed25519KeyType.asJson,
        "keyval" -> Json.obj(
          "public" -> Ed25519KeyType.crypto.encode(pub).asJson,
          "private" -> Ed25519KeyType.crypto.encode(priv).asJson
        )
      )
    case EcPrime256TufKeyPair(pub, priv) =>
      Json.obj(
        "keytype" -> EcPrime256KeyType.asJson,
        "keyval" -> Json.obj(
          "public" -> EcPrime256KeyType.crypto.encode(pub).asJson,
          "private" -> EcPrime256KeyType.crypto.encode(priv).asJson
        )
      )
  }

  implicit val tufKeyPairDecoder: Decoder[TufKeyPair] = Decoder.instance { cursor =>
    for {
      keyType <- cursor.downField("keytype").as[KeyType]
      keyVal = cursor.downField("keyval")
      pubkeyStr <- keyVal.downField("public").as[String]
      privkeyStr <- keyVal.downField("private").as[String]
      pair <- Either
        .fromTry(keyType.crypto.parseKeyPair(pubkeyStr, privkeyStr))
        .leftMap(ex => DecodingFailure(ex.getMessage, cursor.history))
    } yield pair
  }

  implicit val multipartUploadIdCodec: Codec[MultipartUploadId] =
    Codec.from(CirceAnyVal.anyValStringDecoder, CirceAnyVal.anyValStringEncoder)

  implicit val eTagCodec: Codec[ETag] =
    Codec.from(CirceAnyVal.anyValStringDecoder, CirceAnyVal.anyValStringEncoder)

  implicit val uploadPartETagCodec: Codec[UploadPartETag] = deriveCodec
  implicit val initMultipartUploadResultCodec: Codec[InitMultipartUploadResult] = deriveCodec
  implicit val getSignedUrlResultCodec: Codec[GetSignedUrlResult] = deriveCodec
  implicit val completeUploadRequestCodec: Codec[CompleteUploadRequest] = deriveCodec

}
