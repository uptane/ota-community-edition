package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.server.{Directive, MalformedQueryParamRejection}
import org.apache.pekko.http.scaladsl.server.Directives.*
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}

object PaginationParametersDirectives {

  val PaginationParameters: Directive[(Offset, Limit)] =
    (parameters(Symbol("limit").as[Long].?) & parameters(Symbol("offset").as[Long].?)).tflatMap {
      case (Some(mlimit), _) if mlimit < 0 =>
        reject(MalformedQueryParamRejection("limit", "limit cannot be negative"))
      case (_, Some(mOffset)) if mOffset < 0 =>
        reject(MalformedQueryParamRejection("offset", "offset cannot be negative"))
      case (mLimit, mOffset) =>
        val limit = mLimit.getOrElse(50L).min(1000)
        val offset = mOffset.getOrElse(0L)
        tprovide(Offset(offset), Limit(limit))
    }

}
