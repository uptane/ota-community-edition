package com.advancedtelematic.director.deviceregistry.data

import java.time.Instant
import cats.Show
import com.advancedtelematic.director.data.ClientDataType.TagSearchParam
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, ResultCode}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier, Event}
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceMetricsObservation
import com.advancedtelematic.director.deviceregistry.data.CredentialsType.CredentialsType
import com.advancedtelematic.director.deviceregistry.data.DataType.IndexedEventType.IndexedEventType
import com.advancedtelematic.director.deviceregistry.data.Device.{DeviceOemId, DeviceType}
import com.advancedtelematic.director.deviceregistry.data.DeviceSortBy.DeviceSortBy
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.director.deviceregistry.data.SortDirection.SortDirection
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import enumeratum.EnumEntry
import enumeratum.EnumEntry.Camelcase
import io.circe.{Decoder, Encoder, Json}

import scala.concurrent.duration.Duration
import enumeratum.*

object DataType {

  case class IndexedEvent(device: DeviceId,
                          eventID: String,
                          eventType: IndexedEventType,
                          correlationId: Option[CorrelationId])

  case class InstallationStat(resultCode: ResultCode, total: Int, success: Boolean)

  object IndexedEventType extends Enumeration {
    type IndexedEventType = Value

    val DownloadComplete, EcuDownloadStarted, EcuDownloadCompleted, EcuInstallationStarted,
      EcuInstallationApplied, EcuInstallationCompleted, DevicePaused, DeviceResumed,
      CampaignAccepted, CampaignDeclined, CampaignPostponed, InstallationComplete = Value

  }

  object InstallationStatsLevel {
    sealed trait InstallationStatsLevel
    case object Device extends InstallationStatsLevel
    case object Ecu extends InstallationStatsLevel
  }

  final case class TaggedDevice(namespace: Namespace,
                                deviceUuid: DeviceId,
                                tagId: TagId,
                                tagValue: String)

  final case class RenameTagId(tagId: TagId)
  final case class UpdateTagValue(tagId: TagId, tagValue: String)
  final case class TagInfo(tagId: TagId, isDelible: Boolean)

  final case class DeviceT(uuid: Option[DeviceId] = None,
                           deviceName: DeviceName,
                           deviceId: DeviceOemId,
                           deviceType: DeviceType = DeviceType.Other,
                           credentials: Option[String] = None,
                           credentialsType: Option[CredentialsType] = None,
                           hibernated: Option[Boolean] = Some(false))

  final case class SetDevice(deviceName: DeviceName, notes: Option[String] = None)

  final case class UpdateDevice(deviceName: Option[DeviceName], notes: Option[String])

  final case class DeletedDevice(namespace: Namespace, uuid: DeviceId, deviceId: DeviceOemId)

  implicit val eventShow: Show[Event] = Show { event =>
    s"(device=${event.deviceUuid},eventId=${event.eventId},eventType=${event.eventType})"
  }

  final case class DeviceInstallationResult(correlationId: CorrelationId,
                                            resultCode: ResultCode,
                                            deviceId: DeviceId,
                                            success: Boolean,
                                            receivedAt: Instant,
                                            installationReport: Json) {

    // TODO: parse this when writing to db (migrate old rows)
    def description: Option[String] =
      installationReport.hcursor.downField("result").downField("description").as[String].toOption

  }

  final case class EcuInstallationResult(correlationId: CorrelationId,
                                         resultCode: ResultCode,
                                         deviceId: DeviceId,
                                         ecuId: EcuIdentifier,
                                         success: Boolean,
                                         description: Option[String])

  object SearchParams {

    def all(limit: Limit, offset: Offset) = SearchParams(
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      List.empty,
      Set.empty,
      Some(DeviceSortBy.CreatedAt),
      Some(SortDirection.Asc),
      offset,
      limit
    )

  }

  final case class DeviceCountParams(recentSince: Option[Duration], offlineSince: Option[Duration])

  final case class DeviceStatusCounts(recent: Long,
                                      hibernated: Long,
                                      offline: Long,
                                      updatePending: Long,
                                      updateInProgess: Long,
                                      updateFailed: Long,
                                      updateScheduled: Long)

  final case class SearchParams(oemId: Option[DeviceOemId],
                                grouped: Option[Boolean],
                                groupType: Option[GroupType],
                                groupId: Option[GroupId],
                                nameContains: Option[String],
                                notSeenSinceHours: Option[Int],
                                hibernated: Option[HibernationStatus],
                                status: Option[DeviceStatus],
                                activatedAfter: Option[Instant],
                                activatedBefore: Option[Instant],
                                lastSeenStart: Option[Instant],
                                lastSeenEnd: Option[Instant],
                                createdAtStart: Option[Instant],
                                createdAtEnd: Option[Instant],
                                hardwareId: Seq[HardwareIdentifier],
                                deviceTags:  Set[TagSearchParam],
                                sortBy: Option[DeviceSortBy],
                                sortDirection: Option[SortDirection],
                                offset: Offset,
                                limit: Limit) {

    if(deviceTags.nonEmpty) {
      require(
        oemId.isEmpty,
        "Invalid parameters: oemId must be empty when searching by deviceTags"
      )

      require(
        nameContains.isEmpty,
        "Invalid parameters: nameContains must be empty when searching by deviceTags"
      )

      require(
        grouped.isEmpty,
        "Invalid parameters: grouped must be empty when searching by deviceTags"
      )

      require(
        groupType.isEmpty,
        "Invalid parameters: groupType must be empty when searching by deviceTags"
      )

      require(
        hardwareId.isEmpty,
        "Invalid parameters: hardwareId must be empty when searching by deviceTags"
      )

      require(
        activatedAfter.isEmpty,
        "Invalid parameters: activatedAfter must be empty when searching by deviceTags"
      )

      require(
        activatedBefore.isEmpty,
        "Invalid parameters: activatedBefore must be empty when searching by deviceTags"
      )

      require(
        lastSeenStart.isEmpty,
        "Invalid parameters: lastSeenStart must be empty when searching by deviceTags"
      )

      require(
        lastSeenEnd.isEmpty,
        "Invalid parameters: lastSeenEnd must be empty when searching by deviceTags"
      )

    }

    if (oemId.isDefined) {
      require(
        groupId.isEmpty,
        "Invalid parameters: groupId must be empty when searching by deviceId."
      )
      require(
        nameContains.isEmpty,
        "Invalid parameters: nameContains must be empty when searching by deviceId."
      )
      require(
        notSeenSinceHours.isEmpty,
        "Invalid parameters: notSeenSinceHours must be empty when searching by deviceId."
      )
    }

  }

  case class PackageListItem(namespace: Namespace, packageId: PackageId, comment: String)
  case class PackageListItemCount(packageId: PackageId, deviceCount: Int)

  case class DeviceUuids(deviceUuids: Seq[DeviceId])

  case class DevicesQuery(oemIds: Option[List[DeviceOemId]], deviceUuids: Option[List[DeviceId]])

  type HibernationStatus = Boolean

  case class UpdateHibernationStatusRequest(status: HibernationStatus)

  case class ObservationPublishResult(publishedSuccessfully: Boolean, msg: DeviceMetricsObservation)

  sealed trait MqttStatus extends EnumEntry with Camelcase

  object MqttStatus extends Enum[MqttStatus] {

    val values = findValues

    case object Online extends MqttStatus
    case object Offline extends MqttStatus
    case object NotSeen extends MqttStatus
  }

  implicit val mqttStatusEncoder: Encoder[MqttStatus] = Circe.encoder(MqttStatus)
  implicit val mqttStatusDecoder: Decoder[MqttStatus] = Circe.decoder(MqttStatus)
}
