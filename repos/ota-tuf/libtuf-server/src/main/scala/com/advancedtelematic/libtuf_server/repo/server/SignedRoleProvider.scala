package com.advancedtelematic.libtuf_server.repo.server

import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole

import scala.concurrent.Future

trait SignedRoleProvider {
  def find[T : TufRole](repoId: RepoId): Future[SignedRole[T]]

  def persistAll(repoId: RepoId, roles: List[SignedRole[_]]): Future[List[SignedRole[_]]]
}
