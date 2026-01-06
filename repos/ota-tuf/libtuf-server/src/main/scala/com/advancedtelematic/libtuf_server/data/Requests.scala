package com.advancedtelematic.libtuf_server.data

import com.advancedtelematic.libats.codecs.CirceCodecs.*
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, TargetFilename}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.generic.semiauto.deriveDecoder

import java.time.Instant

object Requests {

  case class CreateRepositoryRequest(keyType: KeyType)

  implicit val createRepositoryRequestEncoder: Encoder[CreateRepositoryRequest] = deriveEncoder
  implicit val createRepositoryRequestDecoder: Decoder[CreateRepositoryRequest] = deriveDecoder

  case class TargetComment(value: String) extends AnyVal

  implicit val targetCommentEncoder: Encoder[TargetComment] =
    Encoder.encodeString.contramap(_.value)

  implicit val targetCommentDecoder: Decoder[TargetComment] =
    Decoder.decodeString.map(TargetComment.apply)

  case class CommentRequest(comment: TargetComment)

  implicit val commentRequestEncoder: Encoder[CommentRequest] = deriveEncoder
  implicit val commentRequestDecoder: Decoder[CommentRequest] = deriveDecoder

  case class FilenameComment(filename: TargetFilename, comment: TargetComment)

  implicit val filenameCommentEncoder: Encoder[FilenameComment] = deriveEncoder
  implicit val filenameCommentDecoder: Decoder[FilenameComment] = deriveDecoder

  case class ExpireNotBeforeRequest(expireAt: Instant)

  implicit val refreshRequestEncoder: Encoder[ExpireNotBeforeRequest] =
    io.circe.generic.semiauto.deriveEncoder[ExpireNotBeforeRequest]

  implicit val refreshRequestDecoder: Decoder[ExpireNotBeforeRequest] =
    io.circe.generic.semiauto.deriveDecoder[ExpireNotBeforeRequest]

}
