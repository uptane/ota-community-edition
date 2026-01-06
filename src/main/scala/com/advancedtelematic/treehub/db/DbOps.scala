package com.advancedtelematic.treehub.db

import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}

object DbOps {
  implicit class PaginationResultOffsetOps(x: Option[Offset]) {
    def orDefaultOffset: Offset = x.getOrElse(Offset(0))
  }

  implicit class PaginationResultLimitOps(x: Option[Limit]) {
      def orDefaultLimit: Limit = x.getOrElse(Limit(50L))
  }
}
