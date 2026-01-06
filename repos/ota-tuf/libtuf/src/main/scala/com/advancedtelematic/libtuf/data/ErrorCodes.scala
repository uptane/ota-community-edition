package com.advancedtelematic.libtuf.data

import com.advancedtelematic.libats.data.ErrorCode

object ErrorCodes {

  object KeyServer {
    val KeysNotReady = ErrorCode("keys_not_ready")
    val RoleKeysNotFound = ErrorCode("role_keys_not_found")
    val RepoRootKeysNotFound = ErrorCode("repo_root_keys_not_found")
    val PrivateKeysNotFound = ErrorCode("private_keys_not_found")
    val KeyGenerationFailed = ErrorCode("key_generation_failed")
    val InvalidRootRole = ErrorCode("invalid_root_role")
    val SignedRootRoleDecodingFailed = ErrorCode("signed_root_role_decoding_failed")
    val KeysOffline = ErrorCode("private_keys_offline")
    val KeysReadError = ErrorCode("private_keys_read_error")
  }

  object Reposerver {
    val PayloadTooLarge = ErrorCode("payload_too_large")
    val NotImplemented = ErrorCode("not_implemented")
  }

}
