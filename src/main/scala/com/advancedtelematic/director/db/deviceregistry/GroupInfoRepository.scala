/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.director.deviceregistry.data.{Group, GroupExpression, GroupName, GroupType, TagId}
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupSortBy.GroupSortBy

import java.time.Instant
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import com.advancedtelematic.director.deviceregistry.data
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import DbOps.SortBySlickOrderedGroupConversion
import SlickMappings.*
import com.advancedtelematic.director.http.deviceregistry.{ErrorHandlers, Errors}
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

object GroupInfoRepository {

  // scalastyle:off
  class GroupInfoTable(tag: Tag) extends Table[Group](tag, "DeviceGroup") {
    def id = column[GroupId]("id", O.PrimaryKey)
    def groupName = column[GroupName]("group_name")
    def namespace = column[Namespace]("namespace")
    def groupType = column[GroupType]("type")
    def expression = column[Option[GroupExpression]]("expression")
    def createdAt = column[Instant]("created_at")(javaInstantMapping)
    def updatedAt = column[Instant]("updated_at")(javaInstantMapping)

    def * = (id, groupName, namespace, createdAt, groupType, expression) <> (
      (Group.apply _).tupled,
      Group.unapply
    )

  }
  // scalastyle:on

  val groupInfos = TableQuery[GroupInfoTable]

  def list(namespace: Namespace,
           offset: Offset,
           limit: Limit,
           sortBy: GroupSortBy,
           nameContains: Option[String])(
    implicit ec: ExecutionContext): DBIO[PaginationResult[Group]] =
    groupInfos
      .filter(_.namespace === namespace)
      .maybeContains(_.groupName, nameContains)
      .paginateAndSortResult(sortBy.orderedConv(), offset, limit)

  def findById(id: GroupId)(implicit db: Database, ec: ExecutionContext): Future[Group] =
    db.run(findByIdAction(id))

  def findByIdAction(id: GroupId)(implicit ec: ExecutionContext): DBIO[Group] =
    groupInfos
      .filter(r => r.id === id)
      .result
      .failIfNotSingle(Errors.MissingGroup)

  def create(id: GroupId,
             groupName: GroupName,
             namespace: Namespace,
             groupType: GroupType,
             expression: Option[GroupExpression])(implicit ec: ExecutionContext): DBIO[GroupId] =
    (groupInfos += data.Group(id, groupName, namespace, Instant.now, groupType, expression))
      .handleIntegrityErrors(Errors.ConflictingGroup)
      .mapError(ErrorHandlers.sqlExceptionHandler)
      .map(_ => id)

  def deleteGroup(id: GroupId)(implicit ec: ExecutionContext): DBIO[Unit] =
    groupInfos
      .filter(_.id === id)
      .delete
      .handleSingleUpdateError(Errors.MissingGroup)

  def renameGroup(id: GroupId, newGroupName: GroupName)(implicit ec: ExecutionContext): DBIO[Unit] =
    groupInfos
      .filter(_.id === id)
      .map(_.groupName)
      .update(newGroupName)
      .handleIntegrityErrors(Errors.ConflictingGroupName(newGroupName))
      .handleSingleUpdateError(Errors.MissingGroup)

  def groupInfoNamespace(groupId: GroupId)(implicit ec: ExecutionContext): DBIO[Namespace] =
    groupInfos
      .filter(_.id === groupId)
      .map(_.namespace)
      .result
      .failIfNotSingle(Errors.MissingGroup)

  def renameTagIdInExpression(namespace: Namespace, tagId: TagId, newTagId: TagId): DBIO[Int] =
    sqlu"""
          UPDATE DeviceGroup
          SET expression = REPLACE(expression, 'tag(#${tagId.value})', 'tag(#${newTagId.value})')
          WHERE namespace = ${namespace.get} AND expression LIKE '%tag(#${tagId.value})%';
         """

  private[db] def findSmartGroupsUsingTag(namespace: Namespace,
                                          tagId: TagId): DBIO[Seq[(GroupId, GroupExpression)]] =
    groupInfos
      .filter(_.namespace === namespace)
      .filter(_.groupType === GroupType.dynamic)
      .filter(gi => gi.expression.mappedTo[String].like(s"%tag(${tagId.value})%"))
      .map(gi => gi.id -> gi.expression.get)
      .result

  private[db] def updateSmartGroupExpression(groupId: GroupId, expression: GroupExpression)(
    implicit ec: ExecutionContext): DBIO[Unit] =
    groupInfos
      .filter(_.groupType === GroupType.dynamic)
      .filter(_.id === groupId)
      .map(_.expression)
      .update(Some(expression))
      .map(_ => ())

}
