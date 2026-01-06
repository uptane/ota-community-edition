package com.advancedtelematic.treehub.db

object DbOps {
  implicit class PaginationResultOps(x: Option[Long]) {
    def orDefaultOffset: Long = x.getOrElse(0L)

    def orDefaultLimit: Long = x.getOrElse(50L)
  }
}
