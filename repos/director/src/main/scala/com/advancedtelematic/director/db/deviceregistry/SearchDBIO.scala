package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.director.data.ClientDataType.TagSearchOps
import com.advancedtelematic.director.db
import com.advancedtelematic.director.db.deviceregistry.DbOps.deviceTableToSlickOrder
import com.advancedtelematic.director.db.deviceregistry.GroupInfoRepository.groupInfos
import com.advancedtelematic.director.db.deviceregistry.GroupMemberRepository.groupMembers
import com.advancedtelematic.director.db.deviceregistry.Schema.*
import com.advancedtelematic.director.db.deviceregistry.SlickMappings.*
import com.advancedtelematic.director.db.deviceregistry.TaggedDeviceRepository.taggedDevices
import com.advancedtelematic.director.deviceregistry.data.*
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceCountParams,
  DeviceStatusCounts,
  SearchParams
}
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.{SlickAnyVal, SlickUUIDKey}
import slick.jdbc.GetResult
import slick.jdbc.MySQLProfile.api.*
import slick.lifted.Rep

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object SearchDBIO {

  private def devicesForExpressionQuery(ns: Namespace, expression: GroupExpression) = {
    val all = devices.filter(_.namespace === ns).map(_.id)
    GroupExpressionAST
      .compileToSlick(expression)(Schema.devices, TaggedDeviceRepository.taggedDevices)(all)
      .distinct
  }

  def searchByExpression(ns: Namespace, expression: GroupExpression): DBIO[Seq[DeviceId]] =
    devicesForExpressionQuery(ns, expression).result

  def countDevicesForExpression(ns: Namespace, expression: GroupExpression): DBIO[Int] =
    devicesForExpressionQuery(ns, expression).length.result

  private def optionalFilter[T](o: Option[T])(
    fn: (DeviceTable, T) => Rep[Boolean]): DeviceTable => Rep[Boolean] =
    dt =>
      o match {
        case None    => true.bind
        case Some(t) => fn(dt, t)
      }

  private def searchQuery(ns: Namespace,
                          nameContains: Option[String],
                          groupId: Option[GroupId],
                          notSeenSinceHours: Option[Int]) = {

    val groupFilter = optionalFilter(groupId) { (dt, gid) =>
      dt.id.in(groupMembers.filter(_.groupId === gid).map(_.deviceUuid))
    }

    val nameContainsFilter = optionalFilter(nameContains) { (dt, s) =>
      dt.deviceName.mappedTo[String].toLowerCase.like(s"%${s.toLowerCase}%")
    }

    val notSeenSinceFilter = optionalFilter(notSeenSinceHours) { (dt, h) =>
      dt.lastSeen.map(i => i < Instant.now.minus(h, ChronoUnit.HOURS)).getOrElse(true.bind)
    }

    devices
      .filter(_.namespace === ns)
      .filter(groupFilter)
      .filter(nameContainsFilter)
      .filter(notSeenSinceFilter)
  }

  private def searchQueryByName(ns: Namespace,
                                query: Query[DeviceTable, DeviceDB, Seq],
                                nameContains: Option[String]) = {
    val deviceIdsByName = searchQuery(ns, nameContains, None, None).map(_.id)
    query.filter(_.id in deviceIdsByName)
  }

  private val groupedDevicesQuery
    : (Namespace, Option[GroupType]) => Query[DeviceTable, DeviceDB, Seq] = (ns, groupType) =>
    groupInfos
      .maybeFilter(_.groupType === groupType)
      .filter(_.namespace === ns)
      .join(groupMembers)
      .on(_.id === _.groupId)
      .join(devices)
      .on(_._2.deviceUuid === _.id)
      .map(_._2)
      .distinct

  private def applyCommonSearchFiltersTo(params: SearchParams,
                                         query: Query[DeviceTable, DeviceDB, Seq])(
    implicit ec: ExecutionContext): DBIO[PaginationResult[DeviceDB]] = {
    val sortBy = params.sortBy.getOrElse(DeviceSortBy.Name)
    val sortDirection = params.sortDirection.getOrElse(SortDirection.Asc)

    val activatedAfterFilter = optionalFilter(params.activatedAfter) { (dt, from) =>
      dt.activatedAt.map(i => i >= from).getOrElse(false.bind)
    }

    val activatedBeforeFilter = optionalFilter(params.activatedBefore) { (dt, to) =>
      dt.activatedAt.map(i => i < to).getOrElse(false.bind)
    }

    val lastSeenStartFilter = optionalFilter(params.lastSeenStart) { (dt, lastSeen) =>
      dt.lastSeen.map(i => i > lastSeen).getOrElse(false.bind)
    }

    val lastSeenEndFilter = optionalFilter(params.lastSeenEnd) { (dt, lastSeen) =>
      dt.lastSeen.map(i => i < lastSeen).getOrElse(false.bind)
    }

    val hardwareIdFilter: DeviceTable => Rep[Boolean] = params.hardwareId match {
      case x :: xs =>
        dt => {
          val hardwareIdsQuery =
            db.Schema.activeEcus.filter(_.hardwareId.inSet(x :: xs)).map(_.deviceId)
          dt.id.in(hardwareIdsQuery)
        }
      case _ =>
        _ => true.bind
    }

    query
      .maybeFilter(r => r.deviceStatus === params.status)
      .maybeFilter(_.hibernated === params.hibernated)
      .maybeFilter(_.createdAt > params.createdAtStart)
      .maybeFilter(_.createdAt < params.createdAtEnd)
      .filter(activatedAfterFilter)
      .filter(activatedBeforeFilter)
      .filter(lastSeenStartFilter)
      .filter(lastSeenEndFilter)
      .filter(hardwareIdFilter)
      .sortBy(devices => devices.ordered(sortBy, sortDirection))
      .paginateResult(params.offset, params.limit)
  }

  def search(ns: Namespace, params: SearchParams)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[DeviceDB]] = {
    params match {

      case SearchParams(
            Some(oemId),
            _,
            _,
            None,
            None,
            None,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) =>
        applyCommonSearchFiltersTo(params, DeviceRepository.findByDeviceIdQuery(ns, oemId))

      case SearchParams(
            None,
            Some(true),
            gt,
            None,
            nameContains,
            None,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) =>
        applyCommonSearchFiltersTo(
          params,
          searchQueryByName(ns, groupedDevicesQuery(ns, gt), nameContains)
        )

      case SearchParams(
            None,
            Some(false),
            gt,
            None,
            nameContains,
            None,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) =>
        val ungroupedDevicesQuery =
          devices.filterNot(_.id.in(groupedDevicesQuery(ns, gt).map(_.id)))
        applyCommonSearchFiltersTo(
          params,
          searchQueryByName(ns, ungroupedDevicesQuery, nameContains)
        )

      case SearchParams(None, _, _, _, _, _, _, _, _, _, _, _, _, _, _, deviceTags, _, _, _, _)
          if deviceTags.nonEmpty =>
        tagsSearch(ns, params)

      case SearchParams(
            None,
            _,
            _,
            gid,
            nameContains,
            notSeenSinceHours,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) =>
        applyCommonSearchFiltersTo(params, searchQuery(ns, nameContains, gid, notSeenSinceHours))

      case _ => throw new IllegalArgumentException("Invalid parameter combination.")
    }
  }

  private def tagsSearch(ns: Namespace, params: SearchParams)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[DeviceDB]] = {

    trait SearchQuery {
      type Result
      val query: String
      val limit: Limit
      val offset: Offset
      implicit val getResult: GetResult[Result]
    }

    object Count extends SearchQuery {
      override type Result = Long
      override val query: String = "SELECT COUNT(*)"
      override val limit: Limit = Limit(Long.MaxValue)
      override val offset: Offset = Offset(0)
      override implicit val getResult: GetResult[Long] = GetResult.GetLong
    }

    object Results extends SearchQuery {
      override type Result = DeviceDB
      override val query: String = """SELECT DISTINCT uuid, device_name, device_id, device_type,
                    last_seen, devices.created_at, activated_at, device_status, notes,
                    hibernated, mqtt_status, mqtt_last_seen"""
      override val limit: Limit = params.limit
      override val offset: Offset = params.offset
      override implicit val getResult: GetResult[DeviceDB] = pr =>
        DeviceDB(
          ns,
          SlickUUIDKey.dbMapping[DeviceId].getValue(pr.rs, 1),
          SlickAnyVal.stringAnyValSerializer[DeviceName].getValue(pr.rs, 2),
          SlickAnyVal.stringAnyValSerializer[DeviceOemId].getValue(pr.rs, 3),
          SlickMappings.deviceTypeMaper.getValue(pr.rs, 4),
          Option(javaInstantMapping.getValue(pr.rs, 5)),
          javaInstantMapping.getValue(pr.rs, 6),
          Option(javaInstantMapping.getValue(pr.rs, 7)),
          SlickMappings.deviceStatusColumnType.getValue(pr.rs, 8),
          Option(pr.rs.getString(9)),
          pr.rs.getBoolean(10),
          SlickMappings.mqttStatusMapper.getValue(pr.rs, 11),
          Option(javaInstantMapping.getValue(pr.rs, 12))
        )
    }

    def buildQuery(squery: SearchQuery) = {
      import squery.*

      // this is SQL injection safe only because we validate the parameters format
      val tags = params.deviceTags.map(t => s"('${t.tagId.value}','${t.tagValue}')").mkString(",")

      sql"""
             #${squery.query} FROM Device devices
             JOIN #${taggedDevices.baseTableRow.tableName} tags ON devices.uuid = tags.device_uuid
             WHERE
               devices.namespace = ${ns.get} AND
               (tags.tag_id, tags.tag_value) IN (#$tags)
             ORDER BY ${params.sortBy.map(_.columnName).getOrElse("uuid")} ${params.sortDirection
          .getOrElse(SortDirection.Asc)
          .toString}
             LIMIT ${squery.limit.value}
             OFFSET ${squery.offset.value}
        """.as[squery.Result]
    }

    buildQuery(Count).flatMap { count =>
      buildQuery(Results).map { d =>
        PaginationResult(d, count.headOption.getOrElse(0L), params.offset, params.limit)
      }
    }
  }

  def countByStatus(ns: Namespace, params: DeviceCountParams): DBIO[DeviceStatusCounts] = {
    val recentSince = params.recentSince.getOrElse(7.days).toSeconds
    val offlineSince = params.offlineSince.getOrElse(5.minutes).toSeconds

    implicit val getResult = GetResult[DeviceStatusCounts] { pr =>
      DeviceStatusCounts(
        pr.rs.getLong("recent"),
        pr.rs.getLong("hibernated"),
        pr.rs.getLong("offline"),
        pr.rs.getLong("update_pending"),
        pr.rs.getLong("update_in_progress"),
        pr.rs.getLong("update_failed"),
        pr.rs.getLong("update_scheduled")
      )
    }

    val io =
      sql"""
          select sum(hibernated) hibernated,
          sum(offline) offline,
          sum(recent) recent,
          sum(update_pending) update_pending,
          sum(update_in_progress) update_in_progress,
          sum(update_failed) update_failed,
          sum(update_scheduled) update_scheduled,
          count(*) total
      from (
              select hibernated,
                  IF(TIMESTAMPDIFF(SECOND, last_seen, NOW()) > $offlineSince, 1, 0) offline,
                  IF(TIMESTAMPDIFF(SECOND, created_at, NOW()) < $recentSince, 1, 0) recent,
                  IF(device_status = ${DeviceStatus.UpdatePending.toString},1,0) update_pending,
                  IF(device_status = ${DeviceStatus.Outdated.toString}, 1, 0) update_in_progress,
                  IF(device_status = ${DeviceStatus.Error.toString}, 1, 0) update_failed,
                  IF(device_status = ${DeviceStatus.UpdateScheduled.toString}, 1, 0) update_scheduled
              from Device
              where namespace = ${ns.get}
          ) s1;
        """.as[DeviceStatusCounts]

    // query guaranteed to return single row
    io.head
  }

}
