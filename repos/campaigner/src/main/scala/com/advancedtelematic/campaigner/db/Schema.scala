package com.advancedtelematic.campaigner.db

import java.time.Instant

import com.advancedtelematic.campaigner.data.DataType.CampaignStatus.CampaignStatus
import com.advancedtelematic.campaigner.data.DataType.CancelTaskStatus.CancelTaskStatus
import com.advancedtelematic.campaigner.data.DataType.DeviceStatus.DeviceStatus
import com.advancedtelematic.campaigner.data.DataType.MetadataType.MetadataType
import com.advancedtelematic.campaigner.data.DataType.UpdateType.UpdateType
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.SlickMapping._
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import slick.jdbc.MySQLProfile.api._
import slick.lifted.ProvenShape


object Schema {
  class CampaignsTable(tag: Tag) extends Table[Campaign](tag, "campaigns") {
    def namespace = column[Namespace] ("namespace")
    def id        = column[CampaignId]("uuid", O.PrimaryKey)
    def name      = column[String]    ("name")
    def status = column[CampaignStatus]("status")
    def update    = column[UpdateId]  ("update_uuid")
    def mainCampaignId = column[Option[CampaignId]]("parent_campaign_uuid")
    def failureCode = column[Option[ResultCode]]("failure_code")
    def autoAccept = column[Boolean]   ("auto_accept")
    def createdAt = column[Instant]   ("created_at")
    def updatedAt = column[Instant]   ("updated_at")

    def updateForeignKey = foreignKey("update_fk", update, updates)(_.uuid)

    override def * = (namespace, id, name, update, status, createdAt, updatedAt, mainCampaignId, failureCode, autoAccept) <>
      ((Campaign.apply _).tupled, Campaign.unapply)
  }

  protected [db] val campaigns = TableQuery[CampaignsTable]

  class CampaignMetadataTable(tag: Tag) extends Table[CampaignMetadata](tag, "campaign_metadata") {
    def campaignId = column[CampaignId]("campaign_id")
    def metadataType = column[MetadataType]("type")
    def value = column[String]("value")

    def pk = primaryKey("campaign_metadata_pk", (campaignId, metadataType))

    override def * = (campaignId, metadataType, value) <> ((CampaignMetadata.apply _).tupled, CampaignMetadata.unapply)
  }

  protected [db] val campaignMetadata = TableQuery[CampaignMetadataTable]

  // There is already an association between campaigns and groups in GroupStatsTable. Why do we need this?
  // If it's just for the campaign resource, we can have a new GroupStatus => created and create that when we create
  // a campaign
  class CampaignGroupsTable(tag: Tag) extends Table[(CampaignId, GroupId)](tag, "campaign_groups") {
    def campaignId = column[CampaignId]("campaign_id")
    def groupId    = column[GroupId]("group_id")

    def pk = primaryKey("campaign_groups_pk", (campaignId, groupId))

    override def * = (campaignId, groupId)
  }

  protected [db] val campaignGroups = TableQuery[CampaignGroupsTable]


  class DeviceUpdatesTable(tag: Tag) extends Table[DeviceUpdate](tag, "device_updates") {
    def campaignId = column[CampaignId]("campaign_id")
    def updateId   = column[UpdateId]("update_id")
    def deviceId   = column[DeviceId]("device_id")
    def status     = column[DeviceStatus]("status")
    def resultCode = column[Option[ResultCode]]("result_code")
    def resultDescription = column[Option[ResultDescription]]("result_description")
    def updatedAt  = column[Instant]("updated_at")

    def pk = primaryKey("device_updates_pk", (campaignId, deviceId))

    override def * = (campaignId, updateId, deviceId, status, resultCode, resultDescription, updatedAt) <>
                     ((DeviceUpdate.apply _).tupled, DeviceUpdate.unapply)
  }

  protected [db] val deviceUpdates = TableQuery[DeviceUpdatesTable]


  class CancelTaskTable(tag: Tag) extends Table[CancelTask](tag, "campaign_cancels") {
    def campaignId = column[CampaignId]("campaign_id", O.PrimaryKey)
    def taskStatus = column[CancelTaskStatus]("status")

    override def * = (campaignId, taskStatus) <>
      ((CancelTask.apply _).tupled, CancelTask.unapply)
  }
  protected [db] val cancelTasks = TableQuery[CancelTaskTable]


  type UpdatesTableRow = (UpdateId, ExternalUpdateId, UpdateType, Namespace, String, Option[String], Instant, Instant)

  class UpdatesTable(tag: Tag) extends Table[Update](tag, "updates"){
    def uuid = column[UpdateId]("uuid", O.PrimaryKey)
    def updateId = column[ExternalUpdateId]("update_id")
    def updateSourceType = column[UpdateType]("update_source_type")
    def namespace = column[Namespace]("namespace")
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def uniqueUpdateId = index("unique_update_id", (namespace, updateId), unique = true)

    private def fromRow(row: UpdatesTableRow): Update = Update(row._1, UpdateSource(row._2, row._3), row._4, row._5, row._6, row._7, row._8)

    private def toRow(update: Update): Option[UpdatesTableRow] =
      Some((update.uuid, update.source.id, update.source.sourceType, update.namespace, update.name, update.description, update.createdAt, update.updatedAt))


    override def * : ProvenShape[Update] = (uuid, updateId, updateSourceType, namespace, name, description, createdAt, updatedAt) <> (fromRow, toRow)
  }
  protected [db] val updates = TableQuery[UpdatesTable]

}
