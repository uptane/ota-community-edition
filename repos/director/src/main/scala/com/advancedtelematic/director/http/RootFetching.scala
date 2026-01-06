package com.advancedtelematic.director.http

import com.advancedtelematic.director.db.{
  AdminRolesRepositorySupport,
  RepoNamespaceRepositorySupport
}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, SignedPayload}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

trait RootFetching {
  self: RepoNamespaceRepositorySupport & AdminRolesRepositorySupport =>

  val keyserverClient: KeyserverClient

  def fetchRoot(ns: Namespace, version: Option[Int]): Future[SignedPayload[RootRole]] = {
    val fetchFn = version
      .map(v => (r: RepoId, _: Option[Instant]) => keyserverClient.fetchRootRole(r, v))
      .getOrElse((r: RepoId, i: Option[Instant]) =>
        keyserverClient.fetchRootRole(r, expiresNotBefore = i)
      )

    for {
      repoId <- repoNamespaceRepo.findFor(ns)
      latestExpiringRole <- adminRolesRepository.findLatestExpireDate(repoId)
      latestExpire = latestExpiringRole.map(_.plus(180, ChronoUnit.DAYS))
      root <- fetchFn(repoId, latestExpire)
    } yield root
  }

}
