/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.db

import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.ota.deviceregistry.common.Errors
import com.advancedtelematic.ota.deviceregistry.common.Errors.MemberAlreadyExists
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import com.advancedtelematic.ota.deviceregistry.data.{Device, GroupExpression, GroupExpressionAST, GroupType, TagId}
import com.advancedtelematic.ota.deviceregistry.db.DbOps.PaginationResultOps
import slick.jdbc.MySQLProfile.api._
import slick.lifted.Tag

import scala.concurrent.ExecutionContext
import scala.util.Failure

object GroupMemberRepository {

  final case class GroupMember(groupId: GroupId, deviceUuid: DeviceId)

  // scalastyle:off
  class GroupMembersTable(tag: Tag)
      extends Table[GroupMember](tag, "GroupMembers") {
    def groupId    = column[GroupId]("group_id")
    def deviceUuid = column[DeviceId]("device_uuid")

    def pk = primaryKey("pk_group_members", (groupId, deviceUuid))

    def * =
      (groupId, deviceUuid) <>
      ((GroupMember.apply _).tupled, GroupMember.unapply)
  }
  // scalastyle:on

  val groupMembers = TableQuery[GroupMembersTable]

  //this method assumes that groupId and deviceId belong to the same namespace
  def addGroupMember(groupId: GroupId, deviceId: DeviceId)(implicit ec: ExecutionContext): DBIO[Int] =
    (groupMembers += GroupMember(groupId, deviceId))
      .handleIntegrityErrors(Errors.MemberAlreadyExists)

  def removeGroupMember(groupId: GroupId, deviceId: DeviceId)
                       (implicit ec: ExecutionContext): DBIO[Unit] =
    groupMembers
      .filter(r => r.groupId === groupId && r.deviceUuid === deviceId)
      .delete
      .handleSingleUpdateError(Errors.MissingGroup)

  def removeAllGroupMembers(groupId: GroupId)(implicit ec: ExecutionContext): DBIO[Int] =
    groupMembers.filter(_.groupId === groupId).delete

  def removeDeviceFromAllGroups(deviceUuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Int] =
    groupMembers
      .filter(_.deviceUuid === deviceUuid)
      .delete

  def listDevicesInGroup(groupId: GroupId, offset: Option[Long], limit: Option[Long])
                        (implicit db: Database, ec: ExecutionContext): DBIO[PaginationResult[DeviceId]] =
    listDevicesInGroupAction(groupId, offset, limit)

  def listDevicesInGroupAction(groupId: GroupId, offset: Option[Long], limit: Option[Long])
                              (implicit ec: ExecutionContext): DBIO[PaginationResult[DeviceId]] =
    groupMembers
      .filter(_.groupId === groupId)
      .map(_.deviceUuid)
      .paginateResult(offset.orDefaultOffset, limit.orDefaultLimit)

  def countDevicesInGroup(
      groupId: GroupId
  )(implicit ec: ExecutionContext): DBIO[Long] =
    listDevicesInGroupAction(groupId, None, None).map(_.total)

  def deleteDynamicGroupsForDevice(deviceUuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Unit] =
    groupMembers
      .filter(_.deviceUuid === deviceUuid)
      .filter { _.groupId.in(GroupInfoRepository.groupInfos.filter(_.groupType === GroupType.dynamic).map(_.id)) }
      .delete
      .map(_ => ())

  def addDeviceToDynamicGroups(namespace: Namespace, device: Device, tags: Map[TagId, String])(implicit ec: ExecutionContext): DBIO[Unit] = {
    val dynamicGroupIds =
      GroupInfoRepository.groupInfos
        .filter(_.namespace === namespace)
        .filter(_.groupType === GroupType.dynamic)
        .result
        .map(_.filter { group =>
          GroupExpressionAST.compileToScala(group.expression.get)(device, tags)
        })

    dynamicGroupIds.flatMap { groups =>
      DBIO.sequence(
        groups.map { g =>
          GroupMemberRepository
            .addGroupMember(g.id, device.uuid)
            .recover { case Failure(MemberAlreadyExists) => DBIO.successful(0) }
      })
    }.map(_ => ())
  }

  private[db] def addDeviceToDynamicGroups(namespace: Namespace, device: Device)(implicit ec: ExecutionContext): DBIO[Unit] =
    for {
      tags <- TaggedDeviceRepository.fetchForDevice(device.uuid)
      _ <- GroupMemberRepository.addDeviceToDynamicGroups(namespace, device, tags.toMap)
    } yield ()

  def listGroupsForDevice(deviceUuid: DeviceId, offset: Option[Long], limit: Option[Long])
                         (implicit ec: ExecutionContext): DBIO[PaginationResult[GroupId]] =
    groupMembers
      .filter(_.deviceUuid === deviceUuid)
      .map(_.groupId)
      .paginateResult(offset.orDefaultOffset, limit.orDefaultLimit)

  private[db] def replaceExpression(namespace: Namespace, groupId: GroupId, newExpression: GroupExpression)
                                   (implicit ec: ExecutionContext): DBIO[Unit] =
    for {
      _ <- GroupInfoRepository.updateSmartGroupExpression(groupId, newExpression)
      _ <- groupMembers.filter(_.groupId === groupId).delete
      devs <- DeviceRepository.devices.filter(_.namespace === namespace).result
      _ <- DBIO.sequence(devs.map(GroupMemberRepository.addDeviceToDynamicGroups(namespace, _)))
    } yield ()
}
