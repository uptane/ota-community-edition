package com.advancedtelematic.campaigner.data

import java.time.Instant
import java.util.UUID

import cats.data.NonEmptyList
import com.advancedtelematic.campaigner.data.DataType.CampaignStatus.CampaignStatus
import com.advancedtelematic.campaigner.data.DataType.CancelTaskStatus.CancelTaskStatus
import com.advancedtelematic.campaigner.data.DataType.DeviceStatus.DeviceStatus
import com.advancedtelematic.campaigner.data.DataType.MetadataType.MetadataType
import com.advancedtelematic.campaigner.data.DataType.RetryStatus.RetryStatus
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.campaigner.data.DataType.UpdateType.UpdateType
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}

object DataType {

  final case class CampaignId(uuid: UUID) extends UUIDKey
  object CampaignId extends UUIDKeyObj[CampaignId]

  final case class GroupId(uuid: UUID) extends UUIDKey
  object GroupId extends UUIDKeyObj[GroupId]

  final case class Campaign(
    namespace: Namespace,
    id: CampaignId,
    name: String,
    updateId: UpdateId,
    status: CampaignStatus,
    createdAt: Instant,
    updatedAt: Instant,
    mainCampaignId: Option[CampaignId],
    failureCode: Option[ResultCode],
    autoAccept: Boolean = true
  )

  final case class ExternalUpdateId(value: String) extends AnyVal

  final case class UpdateSource(id: ExternalUpdateId, sourceType: UpdateType)

  final case class Update(
                           uuid: UpdateId,
                           source: UpdateSource,
                           namespace: Namespace,
                           name: String,
                           description: Option[String],
                           createdAt: Instant,
                           updatedAt: Instant
                         )

  final case class CreateUpdate(updateSource: UpdateSource, name: String, description: Option[String]) {
    def mkUpdate(ns: Namespace): Update =
      Update(UpdateId.generate(), updateSource, ns, name, description, Instant.now, Instant.now)
  }

  case class CampaignMetadata(campaignId: CampaignId, `type`: MetadataType, value: String)

  case class CreateCampaignMetadata(`type`: MetadataType, value: String) {
    def toCampaignMetadata(campaignId: CampaignId) = CampaignMetadata(campaignId, `type`, value)
  }

  final case class CreateCampaign(name: String,
                                  update: UpdateId,
                                  groups: NonEmptyList[GroupId],
                                  metadata: Option[Seq[CreateCampaignMetadata]] = None,
                                  approvalNeeded: Option[Boolean] = Some(false))
  {
    def mkCampaign(ns: Namespace): Campaign = {
      val now = Instant.now
      Campaign(
        ns,
        CampaignId.generate(),
        name,
        update,
        CampaignStatus.prepared,
        now,
        now,
        None,
        None,
        !approvalNeeded.getOrElse(false)
      )
    }

    def mkCampaignMetadata(campaignId: CampaignId): Seq[CampaignMetadata] =
      metadata.toList.flatten.map(_.toCampaignMetadata(campaignId))
  }

  final case class RetryFailedDevices(failureCode: ResultCode)

  final case class GetCampaign(
    namespace: Namespace,
    id: CampaignId,
    name: String,
    update: UpdateId,
    status: CampaignStatus,
    createdAt: Instant,
    updatedAt: Instant,
    mainCampaignId: Option[CampaignId],
    retryCampaignIds: Set[CampaignId],
    metadata: Seq[CreateCampaignMetadata],
    autoAccept: Boolean
  )

  object GetCampaign {
    def apply(c: Campaign, retryCampaignIds: Set[CampaignId], metadata: Seq[CampaignMetadata]): GetCampaign =
      GetCampaign(
        c.namespace,
        c.id,
        c.name,
        c.updateId,
        c.status,
        c.createdAt,
        c.updatedAt,
        c.mainCampaignId,
        retryCampaignIds,
        metadata.map(m => CreateCampaignMetadata(m.`type`, m.value)),
        c.autoAccept
      )
  }

  final case class UpdateCampaign(name: String, metadata: Option[Seq[CreateCampaignMetadata]] = None)

  final case class SearchCampaignParams(status: Option[CampaignStatus],
                                        nameContains: Option[String],
                                        withErrors: Option[Boolean],
                                        sortBy: SortBy,
                                        offset: Long,
                                        limit: Long,
                                       ) {
    if (withErrors.isDefined) {
      require(withErrors.get, "Invalid parameters: only value 'true' is supported for parameter 'withErrors'.")
      require(status.isEmpty, "Invalid parameters: 'status' must be empty when searching by 'withErrors'.")
      require(nameContains.isEmpty, "Invalid parameters: 'nameContains' must be empty when searching by 'withErrors'.")
    }
  }

  final case class CampaignStats(
    campaign: CampaignId,
    status: CampaignStatus,
    processed: Long,
    affected: Long,
    cancelled: Long,
    finished: Long,
    failed: Long,
    successful: Long,
    failures: Set[CampaignFailureStats],
  )

  final case class CampaignFailureStats(
    code: ResultCode,
    count: Long,
    retryStatus: RetryStatus,
  )

  final case class DeviceUpdate(
    campaign: CampaignId,
    update: UpdateId,
    device: DeviceId,
    status: DeviceStatus,
    resultCode: Option[ResultCode] = None,
    resultDescription: Option[ResultDescription] = None,
    updatedAt: Instant = Instant.now
  )

  final case class CancelTask(
    campaign: CampaignId,
    status: CancelTaskStatus
  )

  object SortBy {
    sealed trait SortBy
    case object Name      extends SortBy
    case object CreatedAt extends SortBy
  }

  object CampaignStatus extends Enumeration {
    type CampaignStatus = Value
    val prepared, launched, finished, cancelled = Value
  }

  object RetryStatus extends Enumeration {
    type RetryStatus = Value
    val not_launched, launched, finished = Value
  }

  /**
   * Status of a device in the campaign:
   * - `requested` when a device is initially added to the campaign (corresponds
   *   to `processed` state in the UI until a device goes to the Director)
   * - `rejected` when a device is rejected by Director and is not a part of the
   *    campaign anymore (corresponds to `not impacted` state in the UI)
   * - `scheduled` when a device is approved by Director and is scheduled for
   *    an update (partly corresponds to `queued` state in the UI)
   * - `accepted` when an update was accepted on a device and is about to be
   *    installed (partly corresponds to `queued` state in the UI)
   * - `successful` when a device update was applied successfully
   * - `cancelled` when a device update was cancelled
   * - `failed` when a device update was failed
   */
  object DeviceStatus extends Enumeration {
    type DeviceStatus = Value
    val requested, rejected, scheduled, accepted, successful, cancelled, failed = Value
  }

  object CancelTaskStatus extends Enumeration {
    type CancelTaskStatus = Value
    val error, pending, inprogress, completed = Value
  }

  object UpdateType extends Enumeration {
    type UpdateType = Value
    val external, multi_target = Value
  }

  object MetadataType extends Enumeration {
    type MetadataType = Value
    val DESCRIPTION, ESTIMATED_INSTALLATION_DURATION, ESTIMATED_PREPARATION_DURATION = Value
  }
}
