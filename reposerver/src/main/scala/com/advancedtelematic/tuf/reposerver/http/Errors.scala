package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.model.StatusCodes
import cats.data.NonEmptyList
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{JsonError, RawError}
import io.circe.syntax._


object ErrorCodes {
  val RoleKeysNotFound = ErrorCode("role_keys_not_found")
  val TargetNotFound = ErrorCode("target_not_found")
  val RoleChecksumNotProvided = ErrorCode("role_checksum_not_provided")
  val RoleChecksumMismatch = ErrorCode("role_checksum_mismatch")
  val NoRepoForNamespace = ErrorCode("no_repo_for_namespace")
  val NoUriForUnmanagedTarget = ErrorCode("no_uri_for_unmanaged_target")
  val TooManyReposForNamespace = ErrorCode("too_many_repos_for_namespace")
  val DelegationNotFound = ErrorCode("delegations_not_found")
  val DelegationNotDefined = ErrorCode("delegations_not_defined")
  val InvalidTrustedDelegations = ErrorCode("trusted_delegations_invalid")
  val PayloadSignatureInvalid = ErrorCode("payload_signature_invalid")
  val InvalidOfflineTargets = ErrorCode("invalid_offline_targets")
  val RequestCanceledByUpstream = ErrorCode("request_canceled_by_upstream")
}

object Errors {
  val DelegationNotDefined = RawError(ErrorCodes.DelegationNotDefined, StatusCodes.BadRequest, "Delegation is not defined in repository targets.json")
  val DelegationNotFound = RawError(ErrorCodes.DelegationNotFound, StatusCodes.NotFound, "Delegation was not found")
  val RoleKeysNotFound = RawError(ErrorCodes.RoleKeysNotFound, StatusCodes.NotFound, "There are no keys for this repoid/roletype")
  val TargetNotFoundError = RawError(ErrorCodes.TargetNotFound, StatusCodes.NotFound, "TargetNotFound")
  val NoUriForUnamanagedTarget = RawError(ErrorCodes.NoUriForUnmanagedTarget, StatusCodes.ExpectationFailed, "Cannot redirect to unmanaged resource, no known URI for this resource")
  val RoleChecksumNotProvided = RawError(ErrorCodes.RoleChecksumNotProvided, StatusCodes.PreconditionRequired, "A targets role already exists, but no previous checksum was sent")
  val RoleChecksumMismatch = RawError(ErrorCodes.RoleChecksumMismatch, StatusCodes.PreconditionFailed, "Provided checksum of previous role does not match current checksum")
  val TooManyReposForNamespace = RawError(ErrorCodes.TooManyReposForNamespace, StatusCodes.BadRequest, "Too many repos found for this namespace. Use the /repo/:repo_id API instead")

  def PayloadTooLarge(size: Long, max: Long) =
    RawError(com.advancedtelematic.libtuf.data.ErrorCodes.Reposerver.PayloadTooLarge, StatusCodes.PayloadTooLarge,
      s"File being uploaded is too large ($size), maximum size is $max")

  def FilePartTooLarge(size: Long, max: Long) =
    RawError(com.advancedtelematic.libtuf.data.ErrorCodes.Reposerver.PayloadTooLarge, StatusCodes.PayloadTooLarge,
      s"Part of the file being uploaded is too large ($size), maximum part size for multipart upload is $max")

  def PayloadSignatureInvalid(errors: NonEmptyList[String]) =
    JsonError(ErrorCodes.PayloadSignatureInvalid, StatusCodes.BadRequest, errors.asJson, "Invalid payload signature")

  def InvalidOfflineTargets(errors: NonEmptyList[String]) =
    JsonError(ErrorCodes.InvalidOfflineTargets, StatusCodes.BadRequest, errors.asJson, "Invalid offline targets")

  def InvalidTrustedDelegations(errors: NonEmptyList[String]) =
    JsonError(ErrorCodes.InvalidTrustedDelegations, StatusCodes.BadRequest, errors.asJson, "Invalid trusted delegations")

  case class NotImplemented(message: String)
    extends com.advancedtelematic.libats.http.Errors.Error(com.advancedtelematic.libtuf.data.ErrorCodes.Reposerver.NotImplemented, StatusCodes.NotImplemented, message)

  case class NoRepoForNamespace(ns: Namespace)
    extends com.advancedtelematic.libats.http.Errors.Error(ErrorCodes.NoRepoForNamespace, StatusCodes.NotFound, s"No repository exists for namespace ${ns.get}")

  case class RequestCanceledByUpstream(ex: Throwable)
    extends com.advancedtelematic.libats.http.Errors.Error(ErrorCodes.RequestCanceledByUpstream, StatusCodes.BadRequest, ex.getMessage, Some(ex))
}
