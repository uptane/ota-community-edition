/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import com.advancedtelematic.director.db.deviceregistry.GroupMemberRepository.GroupMember
import com.advancedtelematic.director.db.deviceregistry.PublicCredentialsRepository.DevicePublicCredentials
import com.advancedtelematic.director.db.deviceregistry.SystemInfoRepository.SystemInfo
import com.advancedtelematic.director.deviceregistry.data.DataType.PackageListItem
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.director.deviceregistry.data.{
  DeviceName,
  Group,
  GroupExpression,
  GroupName
}
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{EntityAlreadyExists, MissingEntity, RawError}

import java.sql.SQLSyntaxErrorException

object Errors {

  import org.apache.pekko.http.scaladsl.model.StatusCodes

  object Codes {
    val MissingDevice = ErrorCode("missing_device")
    val ConflictingDevice = ErrorCode("conflicting_device")
    val ConflictingGroupName = ErrorCode("conflicting_device_group_name")
    val MemberAlreadyExists = ErrorCode("device_already_a_group_member")
    val RequestNeedsCredentials = ErrorCode("request_needs_credentials")
    val CannotAddDeviceToDynamicGroup = ErrorCode("cannot_add_device_to_dynamic_group")
    val CannotRemoveDeviceFromDynamicGroup = ErrorCode("cannot_remove_device_from_dynamic_group")
    val InvalidGroupExpressionForGroupType = ErrorCode("invalid_group_expression_for_group_type")
    val InvalidGroupExpression = ErrorCode("invalid_group_expression")
    val MalformedInput = ErrorCode("malformed_input")
    val CannotRemoveDeviceTag = ErrorCode("cannot_remove_device_tag")
    val CannotSerializeEcuReplacement = ErrorCode("cannot_serialize_ecu_replacement")
    val InvalidParameterFormat = ErrorCode("invalid_parameter_format")
  }

  def InvalidGroupExpression(err: String) = RawError(
    Codes.InvalidGroupExpression,
    StatusCodes.BadRequest,
    s"Invalid group expression: '$err'"
  )

  def InvalidGroupExpressionForGroupType(groupType: GroupType,
                                         expression: Option[GroupExpression]) =
    RawError(
      Codes.InvalidGroupExpressionForGroupType,
      StatusCodes.BadRequest,
      s"Invalid group expression $expression for group type $groupType"
    )

  val MissingDevice = RawError(Codes.MissingDevice, StatusCodes.NotFound, "device doesn't exist")

  def ConflictingDevice(deviceName: Option[DeviceName], deviceOemId: Option[DeviceOemId] = None) =
    RawError(
      Codes.ConflictingDevice,
      StatusCodes.Conflict,
      s"$deviceOemId or $deviceName is already in use"
    )

  def ConflictingGroupName(groupName: GroupName) =
    RawError(Codes.ConflictingGroupName, StatusCodes.Conflict, s"$groupName already in use")

  val MissingSystemInfo = MissingEntity[SystemInfo]()
  val ConflictingSystemInfo = EntityAlreadyExists[SystemInfo]()

  val MissingGroup = MissingEntity[Group]()
  val ConflictingGroup = EntityAlreadyExists[Group]()
  val MemberAlreadyExists = EntityAlreadyExists[GroupMember]()

  val MissingDevicePublicCredentials = MissingEntity[DevicePublicCredentials]()

  val RequestNeedsCredentials =
    RawError(
      Codes.RequestNeedsCredentials,
      StatusCodes.BadRequest,
      "request should contain credentials"
    )

  val CannotAddDeviceToDynamicGroup =
    RawError(
      Codes.CannotAddDeviceToDynamicGroup,
      StatusCodes.BadRequest,
      "cannot add device to dynamic group"
    )

  val CannotRemoveDeviceFromDynamicGroup =
    RawError(
      Codes.CannotRemoveDeviceFromDynamicGroup,
      StatusCodes.BadRequest,
      "cannot remove device from dynamic group"
    )

  val CannotRemoveDeviceTag =
    RawError(
      Codes.CannotRemoveDeviceTag,
      StatusCodes.BadRequest,
      "Cannot remove device tag because it's there is at least one smart group that uses only this tag in its expression."
    )

  val CannotSerializeEcuReplacement =
    RawError(
      Codes.CannotSerializeEcuReplacement,
      StatusCodes.InternalServerError,
      "Cannot serialize EcuReplacement because of wrong record."
    )

  val MalformedInputFile = RawError(
    Codes.MalformedInput,
    StatusCodes.BadRequest,
    "The file cannot be read because it is malformed."
  )

  val MissingPackageListItem = MissingEntity[PackageListItem]()
  val ConflictingPackageListItem = EntityAlreadyExists[PackageListItem]()

  val InvalidParameterFormat = RawError(
    Codes.InvalidParameterFormat,
    StatusCodes.BadRequest,
    "Request parameters have invalid format"
  )

}

object ErrorHandlers {

  val sqlExceptionHandler: PartialFunction[Throwable, Throwable] = {
    case e: SQLSyntaxErrorException =>
      if (e.getMessage().contains("Incorrect string value")) {
        Errors.InvalidParameterFormat
      } else {
        e
      }
    case e => e
  }

}
