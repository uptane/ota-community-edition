package com.advancedtelematic.director.http

import akka.http.scaladsl.server._
import com.advancedtelematic.director.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.MissingEntity
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

trait NamespaceRepoId {
  self: RepoNamespaceRepositorySupport =>

  import Directives._

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def UserRepoId(ns: Namespace): Directive1[RepoId] = {
    onComplete(repoNamespaceRepo.findFor(ns)).flatMap {
      case Success(repoId) => provide(repoId)
      case Failure(err) if err == repoNamespaceRepo.MissingRepoNamespace(ns) =>
        log.info(s"No repo for namespace $ns")
        reject(AuthorizationFailedRejection)

      case Failure(ex) => failWith(ex)
    }
  }

  def extractNamespaceRepoId(namespace: Directive1[Namespace]): Directive[(RepoId, Namespace)] = namespace.flatMap { ns =>
    UserRepoId(ns).map(_ -> ns)
  }
}
