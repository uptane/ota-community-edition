package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.server.Directives.onComplete
import org.apache.pekko.http.scaladsl.server.{
  AuthorizationFailedRejection,
  Directive,
  Directive1,
  Directives
}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.MissingEntity
import com.advancedtelematic.libats.http.NamespaceDirectives
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.tuf.reposerver.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.tuf.reposerver.http.Errors.NoRepoForNamespace
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api.*

import scala.util.{Failure, Success}

abstract class NamespaceValidation(val extractor: Directive1[Namespace]) {
  def apply(repoId: RepoId): Directive1[Namespace]
}

class DatabaseNamespaceValidation(extractor: Directive1[Namespace])(
  implicit val ec: ExecutionContext,
  val db: Database)
    extends NamespaceValidation(extractor)
    with RepoNamespaceRepositorySupport {

  import Directives._

  private val _log = LoggerFactory.getLogger(this.getClass)

  override def apply(repoId: RepoId): Directive1[Namespace] = extractor.flatMap { namespace =>
    onSuccess(repoNamespaceRepo.belongsTo(repoId, namespace)).flatMap {
      case true =>
        provide(namespace)
      case false =>
        _log.info(s"User not allowed for ($repoId, $namespace)")
        reject(AuthorizationFailedRejection)
    }
  }

}

object NamespaceValidation {
  private lazy val default: Directive1[Namespace] = NamespaceDirectives.defaultNamespaceExtractor

  def withDatabase(implicit ec: ExecutionContext, db: Database): NamespaceValidation =
    new DatabaseNamespaceValidation(default)

}

object UserRepoId {

  import Directives.{failWith, onComplete, provide}

  def apply(namespace: Namespace, findFn: Namespace => Future[RepoId]): Directive1[RepoId] =
    Directive.Empty.tflatMap { _ =>
      onComplete(findFn(namespace)).flatMap {
        case Success(repoId)              => provide(repoId)
        case Failure(_: MissingEntity[_]) => failWith(NoRepoForNamespace(namespace))
        case Failure(ex)                  => failWith(ex)
      }
    }

}

object NamespaceRepoId {
  import Directives.onSuccess

  def apply(namespaceValidation: NamespaceValidation, findNsRepoFn: Namespace => Future[RepoId]) =
    namespaceValidation.extractor.flatMap(ns => UserRepoId(ns, findNsRepoFn))

}
