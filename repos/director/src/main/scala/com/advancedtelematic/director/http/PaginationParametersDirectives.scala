package com.advancedtelematic.director.http

import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives._

object PaginationParametersDirectives {

  val PaginationParameters: Directive[(Long, Long)] =
    (parameters(Symbol("limit").as[Long].?) & parameters(Symbol("offset").as[Long].?)).tmap {
      case (mLimit, mOffset) =>
        val limit = mLimit.getOrElse(50L).min(1000)
        val offset = mOffset.getOrElse(0L)
        (limit, offset)
    }

}
