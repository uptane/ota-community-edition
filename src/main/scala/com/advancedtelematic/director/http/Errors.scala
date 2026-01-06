package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import cats.Show
import cats.data.NonEmptyList
import com.advancedtelematic.director.data.DataType.AdminRoleName
import com.advancedtelematic.director.data.DbDataType.{EcuTargetId, HardwareUpdate}
import com.advancedtelematic.libats.data.DataType.CorrelationId
import com.advancedtelematic.libats.data.{EcuIdentifier, ErrorCode}
import com.advancedtelematic.libats.http.Errors.{Error, JsonError, MissingEntityId, RawError}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import io.circe.Encoder
import io.circe.syntax.*

object ErrorCodes {
  val PrimaryIsNotListedForDevice = ErrorCode("primary_is_not_listed_for_device")

  val DeviceMissingPrimaryEcu = ErrorCode("device_missing_primary_ecu")

  object Manifest {
    val EcuNotPrimary = ErrorCode("ecu_not_primary")
    val SignatureNotValid = ErrorCode("signature_not_valid")
  }

  val ReplaceEcuAssignmentExists = ErrorCode("replace_ecu_assignment_exists")
  val EcuReuseError = ErrorCode("ecu_reuse_not_allowed")
  val EcuReplacementDisabled = ErrorCode("ecu_replacement_disabled")

  val MissingAdminRole = ErrorCode("missing_admin_role")

  val NotAffectedRunningAssignment = ErrorCode("not_affected_running_assignment")

  val InstalledTargetIsUpdate = ErrorCode("installed_target_is_update")

  val DeviceNoCompatibleHardware = ErrorCode("device_no_compatible_hardware")

  val NotAffectedByMtu = ErrorCode("not_affected_by_mtu")

  val InvalidMtu = ErrorCode("invalid_mtu")

  val InvalidAssignment = ErrorCode("invalid_assignment")

  val TooManyOfflineRoles = ErrorCode("too_many_offline_roles")

  val InvalidSignedPayload = ErrorCode("invalid_signed_payload")

  val UpdateScheduleError = ErrorCode("update_schedule_error")

  val DeviceHasScheduledUpdate = ErrorCode("update_already_scheduled_error")
}

object Errors {

  val PrimaryIsNotListedForDevice = RawError(
    ErrorCodes.PrimaryIsNotListedForDevice,
    StatusCodes.BadRequest,
    "The given primary ecu isn't part of ecus for the device"
  )

  case class NotAffectedRunningAssignment(deviceId: DeviceId, ecuIdentifier: EcuIdentifier)
      extends Error(
        ErrorCodes.NotAffectedRunningAssignment,
        StatusCodes.BadRequest,
        s"$deviceId/$ecuIdentifier not affected because ecu has a running assignment"
      )

  case class InstalledTargetIsUpdate(deviceId: DeviceId,
                                     ecuIdentifier: EcuIdentifier,
                                     update: HardwareUpdate)
      extends Error(
        ErrorCodes.InstalledTargetIsUpdate,
        StatusCodes.BadRequest,
        s"Ecu $deviceId/$ecuIdentifier not affected for $update, installed target is already the target update"
      )

  case class DeviceNoCompatibleHardware(deviceId: DeviceId, mtuId: UpdateId)
      extends Error(
        ErrorCodes.DeviceNoCompatibleHardware,
        StatusCodes.BadRequest,
        s"$deviceId not affected for $mtuId, device does not have any ecu with compatible hardware"
      )

  case class DeviceHasScheduledUpdate(deviceId: DeviceId, mtuId: UpdateId)
      extends Error(
        ErrorCodes.DeviceHasScheduledUpdate,
        StatusCodes.BadRequest,
        s"$deviceId not affected for $mtuId, there is an update scheduled for the device"
      )

  case class NotAffectedByMtu(deviceId: DeviceId, ecuIdentifier: EcuIdentifier, mtuId: UpdateId)
      extends Error(
        ErrorCodes.NotAffectedByMtu,
        StatusCodes.BadRequest,
        s"ecu $deviceId$ecuIdentifier not affected by $mtuId"
      )

  case class InvalidAssignment(targetId: EcuTargetId, correlationId: CorrelationId)
      extends Error(
        ErrorCodes.InvalidAssignment,
        StatusCodes.InternalServerError,
        msg =
          s"No ECU Target exists matching the ecu target id (${targetId}) specified in assignment"
      )

  case class InvalidMtu(_msg: String)
      extends Error(ErrorCodes.InvalidMtu, StatusCodes.BadRequest, "Invalid MTU: " + _msg)

  case class MissingAdminRole(repoId: RepoId, name: AdminRoleName)
      extends Error(
        ErrorCodes.MissingAdminRole,
        StatusCodes.NotFound,
        s"admin role $repoId/$name not found"
      )

  def DeviceMissingPrimaryEcu(deviceId: DeviceId) = RawError(
    ErrorCodes.DeviceMissingPrimaryEcu,
    StatusCodes.NotFound,
    s"This server does not have a primary ecu for $deviceId"
  )

  case class AssignmentExistsError(deviceId: DeviceId)
      extends Error(
        ErrorCodes.ReplaceEcuAssignmentExists,
        StatusCodes.PreconditionFailed,
        s"Cannot replace ecus for $deviceId, the device has running assignments"
      )

  def EcuReplacementDisabled(deviceId: DeviceId) =
    RawError(
      ErrorCodes.EcuReplacementDisabled,
      StatusCodes.Conflict,
      s"Device $deviceId is trying to register again but ecu replacement is disabled on this instance"
    )

  def EcusReuseError(deviceId: DeviceId, ecuIds: Seq[EcuIdentifier]) =
    RawError(
      ErrorCodes.EcuReuseError,
      StatusCodes.Conflict,
      s"At least one ecu in ${ecuIds.mkString(", ")} was already used and removed for $deviceId and cannot be reused"
    )

  def InvalidVersionBumpError(oldVersion: Int, newVersion: Int, roleType: RoleType) =
    RawError(
      ErrorCode("invalid_version_bump"),
      StatusCodes.Conflict,
      s"Cannot bump version from $oldVersion to $newVersion for $roleType"
    )

  private val showDeviceIdRoleTypeTuple: Show[(DeviceId, TufRole[_])] = Show.show {
    case (did, tufRole) => s"No tuf role ${tufRole.metaPath} found for $did"
  }

  def SignedRoleNotFound[T](deviceId: DeviceId)(implicit ev: TufRole[T]) =
    MissingEntityId[(DeviceId, TufRole[_])](deviceId -> ev)(
      ct = implicitly,
      show = showDeviceIdRoleTypeTuple
    )

  object Manifest {

    val EcuNotPrimary = RawError(
      ErrorCodes.Manifest.EcuNotPrimary,
      StatusCodes.BadRequest,
      "The claimed primary ECU is not the primary ECU for the device"
    )

    def SignatureNotValid(err: String) = RawError(
      ErrorCodes.Manifest.SignatureNotValid,
      StatusCodes.BadRequest,
      s"The given signature is not valid: $err"
    )

  }

  def AssignmentInFlight(deviceId: DeviceId) =
    RawError(
      ErrorCode("assignment_in_flight"),
      StatusCodes.Conflict,
      s"there is an assignmement in flight for $deviceId"
    )

  def TooManyOfflineRoles(max: Int) =
    RawError(
      ErrorCodes.TooManyOfflineRoles,
      StatusCodes.BadRequest,
      s"this account has too many offline roles. Maximum is set to $max roles"
    )

  def InvalidSignedPayload[E: Encoder](repoId: RepoId, reasons: NonEmptyList[E]) =
    JsonError(
      ErrorCodes.InvalidSignedPayload,
      StatusCodes.BadRequest,
      reasons.asJson,
      msg = s"Invalid signed payload received for $repoId: ${reasons.toList.mkString(", ")}"
    )

  def UpdateScheduleError[E: Encoder](deviceId: DeviceId, reasons: NonEmptyList[E]) =
    JsonError(
      ErrorCodes.UpdateScheduleError,
      StatusCodes.BadRequest,
      reasons.asJson,
      msg = "Invalid ecu status for scheduled update"
    )

}
