package com.advancedtelematic.ota.deviceregistry.data

import java.time.Instant

import cats.Show
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, ResultCode}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, Event}
import com.advancedtelematic.ota.deviceregistry.data.CredentialsType.CredentialsType
import com.advancedtelematic.ota.deviceregistry.data.DataType.IndexedEventType.IndexedEventType
import com.advancedtelematic.ota.deviceregistry.data.Device.{DeviceOemId, DeviceType}
import com.advancedtelematic.ota.deviceregistry.data.Group.GroupId
import com.advancedtelematic.ota.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.ota.deviceregistry.data.SortBy.SortBy
import io.circe.Json

object DataType {
  case class IndexedEvent(device: DeviceId, eventID: String, eventType: IndexedEventType, correlationId: Option[CorrelationId])

  case class InstallationStat(resultCode: ResultCode, total: Int, success: Boolean)

  object IndexedEventType extends Enumeration {
    type IndexedEventType = Value

    val DownloadComplete,
        EcuDownloadStarted,
        EcuDownloadCompleted,
        EcuInstallationStarted,
        EcuInstallationApplied,
        EcuInstallationCompleted,
        DevicePaused,
        DeviceResumed,
        CampaignAccepted,
        CampaignDeclined,
        CampaignPostponed,
        InstallationComplete = Value
  }

  object InstallationStatsLevel {
    sealed trait InstallationStatsLevel
    case object Device extends InstallationStatsLevel
    case object Ecu extends InstallationStatsLevel
  }

  final case class TaggedDevice(namespace: Namespace, deviceUuid: DeviceId, tagId: TagId, tagValue: String)
  final case class RenameTagId(tagId: TagId)
  final case class UpdateTagValue(tagId: TagId, tagValue: String)
  final case class TagInfo(tagId: TagId, isDelible: Boolean)

  final case class DeviceT(uuid: Option[DeviceId] = None,
                           deviceName: DeviceName,
                           deviceId: DeviceOemId,
                           deviceType: DeviceType = DeviceType.Other,
                           credentials: Option[String] = None,
                           credentialsType: Option[CredentialsType] = None)

  final case class UpdateDevice(deviceName: DeviceName)

  final case class DeletedDevice(
    namespace: Namespace,
    uuid: DeviceId,
    deviceId: DeviceOemId)

  implicit val eventShow: Show[Event] = Show { event =>
    s"(device=${event.deviceUuid},eventId=${event.eventId},eventType=${event.eventType})"
  }

  final case class DeviceInstallationResult(correlationId: CorrelationId, resultCode: ResultCode, deviceId: DeviceId, success: Boolean, receivedAt: Instant, installationReport: Json)
  final case class EcuInstallationResult(correlationId: CorrelationId, resultCode: ResultCode, deviceId: DeviceId, ecuId: EcuIdentifier, success: Boolean)

  object SearchParams {
    def all(limit: Option[Long], offset: Option[Long]) = SearchParams(
      None,
      None,
      None,
      None,
      None,
      None,
      Some(SortBy.CreatedAt),
      offset,
      limit
    )
  }

  final case class SearchParams(oemId: Option[DeviceOemId],
                                grouped: Option[Boolean],
                                groupType: Option[GroupType],
                                groupId: Option[GroupId],
                                nameContains: Option[String],
                                notSeenSinceHours: Option[Int],
                                sortBy: Option[SortBy],
                                offset: Option[Long],
                                limit: Option[Long],
                               ) {
    if (oemId.isDefined) {
      require(groupId.isEmpty, "Invalid parameters: groupId must be empty when searching by deviceId.")
      require(nameContains.isEmpty, "Invalid parameters: nameContains must be empty when searching by deviceId.")
      require(notSeenSinceHours.isEmpty, "Invalid parameters: notSeenSinceHours must be empty when searching by deviceId.")
    }
  }

  case class PackageListItem(namespace: Namespace, packageId: PackageId, comment: String)
  case class PackageListItemCount(packageId: PackageId, deviceCount: Int)

  case class DeviceUuids(deviceUuids: Seq[DeviceId])
}
