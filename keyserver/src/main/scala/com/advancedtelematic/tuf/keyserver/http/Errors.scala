package com.advancedtelematic.tuf.keyserver.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.libats.http.Errors.{JsonError, RawError}
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenId
import io.circe.syntax._
import com.advancedtelematic.libtuf.data.ErrorCodes
import com.advancedtelematic.libats.http.Errors.Error

object Errors {
  val KeysNotReady = RawError(ErrorCodes.KeyServer.KeysNotReady, StatusCodes.Locked, "A key generation request exists")
  val RepoRootKeysNotFound = RawError(ErrorCodes.KeyServer.RepoRootKeysNotFound, StatusCodes.NotFound, "Repository root keys not available/offline")
  val RoleKeysNotFound = RawError(ErrorCodes.KeyServer.RoleKeysNotFound, StatusCodes.NotFound, "There are no keys for this repoid/roletype")
  val PrivateKeysNotFound = RawError(ErrorCodes.KeyServer.PrivateKeysNotFound, StatusCodes.PreconditionFailed, "There are no private keys for that role")

  def KeyGenerationFailed(repoId: RepoId, errors: Map[KeyGenId, String]) =
    JsonError(ErrorCodes.KeyServer.KeyGenerationFailed, StatusCodes.InternalServerError, errors.asJson, "Could not generate keys")
}
