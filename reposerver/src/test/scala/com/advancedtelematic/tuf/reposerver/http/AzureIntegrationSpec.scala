package com.advancedtelematic.tuf.reposerver.http

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.{FileIO, Source}
import org.apache.pekko.stream.IOResult
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import com.advancedtelematic.libtuf.crypt.{Sha256FileDigest, TufCrypto}
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  TargetCustom,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  RepoId,
  RoleType,
  SignedPayload,
  TargetFormat,
  TargetName,
  TargetVersion,
  ValidTargetFilename
}
import com.advancedtelematic.libtuf.http.ReposerverHttpClient
import com.advancedtelematic.libtuf_server.data.Requests
import com.advancedtelematic.tuf.reposerver.target_store.{AzureTargetStoreEngine, TargetStore}
import com.advancedtelematic.tuf.reposerver.util.{ResourceSpec, TufReposerverSpec}
import com.advancedtelematic.tuf.reposerver.Settings
import org.scalatest.{time, BeforeAndAfterAll, Inspectors}
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.prop.Whenever
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.client4.*
import sttp.client4.pekkohttp.{PekkoHttpBackend, PekkoHttpClient}
import sttp.model.{StatusCode, Uri}
import org.scalatest.OptionValues.*
import cats.syntax.option.*
import cats.syntax.show.*
import io.circe.syntax.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  TargetCustom,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import sttp.{capabilities, client4}
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.{GenericRequest, WebSocketStreamBackend}

import scala.concurrent.{duration, Future}
import scala.concurrent.duration.Duration
import scala.util.{Random, Success}

class AzureIntegrationSpec
    extends TufReposerverSpec
    with ResourceSpec
    with BeforeAndAfterAll
    with Inspectors
    with Whenever
    with PatienceConfiguration {

  private[this] val storeEngine = {
    val storeConfig = new Settings {}.azureSettings
    new AzureTargetStoreEngine(storeConfig)
  }

  override lazy val targetStore =
    new TargetStore(fakeKeyserverClient, storeEngine, fakeHttpClient, messageBusPublisher)

  private val tufTargetsPublisher = new TufTargetsPublisher(messageBusPublisher)

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = time.Span(15, Seconds), Span(100, Millis))

  override lazy val routes = Route.seal {
    pathPrefix("api" / "v1") {
      new RepoResource(
        fakeKeyserverClient,
        namespaceValidation,
        targetStore,
        tufTargetsPublisher,
        fakeRemoteDelegationClient
      ).route
    }
  }

  val repoId = RepoId.generate()

  override def beforeAll(): Unit = {
    super.beforeAll()

    fakeKeyserverClient.createRoot(repoId).futureValue

    Post(
      apiUri(s"repo/${repoId.uuid.toString}"),
      Requests.CreateRepositoryRequest(Ed25519KeyType)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("store") {
    val uploadFilePath = Files.createTempFile("upload", "txt")
    val chunkSize = 2000000
    val fileGenResult = Source(1.to(5))
      .map(_ => ByteString(Random.nextString(chunkSize)))
      .concat(Source.single(ByteString(Random.nextString(Random.nextInt(chunkSize)))))
      .runWith(FileIO.toPath(uploadFilePath))
      .futureValue
    val uploadedFileChecksum =
      com.advancedtelematic.libtuf.crypt.Sha256FileDigest.from(uploadFilePath)
    println(s"File generated: ${fileGenResult.count} bytes, checksum: ${uploadedFileChecksum}")

    val source = FileIO.fromPath(uploadFilePath, 2048000)
    val targetFilename = eu.timepit.refined
      .refineV[ValidTargetFilename](uploadFilePath.getFileName.toString)
      .toOption
      .get
    val storeResult = storeEngine.store(repoId, targetFilename, source).futureValue
    storeResult.checksum should equal(uploadedFileChecksum)
  }

  test("cli upload and download") {
    val realClient = PekkoHttpBackend.apply()
    val testBackend =
      PekkoHttpBackend.usingClient(system, http = PekkoHttpClient.stubFromRoute(Route.seal(routes)))

    val testBackendWithFallback = new WebSocketStreamBackend[Future, PekkoStreams]() {
      override def send[T](
        request: GenericRequest[T, PekkoStreams & capabilities.WebSockets & capabilities.Effect[
          Future
        ]]): Future[client4.Response[T]] =
        if (request.uri.host.contains("0.0.0.0")) {
          testBackend.send(request)
        } else {
          realClient.send(request)
        }

      override def close(): Future[Unit] = testBackend.close()

      override def monad: sttp.monad.MonadError[Future] = testBackend.monad
    }

    val client = new ReposerverHttpClient(URI.create("http://0.0.0.0"), testBackendWithFallback)

    val targetInfo = uploadTargetFile(TargetName("test"), TargetVersion("0.0.1"), client)

    updateTargetsMetadata(repoId, targetInfo)
    downloadTarget(realClient, "core.windows.net", repoId, targetInfo)
  }

}
