package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.data.DataType.{DeltaId, ObjectId, SuperBlockHash}
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{Error, RawError}

object ErrorCodes {
  val CommitMissing = ErrorCode("commit_missing")
  val BlobNotFound = ErrorCode("blob_missing")
  val ObjectNotFound = ErrorCode("object_missing")
  val ObjectAExists = ErrorCode("object_exists")
  val StaticDeltaDoesNotExist = ErrorCode("delta_missing")
  val OutOfBandStorageNotSupported = ErrorCode("out_of_band_storage_not_supported")
  val TimeoutOnWaiting = ErrorCode("timeout_on_waiting")
  val MissingSuperblockHash = ErrorCode("missing_superblock_hash")
  val StaticDeltaExists = ErrorCode("static_delta_exists")
  val StaticDeltaNotUploaded = ErrorCode("static_delta_not_uploaded")
}

object Errors {
  case class ObjectExists(id: ObjectId) extends Error(ErrorCodes.ObjectAExists, StatusCodes.Conflict, s"$id already exists")
  case class StaticDeltaExists(id: DeltaId, superblockHash: SuperBlockHash) extends Error(ErrorCodes.StaticDeltaExists, StatusCodes.Conflict,
    s"A static delta with id $id already exists and the hash provided for this request ($superblockHash) does not match that superblock")

  val CommitMissing = RawError(ErrorCodes.CommitMissing, StatusCodes.PreconditionFailed, "Commit does not exist")
  val BlobNotFound =  RawError(ErrorCodes.BlobNotFound, StatusCodes.NotFound, "object blob not stored")
  def ObjectNotFound(id: ObjectId) = RawError(ErrorCodes.ObjectNotFound, StatusCodes.NotFound, s"object not found: $id")
  val StaticDeltaDoesNotExist = RawError(ErrorCodes.StaticDeltaDoesNotExist, StatusCodes.NotFound, "delta does not exist")
  val OutOfBandStorageNotSupported = RawError(ErrorCodes.OutOfBandStorageNotSupported, StatusCodes.BadRequest, "out of band storage not supported for Local storage")
  val MissingSuperblockHash = RawError(ErrorCodes.MissingSuperblockHash, StatusCodes.BadRequest, "missing/invalid superblock hash in headers. The request needs to contain the sha256 hash in hex format of the static delta superblock in the `x-trx-superblock-hash` header")
  val StaticDeltaNotUploaded = RawError(ErrorCodes.StaticDeltaNotUploaded, StatusCodes.RetryWith, "A static delta with the requested id exists, but is not yet fully uploaded. the static delta will be available after the superblock is uploaded")
}
