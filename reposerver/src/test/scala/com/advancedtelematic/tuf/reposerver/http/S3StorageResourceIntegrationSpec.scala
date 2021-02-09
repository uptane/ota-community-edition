package com.advancedtelematic.tuf.reposerver.http

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.headers.{Location, _}
import akka.http.scaladsl.model.{HttpEntity, Multipart, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.libtuf.crypt.{Sha256FileDigest, TufCrypto}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, TargetCustom, TargetsRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.RepoId._
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, RepoId, RoleType, SignedPayload, TargetFormat, TargetName, TargetVersion, ValidTargetFilename}
import com.advancedtelematic.libtuf.http.ReposerverHttpClient
import com.advancedtelematic.libtuf_server.data.Requests
import com.advancedtelematic.tuf.reposerver.Settings
import com.advancedtelematic.tuf.reposerver.target_store.{S3TargetStoreEngine, TargetStore}
import com.advancedtelematic.tuf.reposerver.util.{ResourceSpec, TufReposerverSpec}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import org.scalatest.OptionValues._
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.prop.Whenever
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Inspectors, time}
import sttp.client.akkahttp.{AkkaHttpBackend, AkkaHttpClient}
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend, _}
import sttp.model.{StatusCode, Uri}

import scala.concurrent.Future
import scala.concurrent.duration._

class S3StorageResourceIntegrationSpec
  extends ResourceSpec with BeforeAndAfterAll with Inspectors with Whenever with PatienceConfiguration {

  lazy val credentials = new Settings {}.s3Credentials

  lazy val s3Storage = new S3TargetStoreEngine(credentials)
  override lazy val targetStore = new TargetStore(fakeKeyserverClient, s3Storage, fakeHttpClient, messageBusPublisher)

  private val tufTargetsPublisher = new TufTargetsPublisher(messageBusPublisher)


  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = time.Span(15, Seconds), Span(100, Millis))

  override lazy val routes = Route.seal {
    pathPrefix("api" / "v1") {
      new RepoResource(fakeKeyserverClient, namespaceValidation, targetStore, tufTargetsPublisher).route
    }
  }

  val repoId = RepoId.generate()

  override def beforeAll(): Unit = {
    super.beforeAll()

    fakeKeyserverClient.createRoot(repoId).futureValue

    Post(apiUri(s"repo/${repoId.show}"), Requests.CreateRepositoryRequest(Ed25519KeyType)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("uploading a target changes targets json") {
    // pending // Needs valid s3 credentials to run

    val entity = HttpEntity(ByteString("""
                                         |Like all the men of the Library, in my younger days I traveled;
                                         |I have journeyed in quest of a book, perhaps the catalog of catalogs.
                                       """.stripMargin))

    val fileBodyPart = BodyPart("file", entity, Map("filename" -> "babel.txt"))

    val form = Multipart.FormData(fileBodyPart)

    Put(apiUri(s"repo/${repoId.show}/targets/some/target/funky/thing?name=pkgname&version=pkgversion&desc=wat"), form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/targets/some/target/funky/thing")) ~> routes ~> check {
      status shouldBe StatusCodes.Found
      header("Location").get.value() should include("amazonaws.com")
    }
  }

  test("PUT to uploads signs custom url when using s3 storage") {
    import org.scalatest.OptionValues._

    Put(apiUri(s"repo/${repoId.show}/uploads/my/target")).withHeaders(`Content-Length`(35445)) ~> routes ~> check {
      status shouldBe StatusCodes.Found
      val url = header[Location].value.uri.toString()

      println(url)

      url should include("amazonaws.com")
      url should include("X-Amz-SignedHeaders")
      url should include("X-Amz-Signature")
    }
  }

  // This test is quite involved but this is required to test properly with S3
  //
  // A custom SttpBackend is setup which calls a real http client when reposerver returns a Redirect response
  // We then upload the file to the server, which will redirect sttp to amazon, the fallback client will push the req.
  // to s3 and return the response returned by s3.
  // We then sign a new targets.json with the new target and upload it to reposerver
  // Finally, we download the target through reposerver and follow the redirect to s3 to verify the stored contents
  // using it's checksum
  test("cli client can upload binary to s3 which can be downloaded through reposerver using redirects") {
    val realClient = AkkaHttpBackend.apply()
    val testBackend = AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(Route.seal(routes)))

    val testBackendWithFallback = new SttpBackend[Future, Nothing, Nothing]() {
      override def send[T](request: Request[T, Nothing]): Future[Response[T]] = {
        responseMonad.flatMap(testBackend.send(request.followRedirects(false))) {
          case resp if resp.code == StatusCode.Found =>
            val location = Uri.parse(resp.header("Location").get).right.get
            realClient.send(request.copy(uri = location: Identity[Uri]))
        }
      }

      override def openWebsocket[T, WS_RESULT](request: Request[T, Nothing], handler: Nothing): Future[WebSocketResponse[WS_RESULT]] = ???

      override def close(): Future[Unit] = testBackend.close()

      override def responseMonad: MonadError[Future] = testBackend.responseMonad
    }

    val client = new ReposerverHttpClient(URI.create("http://0.0.0.0"), testBackendWithFallback)

    val targetInfo = uploadTargetFile(TargetName("test"), TargetVersion("0.0.1"), client)

    updateTargetsMetadata(repoId, targetInfo)
    downloadTarget(realClient, "amazonaws.com", repoId, targetInfo)
  }
}
