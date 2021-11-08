package com.advancedtelematic.director.http

import com.advancedtelematic.director.db.{DeviceRepositorySupport, RepoNamespaceRepositorySupport}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, RepoId}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.http.UUIDKeyAkka._

import scala.concurrent.{ExecutionContext, Future}

class RepositoryCreation(keyserverClient: KeyserverClient)(implicit val db: Database, val ec: ExecutionContext)
  extends DeviceRepositorySupport with RepoNamespaceRepositorySupport {

  def create(ns: Namespace): Future[Unit] = {
    val repoId = RepoId.generate()

    for {
      _ <- keyserverClient.createRoot(repoId, Ed25519KeyType, forceSync = true)
      _ <- repoNamespaceRepo.persist(repoId, ns)
    } yield ()
  }
}
