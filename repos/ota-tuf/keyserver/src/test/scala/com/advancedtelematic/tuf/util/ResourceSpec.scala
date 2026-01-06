package com.advancedtelematic.tuf.util

import org.apache.pekko.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.advancedtelematic.tuf.keyserver.http.TufKeyserverRoutes
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.duration._
import org.apache.pekko.testkit.TestDuration
import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, KeyType, RepoId, RsaKeyType}
import com.advancedtelematic.tuf.keyserver.daemon.DefaultKeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.Key
import com.advancedtelematic.tuf.keyserver.db.KeyGenRequestSupport
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.{Future, Promise}

trait LongHttpRequest {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout =
    RouteTestTimeout(15.seconds.dilated(system))

}

trait HttpClientSpecSupport {
  self: ResourceSpec =>

  def testHttpClient(req: HttpRequest): Future[HttpResponse] = {
    val p = Promise[HttpResponse]()
    req ~> Route.seal(routes) ~> check(p.success(response))
    p.future
  }

}

trait RootGenerationSpecSupport {
  self: ResourceSpec with KeyGenRequestSupport =>

  private val keyGenerationOp = DefaultKeyGenerationOp()

  def processKeyGenerationRequest(repoId: RepoId): Future[Seq[Key]] =
    keyGenRepo.findBy(repoId).flatMap { ids =>
      Future
        .sequence {
          ids.map(_.id).map { id =>
            keyGenRepo
              .find(id)
              .flatMap(keyGenerationOp)
          }
        }
        .map(_.flatten)
    }

}

trait KeyTypeSpecSupport {
  self: AnyFunSuite =>

  def keyTypeTest(name: String)(fn: KeyType => Any)(implicit pos: Position): Unit = {
    test(name + " Ed25519")(fn(Ed25519KeyType))
    test(name + " RSA")(fn(RsaKeyType))
  }

}

trait ResourceSpec
    extends TufKeyserverSpec
    with ScalatestRouteTest
    with MysqlDatabaseSpec
    with LongHttpRequest {
  def apiUri(path: String): String = "/api/v1/" + path

  lazy val routes = new TufKeyserverRoutes().routes
}
