package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.director.db.deviceregistry.DeviceRepository.findByDeviceIdQuery
import com.advancedtelematic.director.db.deviceregistry.GroupMemberRepository.{
  addDeviceToDynamicGroups,
  deleteDynamicGroupsForDevice
}
import com.advancedtelematic.director.deviceregistry.data.DataType.{TagInfo, TaggedDevice}
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.{Device, DeviceDB, TagId}
import com.advancedtelematic.director.http.deviceregistry.Errors
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickAnyVal.stringAnyValSerializer
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext

object TaggedDeviceRepository {

  class TaggedDeviceTable(tag: Tag) extends Table[TaggedDevice](tag, "TaggedDevice") {
    def namespace = column[Namespace]("namespace")
    def deviceUuid = column[DeviceId]("device_uuid")
    def tagId = column[TagId]("tag_id")(validatedStringMapper)
    def tagValue = column[String]("tag_value")

    def * = (namespace, deviceUuid, tagId, tagValue).shaped <> (
      (TaggedDevice.apply _).tupled,
      TaggedDevice.unapply
    )

    def pk = primaryKey("tagged_device_pk", (deviceUuid, tagId))
  }

  val taggedDevices = TableQuery[TaggedDeviceTable]

  private def isTagDelible(namespace: Namespace, tagId: TagId)(
    implicit ec: ExecutionContext): DBIO[Boolean] =
    GroupInfoRepository
      .findSmartGroupsUsingTag(namespace, tagId)
      .map(_.map(_._2.droppingTag(tagId)))
      .map(_.forall(_.isDefined))

  def fetchAll(namespace: Namespace)(implicit ec: ExecutionContext): DBIO[Seq[TagInfo]] =
    for {
      tagIds <- taggedDevices.filter(_.namespace === namespace).map(_.tagId).distinct.result
      tagIdsAndDelibles <- DBIO.sequence {
        tagIds.map(tagId => isTagDelible(namespace, tagId).map(tagId -> _))
      }
    } yield tagIdsAndDelibles.map((TagInfo.apply _).tupled)

  def fetchForDevice(deviceUuid: DeviceId): DBIO[Seq[(TagId, String)]] =
    taggedDevices
      .filter(_.deviceUuid === deviceUuid)
      .map(td => td.tagId -> td.tagValue)
      .result

  def fetchForDevices(deviceUuids: Seq[DeviceId]): DBIO[Seq[(DeviceId, (TagId, String))]] =
    taggedDevices
      .filter(_.deviceUuid.inSet(deviceUuids))
      .map(td => td.deviceUuid -> (td.tagId -> td.tagValue))
      .result

  def updateTagId(namespace: Namespace, tagId: TagId, newTagId: TagId): DBIO[Int] =
    taggedDevices
      .filter(_.namespace === namespace)
      .filter(_.tagId === tagId)
      .map(_.tagId)
      .update(newTagId)

  def delete(deviceUuid: DeviceId): DBIO[Int] =
    taggedDevices
      .filter(_.deviceUuid === deviceUuid)
      .delete

  def deleteTag(namespace: Namespace, tagId: TagId)(implicit ec: ExecutionContext): DBIO[Unit] = {
    val action = for {
      _ <- taggedDevices.filter(_.namespace === namespace).filter(_.tagId === tagId).delete
      expressions <- GroupInfoRepository.findSmartGroupsUsingTag(namespace, tagId)
      newExpressions = expressions.map { case (g, e) => g -> e.droppingTag(tagId) }
      _ <-
        if (newExpressions.exists(_._2.isEmpty)) {
          DBIO.failed(Errors.CannotRemoveDeviceTag)
        } else {
          DBIO.sequence {
            newExpressions.map { case (g, e) =>
              GroupMemberRepository.replaceExpression(namespace, g, e.get)
            }
          }
        }
    } yield ()
    action.transactionally
  }

  def tagDeviceByOemId(namespace: Namespace, deviceId: DeviceOemId, tags: Map[TagId, String])(
    implicit ec: ExecutionContext): DBIO[Unit] = {
    val action = for {
      d <- findByDeviceIdQuery(namespace, deviceId).result.failIfNotSingle(Errors.MissingDevice)
      _ <- refreshDeviceTags(namespace, DeviceDB.toDevice(d), tags)
    } yield ()
    action.transactionally
  }

  def updateDeviceTagValue(namespace: Namespace,
                           deviceId: DeviceId,
                           tagId: TagId,
                           tagValue: String)(implicit ec: ExecutionContext): DBIO[Unit] = {
    val action = for {
      d <- DeviceRepository.exists(namespace, deviceId)
      currentTags <- fetchForDevice(deviceId).map(_.toMap)
      newTags = currentTags + (tagId -> tagValue)
      _ <- refreshDeviceTags(namespace, d, newTags)
    } yield ()
    action.transactionally
  }

  private[db] def refreshDeviceTags(namespace: Namespace, device: Device, tags: Map[TagId, String])(
    implicit ec: ExecutionContext): DBIO[Unit] = {
    val action = for {
      _ <- setDeviceTags(namespace, device.uuid, tags)
      _ <- deleteDynamicGroupsForDevice(device.uuid)
      _ <- addDeviceToDynamicGroups(namespace, device, tags)
    } yield ()
    action.transactionally
  }

  private def setDeviceTags(ns: Namespace, deviceUuid: DeviceId, tags: Map[TagId, String])(
    implicit ec: ExecutionContext): DBIO[Unit] = {
    val action = for {
      _ <- taggedDevices
        .filterNot(_.tagId.inSet(tags.keys))
        .filter(_.deviceUuid === deviceUuid)
        .delete
      _ <- DBIO.sequence(
        tags
          .map { case (tid, tv) => TaggedDevice(ns, deviceUuid, tid, tv) }
          .map(taggedDevices.insertOrUpdate)
      )
    } yield ()
    action.transactionally
  }

}
