package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1, Directives}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.NamespaceDirectives
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.tuf.reposerver.db.RepoNamespaceRepositorySupport
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import slick.jdbc.MySQLProfile.api._


abstract class NamespaceValidation(val extractor: Directive1[Namespace]) {
  def apply(repoId: RepoId): Directive1[Namespace]
}

class DatabaseNamespaceValidation(extractor: Directive1[Namespace])
                                 (implicit val ec: ExecutionContext, val db: Database)
  extends NamespaceValidation(extractor) with RepoNamespaceRepositorySupport {

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
