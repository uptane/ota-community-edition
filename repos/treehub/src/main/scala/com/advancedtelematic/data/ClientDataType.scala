package com.advancedtelematic.data

import com.advancedtelematic.libats.messaging_datatype.DataType.Commit

object ClientDataType {

  import io.circe.{Decoder, Encoder}
  import com.advancedtelematic.libats.codecs.CirceRefined.*
  import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

  case class StaticDelta(from: Commit, to: Commit, size: Long)

  object StaticDelta {
    implicit val Encoder: Encoder[StaticDelta] = deriveEncoder[StaticDelta]
    implicit val Decoder: Decoder[StaticDelta] = deriveDecoder[StaticDelta]
  }

  case class CommitInfoRequest(commits: Seq[Commit])

  object CommitInfoRequest {
    implicit val Encoder: Encoder[CommitInfoRequest] = deriveEncoder[CommitInfoRequest]
    implicit val Decoder: Decoder[CommitInfoRequest] = deriveDecoder[CommitInfoRequest]
  }

  case class CommitSize(commit: Commit, size: Long)

  object CommitSize {
    implicit val Encoder: Encoder[CommitSize] = deriveEncoder[CommitSize]
    implicit val Decoder: Decoder[CommitSize] = deriveDecoder[CommitSize]
  }

  case class CommitInfo(from: Seq[CommitSize] = Seq(), to: Seq[CommitSize] = Seq()) {
    def +(other: CommitInfo): CommitInfo = CommitInfo(from ++ other.from, to ++ other.to)
  }

  object CommitInfo {
    implicit val Encoder: Encoder[CommitInfo] = deriveEncoder[CommitInfo]
    implicit val Decoder: Decoder[CommitInfo] = deriveDecoder[CommitInfo]
  }
}
