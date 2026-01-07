package com.advancedtelematic.treehub.http

import akka.http.scaladsl.server.PathMatcher1
import com.advancedtelematic.data.DataType.{ObjectId, ValidDeltaId, ValidDeltaIndexId}
import com.advancedtelematic.libats.data.RefinedUtils.*

object PathMatchers {
  import akka.http.scaladsl.server.Directives.*

  val PrefixedObjectId: PathMatcher1[ObjectId] = (Segment / Segment).tflatMap { case (oprefix, osuffix) =>
    ObjectId.parse(oprefix + osuffix).toOption.map(Tuple1(_))
  }

  val PrefixedDeltaIdPath = Segments(2).flatMap { parts =>
    parts.mkString("").refineTry[ValidDeltaId].toOption
  }

  val PrefixedIndexId = Segments(2).flatMap { parts =>
    parts.mkString("").refineTry[ValidDeltaIndexId].toOption
  }
}
