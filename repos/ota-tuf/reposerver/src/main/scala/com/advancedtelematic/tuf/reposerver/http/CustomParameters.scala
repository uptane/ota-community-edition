package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directive, Directives, MalformedQueryParamRejection}
import org.apache.pekko.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import com.advancedtelematic.libtuf.data.ClientDataType.TargetCustom
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  TargetFormat,
  TargetName,
  TargetVersion
}
import io.circe.*
import org.apache.pekko.http.scaladsl.unmarshalling.*
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import com.advancedtelematic.libats.http.RefinedMarshallingSupport.*
import com.advancedtelematic.libats.http.AnyvalMarshallingSupport.*
import com.advancedtelematic.libats.http.Errors.RawError

import scala.collection.immutable
import com.advancedtelematic.libtuf_server.data.Marshalling.targetFormatFromStringUnmarshaller
import com.advancedtelematic.tuf.reposerver.http.CustomParameterUnmarshallers.nonNegativeLong

import scala.math.Ordering.Implicits.infixOrderingOps

object TargetCustomParameterExtractors {

  import org.apache.pekko.http.scaladsl.server.Directives._
  import org.apache.pekko.http.scaladsl.server._
  import com.advancedtelematic.libats.http.RefinedMarshallingSupport._

  val all: Directive1[TargetCustom] =
    parameters(
      Symbol("name").as[TargetName],
      Symbol("version").as[TargetVersion],
      Symbol("hardwareIds")
        .as(CsvSeq[HardwareIdentifier])
        .?(immutable.Seq.empty[HardwareIdentifier]),
      Symbol("targetFormat").as[TargetFormat].?
    ).tmap { case (name, version, hardwareIds, targetFormat) =>
      TargetCustom(name, version, hardwareIds, targetFormat.orElse(Some(TargetFormat.BINARY)))
    }

}

object CustomParameterUnmarshallers {

  val nonNegativeLong: Unmarshaller[String, Long] =
    PredefinedFromStringUnmarshallers.longFromStringUnmarshaller
      .flatMap { ec => mat => value =>
        if (value < 0)
          FastFuture.failed(
            RawError(ErrorCode("Bad Request"), StatusCodes.BadRequest, "Value cannot be negative")
          )
        else FastFuture.successful(value)
      }

}

object PaginationParamsOps {

  implicit class PaginationResultOffsetOps(x: Option[Offset]) {
    def orDefaultOffset: Offset = x.getOrElse(Offset(0L))
  }

  implicit class PaginationResultLimitOps(x: Option[Limit]) {
    def orDefaultLimit: Limit = x.getOrElse(Limit(50L))
  }

  import Directives.*

  val PaginationParams: Directive[(Offset, Limit)] =
    (parameters(Symbol("limit").as[Long].?) & parameters(Symbol("offset").as[Long].?)).tflatMap {
      case (Some(mlimit), _) if mlimit < 0 =>
        reject(MalformedQueryParamRejection("limit", "limit cannot be negative"))
      case (_, Some(mOffset)) if mOffset < 0 =>
        reject(MalformedQueryParamRejection("offset", "offset cannot be negative"))
      case (mLimit, mOffset) =>
        val limit = mLimit.map(Limit.apply).orDefaultLimit
        val offset = mOffset.map(Offset.apply).orDefaultOffset
        tprovide(offset, limit)
    }

}
