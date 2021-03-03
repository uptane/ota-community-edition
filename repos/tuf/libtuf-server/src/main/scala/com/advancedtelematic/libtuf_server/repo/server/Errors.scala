package com.advancedtelematic.libtuf_server.repo.server

import cats.Show
import com.advancedtelematic.libats.http.Errors.{MissingEntity, MissingEntityId}
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType

object Errors {
  implicit val showRepoIdError: Show[(RepoId, RoleType)] = Show.show { case (repoId: RepoId, roleType: RoleType) =>
    s"$repoId/$roleType"
  }

  def SignedRoleNotFound(repoId: RepoId, roleType: RoleType) = MissingEntityId[(RepoId, RoleType)](repoId -> roleType)
}
