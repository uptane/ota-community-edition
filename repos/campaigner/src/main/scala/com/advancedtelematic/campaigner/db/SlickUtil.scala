package com.advancedtelematic.campaigner.db

import com.advancedtelematic.campaigner.data.DataType.SortBy
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.campaigner.db.Schema.{CampaignsTable, UpdatesTable}
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import slick.dbio.DBIO
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Future
import scala.language.implicitConversions


object SlickUtil {
  implicit class DBIOActionToFutureOps[T](value: DBIO[T]) {
    def run(implicit db: Database): Future[T] = db.run(value)
  }

  implicit def sortBySlickOrderedCampaignConversion(sortBy: SortBy): CampaignsTable => slick.lifted.Ordered =
    sortBy match {
      case SortBy.Name => table => table.name.asc
      case SortBy.CreatedAt => table => table.createdAt.desc
    }

  implicit def sortBySlickOrderedUpdateConversion(sortBy: SortBy): UpdatesTable => slick.lifted.Ordered =
    sortBy match {
      case SortBy.Name => table => table.name.asc
      case SortBy.CreatedAt => table => table.createdAt.desc
    }
}
