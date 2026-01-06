/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.director.http.deviceregistry.Errors.MemberAlreadyExists
import com.advancedtelematic.director.deviceregistry.data.DataType.HibernationStatus
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.{
  Device,
  DeviceDB,
  GroupExpression,
  GroupExpressionAST,
  GroupType,
  TagId
}
import DbOps.PaginationResultOps
import com.advancedtelematic.director.http.deviceregistry.Errors
import slick.jdbc.{PositionedParameters, SetParameter}
import slick.jdbc.MySQLProfile.api.*
import slick.lifted.Tag

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.util.Failure

object GroupMemberRepository {

  def setHibernationStatus(ns: Namespace, groupId: GroupId, status: HibernationStatus): DBIO[_] = {
    @unused
    implicit val setGroupId: SetParameter[GroupId] =
      (groupId: GroupId, pos: PositionedParameters) => pos.setString(groupId.uuid.toString)

    sql"""
            update Device d, #$tableName gm SET d.hibernated = $status WHERE
            d.uuid = gm.device_uuid AND gm.group_id = $groupId AND d.namespace = ${ns.get}
        """.asUpdate
  }

  final case class GroupMember(groupId: GroupId, deviceUuid: DeviceId)

  // scalastyle:off
  class GroupMembersTable(tag: Tag) extends Table[GroupMember](tag, "GroupMembers") {
    def groupId = column[GroupId]("group_id")
    def deviceUuid = column[DeviceId]("device_uuid")

    def pk = primaryKey("pk_group_members", (groupId, deviceUuid))

    def * =
      (groupId, deviceUuid) <>
        ((GroupMember.apply _).tupled, GroupMember.unapply)

  }

  // scalastyle:on
  val groupMembers = TableQuery[GroupMembersTable]

  val tableName = groupMembers.baseTableRow.tableName

  // this method assumes that groupId and deviceId belong to the same namespace
  def addGroupMember(groupId: GroupId, deviceId: DeviceId)(
    implicit ec: ExecutionContext): DBIO[Int] =
    (groupMembers += GroupMember(groupId, deviceId))
      .handleIntegrityErrors(Errors.MemberAlreadyExists)

  def removeGroupMember(groupId: GroupId, deviceId: DeviceId)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    groupMembers
      .filter(r => r.groupId === groupId && r.deviceUuid === deviceId)
      .delete
      .handleSingleUpdateError(Errors.MissingGroup)

  def removeAllGroupMembers(groupId: GroupId): DBIO[Int] =
    groupMembers.filter(_.groupId === groupId).delete

  def removeDeviceFromAllGroups(deviceUuid: DeviceId): DBIO[Int] =
    groupMembers
      .filter(_.deviceUuid === deviceUuid)
      .delete

  def listDevicesInGroup(groupId: GroupId, offset: Option[Long], limit: Option[Long])(
    implicit ec: ExecutionContext): DBIO[PaginationResult[DeviceId]] =
    listDevicesInGroupAction(groupId, offset, limit)

  def listDevicesInGroupAction(groupId: GroupId, offset: Option[Long], limit: Option[Long])(
    implicit ec: ExecutionContext): DBIO[PaginationResult[DeviceId]] =
    groupMembers
      .filter(_.groupId === groupId)
      .map(_.deviceUuid)
      .paginateResult(offset.orDefaultOffset, limit.orDefaultLimit)

  def countDevicesInGroup(groupIds: Set[GroupId])(
    implicit ec: ExecutionContext): DBIO[Map[GroupId, Long]] =
    groupMembers
      .filter(_.groupId.inSet(groupIds))
      .groupBy(_.groupId)
      .map { case (group, rows) => group -> rows.length }
      .result
      .map(_.map { case (group, size) => group -> size.toLong }.toMap)

  def deleteDynamicGroupsForDevice(deviceUuid: DeviceId)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    groupMembers
      .filter(_.deviceUuid === deviceUuid)
      .filter {
        _.groupId.in(
          GroupInfoRepository.groupInfos.filter(_.groupType === GroupType.dynamic).map(_.id)
        )
      }
      .delete
      .map(_ => ())

  def addDeviceToDynamicGroups(namespace: Namespace, device: Device, tags: Map[TagId, String])(
    implicit ec: ExecutionContext): DBIO[Unit] = {
    val dynamicGroupIds =
      GroupInfoRepository.groupInfos
        .filter(_.namespace === namespace)
        .filter(_.groupType === GroupType.dynamic)
        .result
        .map(_.filter { group =>
          GroupExpressionAST.compileToScala(group.expression.get)(device, tags)
        })

    dynamicGroupIds
      .flatMap { groups =>
        DBIO.sequence(groups.map { g =>
          GroupMemberRepository
            .addGroupMember(g.id, device.uuid)
            .recover { case Failure(MemberAlreadyExists) => DBIO.successful(0) }
        })
      }
      .map(_ => ())
  }

  private[db] def addDeviceToDynamicGroups(namespace: Namespace, device: Device)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    for {
      tags <- TaggedDeviceRepository.fetchForDevice(device.uuid)
      _ <- GroupMemberRepository.addDeviceToDynamicGroups(namespace, device, tags.toMap)
    } yield ()

  def listGroupsForDevice(deviceUuid: DeviceId, offset: Option[Long], limit: Option[Long])(
    implicit ec: ExecutionContext): DBIO[PaginationResult[GroupId]] =
    groupMembers
      .filter(_.deviceUuid === deviceUuid)
      .map(_.groupId)
      .paginateResult(offset.orDefaultOffset, limit.orDefaultLimit)

  def listGroupsForDevices(deviceUuids: Seq[DeviceId])(
    implicit ec: ExecutionContext): DBIO[Map[DeviceId, Seq[GroupId]]] = {
    val queryResult = groupMembers.filter(_.deviceUuid.inSet(deviceUuids)).result
    queryResult.map(_.groupBy(_.deviceUuid).map { case (deviceId, groups) =>
      deviceId -> groups.map(_.groupId)
    })
  }

  private[db] def replaceExpression(namespace: Namespace,
                                    groupId: GroupId,
                                    newExpression: GroupExpression)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    for {
      _ <- GroupInfoRepository.updateSmartGroupExpression(groupId, newExpression)
      _ <- groupMembers.filter(_.groupId === groupId).delete
      devs <- Schema.devices.filter(_.namespace === namespace).result
      _ <- DBIO.sequence(
        devs
          .map(DeviceDB.toDevice(_))
          .map(GroupMemberRepository.addDeviceToDynamicGroups(namespace, _))
      )
    } yield ()

}
