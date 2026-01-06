package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.model.{StatusCode, StatusCodes, Uri}
import cats.data.NonEmptyList
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{JsonError, RawError}
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedRoleName
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import io.circe.syntax._

object ErrorCodes {
  val RoleKeysNotFound = ErrorCode("role_keys_not_found")
  val TargetNotFound = ErrorCode("target_not_found")
  val RoleChecksumNotProvided = ErrorCode("role_checksum_not_provided")
  val RoleChecksumMismatch = ErrorCode("role_checksum_mismatch")
  val NoRepoForNamespace = ErrorCode("no_repo_for_namespace")
  val NoUriForUnmanagedTarget = ErrorCode("no_uri_for_unmanaged_target")
  val DelegationNotFound = ErrorCode("delegations_not_found")
  val DelegationNotDefined = ErrorCode("delegations_not_defined")
  val InvalidTrustedDelegations = ErrorCode("trusted_delegations_invalid")
  val InvalidDelegationName = ErrorCode("invalid_delegation_role_name")
  val PayloadSignatureInvalid = ErrorCode("payload_signature_invalid")
  val InvalidOfflineTargets = ErrorCode("invalid_offline_targets")
  val RequestCanceledByUpstream = ErrorCode("request_canceled_by_upstream")
  val DelegationRemoteFetchFailed = ErrorCode("delegation_remote_fetch_failed")
  val DelegationRemoteParseFailed = ErrorCode("delegation_remote_parse_failed")
  val InvalidDelegatedTarget = ErrorCode("invalid_delegated_target")
  val MissingRemoteDelegationUri = ErrorCode("missing_remote_delegation_uri")
  val ImmutableFields = ErrorCode("immutable_fields_specified")
  val SetRootExpireError = ErrorCode("set_root_expire_failed")
}

object Errors {

  val DelegationNotDefined = RawError(
    ErrorCodes.DelegationNotDefined,
    StatusCodes.BadRequest,
    "Delegation is not defined in repository targets.json"
  )

  val DelegationNotFound =
    RawError(ErrorCodes.DelegationNotFound, StatusCodes.NotFound, "Delegation was not found")

  val RoleKeysNotFound = RawError(
    ErrorCodes.RoleKeysNotFound,
    StatusCodes.NotFound,
    "There are no keys for this repoid/roletype"
  )

  val TargetNotFoundError =
    RawError(ErrorCodes.TargetNotFound, StatusCodes.NotFound, "TargetNotFound")

  val NoUriForUnamanagedTarget = RawError(
    ErrorCodes.NoUriForUnmanagedTarget,
    StatusCodes.ExpectationFailed,
    "Cannot redirect to unmanaged resource, no known URI for this resource"
  )

  val RoleChecksumNotProvided = RawError(
    ErrorCodes.RoleChecksumNotProvided,
    StatusCodes.PreconditionRequired,
    "A targets role already exists, but no previous checksum was sent"
  )

  val RoleChecksumMismatch = RawError(
    ErrorCodes.RoleChecksumMismatch,
    StatusCodes.PreconditionFailed,
    "Provided checksum of previous role does not match current checksum"
  )

  def MissingRemoteDelegationUri(repoId: RepoId, delegationName: DelegatedRoleName) =
    RawError(
      ErrorCodes.MissingRemoteDelegationUri,
      StatusCodes.PreconditionFailed,
      s"Role $repoId/$delegationName does not have a remote uri and therefore cannot be refreshed"
    )

  def DelegationRemoteFetchFailed(uri: Uri, statusCode: StatusCode, status: String) =
    RawError(
      ErrorCodes.DelegationRemoteFetchFailed,
      StatusCodes.BadGateway,
      s"Could not get signed targets role from remote delegation service at $uri. Server responded with $statusCode/$status"
    )

  def DelegationRemoteParseFailed(uri: Uri, errorMsg: String) =
    RawError(
      ErrorCodes.DelegationRemoteParseFailed,
      StatusCodes.BadGateway,
      s"Could not parse delegations json from $uri. Error: $errorMsg"
    )

  def PayloadTooLarge(size: Long, max: Long) =
    RawError(
      com.advancedtelematic.libtuf.data.ErrorCodes.Reposerver.PayloadTooLarge,
      StatusCodes.ContentTooLarge,
      s"File being uploaded is too large ($size), maximum size is $max"
    )

  def FilePartTooLarge(size: Long, max: Long) =
    RawError(
      com.advancedtelematic.libtuf.data.ErrorCodes.Reposerver.PayloadTooLarge,
      StatusCodes.ContentTooLarge,
      s"Part of the file being uploaded is too large ($size), maximum part size for multipart upload is $max"
    )

  def PayloadSignatureInvalid(errors: NonEmptyList[String]) =
    JsonError(
      ErrorCodes.PayloadSignatureInvalid,
      StatusCodes.BadRequest,
      errors.asJson,
      "Invalid payload signature"
    )

  def InvalidOfflineTargets(errors: NonEmptyList[String]) =
    JsonError(
      ErrorCodes.InvalidOfflineTargets,
      StatusCodes.BadRequest,
      errors.asJson,
      "Invalid offline targets"
    )

  def InvalidTrustedDelegations(errors: NonEmptyList[String]) =
    JsonError(
      ErrorCodes.InvalidTrustedDelegations,
      StatusCodes.BadRequest,
      errors.asJson,
      "Invalid trusted delegations"
    )

  def InvalidDelegationName(errors: NonEmptyList[String]) =
    JsonError(
      ErrorCodes.InvalidDelegationName,
      StatusCodes.BadRequest,
      errors.asJson,
      "Invalid delegation name"
    )

  def InvalidDelegatedTarget(errors: NonEmptyList[String]) =
    JsonError(
      ErrorCodes.InvalidDelegatedTarget,
      StatusCodes.BadRequest,
      errors.asJson,
      "Invalid delegated target filename(s)"
    )

  case class RequestedImmutableFields(mutableFields: Seq[String], immutableFields: Seq[String])
      extends com.advancedtelematic.libats.http.Errors.Error(
        ErrorCodes.ImmutableFields,
        StatusCodes.BadRequest,
        s"Only allowed to manipulate field(s): ${mutableFields.toString()}, NOT: ${immutableFields.toString()}"
      )

  case class NotImplemented(message: String)
      extends com.advancedtelematic.libats.http.Errors.Error(
        com.advancedtelematic.libtuf.data.ErrorCodes.Reposerver.NotImplemented,
        StatusCodes.NotImplemented,
        message
      )

  case class NoRepoForNamespace(ns: Namespace)
      extends com.advancedtelematic.libats.http.Errors.Error(
        ErrorCodes.NoRepoForNamespace,
        StatusCodes.NotFound,
        s"No repository exists for namespace ${ns.get}"
      )

  case class RequestCanceledByUpstream(ex: Throwable)
      extends com.advancedtelematic.libats.http.Errors.Error(
        ErrorCodes.RequestCanceledByUpstream,
        StatusCodes.BadRequest,
        ex.getMessage,
        Some(ex)
      )

  case class SetRootExpire(ex: Throwable)
      extends com.advancedtelematic.libats.http.Errors.Error(
        ErrorCodes.SetRootExpireError,
        StatusCodes.BadGateway,
        "expire-not-before was set on reposerver roles but not on root.json. Check the attached cause and try again",
        cause = Option(ex)
      )

}
