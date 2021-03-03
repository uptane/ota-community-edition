package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.prop.Whenever
import org.scalatest.{BeforeAndAfterAll, Inspectors}
import cats.syntax.show._
import com.advancedtelematic.libats.auth.NamespaceDirectives
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.data.Requests.{CommentRequest, TargetComment}
import com.advancedtelematic.libtuf_server.repo.client.ReposerverClient.RequestTargetItem
import com.advancedtelematic.tuf.reposerver.db.RepoNamespaceRepositorySupport
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.ExecutionContext
import com.advancedtelematic.tuf.reposerver.util.NamespaceSpecOps._
import com.advancedtelematic.tuf.reposerver.util._

class RepoResourceNamespaceExtractionSpec extends TufReposerverSpec
  with ResourceSpec with BeforeAndAfterAll with Inspectors with Whenever with PatienceConfiguration with RepoNamespaceRepositorySupport {

  implicit val ec = ExecutionContext.global

  val dbValidation = new DatabaseNamespaceValidation(NamespaceDirectives.defaultNamespaceExtractor.map(_.namespace))

  private val tufTargetsPublisher = new TufTargetsPublisher(messageBusPublisher)

  override lazy val routes: Route = ErrorHandler.handleErrors {
    new RepoResource(fakeKeyserverClient, dbValidation, targetStore, tufTargetsPublisher).route
  }

  val testFile = {
    val checksum = Sha256Digest.digest("hi".getBytes)
    RequestTargetItem(Uri.Empty, checksum, targetFormat = None, name = None, version = None, hardwareIds = Seq.empty, length = "hi".getBytes.length)
  }

  test("reject when repo does not belong to namespace") {
    val repoId = RepoId.generate()

    withNamespace("rejectit") { implicit ns =>
      Post(s"/repo/${repoId.show}").namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Get(s"/repo/${repoId.show}/root.json") ~> Route.seal(routes) ~> check {
      status shouldBe StatusCodes.Forbidden
    }
  }

  test("allow when repo belongs to namespace") {
    val repoId = RepoId.generate()

    withNamespace("mynamespace") { implicit ns =>
      Post(s"/repo/${repoId.show}").namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.OK
      }

      Post(s"/repo/${repoId.show}/targets/myfile", testFile).namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.OK
      }

    }
  }

  test("reject when user repo does not belong to user namespace") {
    withNamespace("authnamespace") { implicit ns =>
      Post(s"/user_repo/targets/myfile", testFile).namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  test("allow when user repo belongs to user namespace") {
    withNamespace("mynamespace02") { implicit ns =>
      Post(s"/user_repo").namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[RepoId]
      }

      Post(s"/user_repo/targets/myfile", testFile).namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.OK
      }


      Put(s"/user_repo/comments/myfile", CommentRequest(TargetComment("comment"))).namespaced ~> Route.seal(routes) ~> check {
        status shouldBe StatusCodes.OK
      }

    }
  }
}
