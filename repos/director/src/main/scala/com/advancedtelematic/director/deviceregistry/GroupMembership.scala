package com.advancedtelematic.director.deviceregistry

import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.db.deviceregistry.{GroupInfoRepository, GroupMemberRepository, SearchDBIO}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.director.deviceregistry.data.{Group, GroupExpression, GroupName, GroupType}
import com.advancedtelematic.director.http.deviceregistry.Errors
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId

protected trait GroupMembershipOperations {
  def addMember(groupId: GroupId, deviceId: DeviceId): Future[Unit]
  def removeMember(group: Group, deviceId: DeviceId): Future[Unit]
}

protected class DynamicMembership(implicit db: Database, ec: ExecutionContext)
    extends GroupMembershipOperations {

  def create(groupId: GroupId,
             name: GroupName,
             namespace: Namespace,
             expression: GroupExpression): Future[GroupId] = db.run {
    GroupInfoRepository
      .create(groupId, name, namespace, GroupType.dynamic, Some(expression))
      .andThen {
        SearchDBIO.searchByExpression(namespace, expression).flatMap { devices =>
          DBIO.sequence(devices.map(d => GroupMemberRepository.addGroupMember(groupId, d)))
        }
      }
      .map(_ => groupId)
      .transactionally
  }

  override def addMember(groupId: GroupId, deviceId: DeviceId): Future[Unit] =
    FastFuture.failed(Errors.CannotAddDeviceToDynamicGroup)

  override def removeMember(group: Group, deviceId: DeviceId): Future[Unit] =
    FastFuture.failed(Errors.CannotRemoveDeviceFromDynamicGroup)

}

protected class StaticMembership(implicit db: Database, ec: ExecutionContext)
    extends GroupMembershipOperations {

  override def addMember(groupId: GroupId, deviceId: DeviceId): Future[Unit] =
    db.run(GroupMemberRepository.addGroupMember(groupId, deviceId)).map(_ => ())

  override def removeMember(group: Group, deviceId: DeviceId): Future[Unit] =
    db.run(GroupMemberRepository.removeGroupMember(group.id, deviceId))

  def create(groupId: GroupId, name: GroupName, namespace: Namespace): Future[GroupId] = db.run {
    GroupInfoRepository.create(groupId, name, namespace, GroupType.static, expression = None)
  }

}

class GroupMembership(implicit val db: Database, ec: ExecutionContext) {

  private def runGroupOperation[T](groupId: GroupId)(
    fn: (Group, GroupMembershipOperations) => Future[T]): Future[T] =
    GroupInfoRepository.findById(groupId).flatMap {
      case g if g.groupType == GroupType.static => fn(g, new StaticMembership())
      case g                                    => fn(g, new DynamicMembership())
    }

  def create(name: GroupName,
             namespace: Namespace,
             groupType: GroupType,
             expression: Option[GroupExpression]): Future[GroupId] =
    (groupType, expression) match {
      case (GroupType.static, None) =>
        new StaticMembership().create(GroupId.generate(), name, namespace)
      case (GroupType.dynamic, Some(exp)) =>
        new DynamicMembership().create(GroupId.generate(), name, namespace, exp)
      case (GroupType.static, exp) =>
        FastFuture.failed(Errors.InvalidGroupExpressionForGroupType(groupType, exp))
      case (GroupType.dynamic, None) =>
        FastFuture.failed(Errors.InvalidGroupExpressionForGroupType(groupType, None))
      case (_, _) =>
        throw new IllegalArgumentException(s"(groupType, expression) = ($groupType, $expression)")
    }

  def listDevices(groupId: GroupId,
                  offset: Offset,
                  limit: Limit): Future[PaginationResult[DeviceId]] =
    db.run(GroupMemberRepository.listDevicesInGroup(groupId, offset, limit))

  def addGroupMember(groupId: GroupId, deviceId: DeviceId): Future[Unit] =
    runGroupOperation(groupId) { (g, m) =>
      m.addMember(g.id, deviceId)
    }

  def countDevices(groupId: GroupId): Future[Long] =
    db.run(GroupMemberRepository.countDevicesInGroup(Set(groupId))).map { counts =>
      counts.getOrElse(groupId, 0)
    }

  def countDevicesPerGroup(groupIds: Set[GroupId]): Future[Map[GroupId, Long]] =
    db.run(GroupMemberRepository.countDevicesInGroup(groupIds))

  def removeGroupMember(groupId: GroupId, deviceId: DeviceId): Future[Unit] =
    runGroupOperation(groupId) { (g, m) =>
      m.removeMember(g, deviceId)
    }

}
