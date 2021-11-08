package com.advancedtelematic.director.http

import com.advancedtelematic.director.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, SignedPayload}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient

import scala.concurrent.Future

trait RootFetching {
  self: RepoNamespaceRepositorySupport =>

  val keyserverClient: KeyserverClient

  def fetchRoot(ns: Namespace, version: Option[Int]): Future[SignedPayload[RootRole]] = {
    val fetchFn = version
      .map(v => (r: RepoId) => keyserverClient.fetchRootRole(r, v))
      .getOrElse((r: RepoId) => keyserverClient.fetchRootRole(r))

    for {
      repoId <- repoNamespaceRepo.findFor(ns)
      root <- fetchFn(repoId)
    } yield root
  }

}
