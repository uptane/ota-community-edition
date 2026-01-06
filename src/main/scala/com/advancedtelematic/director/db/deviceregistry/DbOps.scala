package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import com.advancedtelematic.director.deviceregistry.data.DeviceSortBy.DeviceSortBy
import com.advancedtelematic.director.deviceregistry.data.GroupSortBy.GroupSortBy
import com.advancedtelematic.director.deviceregistry.data.{DeviceSortBy, GroupSortBy, SortDirection}
import com.advancedtelematic.director.deviceregistry.data.SortDirection.SortDirection
import Schema.DeviceTable
import GroupInfoRepository.GroupInfoTable
import com.advancedtelematic.libats.data.PaginationResult.{Offset, Limit}
import slick.ast.Ordering
import slick.jdbc.MySQLProfile.api.*
import slick.lifted.ColumnOrdered

object DbOps {

  implicit class SortBySlickOrderedGroupConversion(sortBy: GroupSortBy) {

    def orderedConv(): GroupInfoTable => slick.lifted.Ordered = sortBy match {
      case GroupSortBy.Name      => table => table.groupName.asc
      case GroupSortBy.CreatedAt => table => table.createdAt.desc
    }

  }

  private def slickDirection(direction: SortDirection): Ordering.Direction =
    direction match {
      case SortDirection.Asc  => Ordering.Asc
      case SortDirection.Desc => Ordering.Desc
    }

  implicit class deviceTableToSlickOrder(d: DeviceTable) {

    def ordered(sortBy: DeviceSortBy, sortDirection: SortDirection): slick.lifted.Ordered =
      sortBy match {
        case DeviceSortBy.Name =>
          ColumnOrdered(d.deviceName, Ordering(slickDirection(sortDirection)))
        case DeviceSortBy.DeviceId =>
          ColumnOrdered(d.oemId, Ordering(slickDirection(sortDirection)))
        case DeviceSortBy.Uuid => ColumnOrdered(d.id, Ordering(slickDirection(sortDirection)))
        case DeviceSortBy.CreatedAt =>
          ColumnOrdered(d.createdAt, Ordering(slickDirection(sortDirection)))
        case DeviceSortBy.ActivatedAt =>
          ColumnOrdered(d.activatedAt, Ordering(slickDirection(sortDirection)))
        case DeviceSortBy.LastSeen =>
          ColumnOrdered(d.lastSeen, Ordering(slickDirection(sortDirection)))
      }

  }

  implicit class groupTableToSlickOrder(g: GroupInfoTable) {

    def ordered(sortBy: GroupSortBy, sortDirection: SortDirection): slick.lifted.Ordered =
      sortBy match {
        case GroupSortBy.Name =>
          ColumnOrdered(g.groupName, Ordering(slickDirection(sortDirection)))
        case GroupSortBy.CreatedAt =>
          ColumnOrdered(g.createdAt, Ordering(slickDirection(sortDirection)))
      }

  }

}
