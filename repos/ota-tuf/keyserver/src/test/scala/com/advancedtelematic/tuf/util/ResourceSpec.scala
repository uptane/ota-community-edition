package com.advancedtelematic.tuf.util

import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.advancedtelematic.tuf.keyserver.http.TufKeyserverRoutes
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture

import scala.concurrent.duration._
import akka.testkit.TestDuration
import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, KeyType, RepoId, RsaKeyType}
import com.advancedtelematic.tuf.keyserver.daemon.DefaultKeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.Key
import com.advancedtelematic.tuf.keyserver.db.{KeyGenRequestSupport, KeyRepositorySupport}
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.{Future, Promise}

trait LongHttpRequest {
  implicit def default(implicit system: ActorSystem) =
    RouteTestTimeout(15.seconds.dilated(system))
}

trait HttpClientSpecSupport {
  self: ResourceSpec =>

  def testHttpClient(req: HttpRequest): Future[HttpResponse] = {
    val p = Promise[HttpResponse]()
    req ~> Route.seal(routes) ~> check { p.success(response) }
    p.future
  }
}

trait RootGenerationSpecSupport {
  self: ResourceSpec with KeyGenRequestSupport =>

  private val keyGenerationOp = DefaultKeyGenerationOp()

  def processKeyGenerationRequest(repoId: RepoId): Future[Seq[Key]] = {
    keyGenRepo.findBy(repoId).flatMap { ids ⇒
      Future.sequence {
        ids.map(_.id).map { id =>
          keyGenRepo
            .find(id)
            .flatMap(keyGenerationOp)
        }
      }.map(_.flatten)
    }
  }
}

trait KeyTypeSpecSupport {
  self: AnyFunSuite =>

  def keyTypeTest(name: String)(fn: KeyType => Any)(implicit pos: Position): Unit = {
    test(name + " Ed25519")(fn(Ed25519KeyType))
    test(name + " RSA")(fn(RsaKeyType))
  }
}

trait ResourceSpec extends TufKeyserverSpec
  with ScalatestRouteTest
  with MysqlDatabaseSpec
  with LongHttpRequest {
  def apiUri(path: String): String = "/api/v1/" + path

  lazy val routes = new TufKeyserverRoutes().routes
}
