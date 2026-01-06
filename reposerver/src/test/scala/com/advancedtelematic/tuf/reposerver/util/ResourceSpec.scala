package com.advancedtelematic.tuf.reposerver.util

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.{Directive1, Directives, Route}
import org.apache.pekko.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.testkit.TestDuration
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libats.http.NamespaceDirectives
import com.advancedtelematic.libats.http.tracing.NullServerRequestTracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.messaging.MemoryMessageBus
import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import com.advancedtelematic.libtuf.crypt.{Sha256FileDigest, TufCrypto}
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, RoleKeys, RootRole, TargetCustom, TargetsRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.*
import com.advancedtelematic.libtuf.http.ReposerverHttpClient
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.tuf.reposerver.delegations.RemoteDelegationClient
import com.advancedtelematic.tuf.reposerver.http.{Errors, NamespaceValidation, TufReposerverRoutes}
import com.advancedtelematic.tuf.reposerver.target_store.{LocalTargetStoreEngine, TargetStore}
import com.advancedtelematic.tuf.reposerver.util.ResourceSpec.TargetInfo
import io.circe.{Codec, Json}
import sttp.client4.{Backend, StreamBackend}
import sttp.model.StatusCodes

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.NoSuchElementException
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.Try
import scala.async.Async.*

class FakeKeyserverClient extends KeyserverClient {

  import KeyserverClient._
  import io.circe.syntax._

  private val keys = new ConcurrentHashMap[RepoId, Map[RoleType, TufKeyPair]]()

  private val rootRoles = new ConcurrentHashMap[RepoId, SignedPayload[RootRole]]()

  private val pendingRequests = new ConcurrentHashMap[RepoId, Boolean]()

  def publicKey(repoId: RepoId, roleType: RoleType): TufKey = keys.get(repoId)(roleType).pubkey

  def resetKeyServer(): Unit = this.synchronized {
    keys.clear()
    rootRoles.clear()
  }

  def forceKeyGenerationPending(repoId: RepoId): Unit = {
    deleteRepo(repoId)
    pendingRequests.put(repoId, true)
  }

  private lazy val preGeneratedKeys: Map[KeyType, Map[RoleType, TufKeyPair]] =
    Seq(RsaKeyType, Ed25519KeyType).map { keyType =>
      keyType -> RoleType.TUF_ALL.map { role =>
        role -> keyType.crypto.generateKeyPair()
      }.toMap
    }.toMap

  def updateRepoKeys(repoId: RepoId,
                     role: RoleType,
                     keyPair: TufKeyPair): Map[RoleType, TufKeyPair] =
    keys.compute(
      repoId,
      (t: RepoId, u: Map[RoleType, TufKeyPair]) =>
        if (u == null)
          Map(role -> keyPair)
        else
          u + (role -> keyPair)
    )

  private def generateRoot(repoId: RepoId, keyType: KeyType): RootRole = {
    updateRepoKeys(repoId, RoleType.ROOT, keyType.crypto.generateKeyPair())

    val roles = keys.get(repoId).map { case (role, keyPair) =>
      role -> RoleKeys(List(keyPair.pubkey.id), threshold = 1)
    }

    val clientKeys = keys.get(repoId).map { case (_, keyPair) =>
      keyPair.pubkey.id -> keyPair.pubkey
    }

    // expires truncated to seconds since circe codecs will code it that way, we cannot save it with more precision than that
    RootRole(
      clientKeys,
      roles,
      expires = Instant.now.plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS),
      version = 1
    )
  }

  private def generateKeys(repoId: RepoId, keyType: KeyType): List[TufKeyPair] =
    preGeneratedKeys(keyType).map { case (role, keyPair) =>
      updateRepoKeys(repoId, role, keyPair)

      keyPair
    }.toList

  def deleteRepo(repoId: RepoId): Option[RootRole] =
    keys.asScala.remove(repoId).flatMap(_ => rootRoles.asScala.remove(repoId).map(_.signed))

  override def createRoot(repoId: RepoId, keyType: KeyType, forceSync: Boolean): Future[Json] =
    if (keys.contains(repoId)) {
      FastFuture.failed(RootRoleConflict)
    } else {
      generateKeys(repoId, keyType)
      val rootRole = generateRoot(repoId, keyType)
      sign(repoId, rootRole).map { signedPayload =>
        rootRoles.put(repoId, signedPayload)
        rootRole.asJson
      }
    }

  override def sign[T: Codec](repoId: RepoId, payload: T)(
    implicit tufRole: TufRole[T]): Future[SignedPayload[T]] = {
    val okey = keys.asScala.get(repoId).flatMap(_.get(tufRole.roleType))
    val fkey = okey.map(FastFuture.successful).getOrElse(FastFuture.failed(RoleKeyNotFound))

    fkey.map { tufKeyPair =>
      val signature =
        TufCrypto.signPayload(tufKeyPair.privkey, payload.asJson).toClient(tufKeyPair.pubkey.id)
      SignedPayload(List(signature), payload, payload.asJson)
    }
  }

  private def refreshAndSaveRoot(repoId: RepoId,
                                 role: SignedPayload[RootRole],
                                 expireNotBefore: Instant): Future[SignedPayload[RootRole]] = {
    val newRole = role.signed.copy(expires = expireNotBefore)
    sign(repoId, newRole).map { signedPayload =>
      rootRoles.put(repoId, signedPayload)
      signedPayload
    }
  }

  override def fetchRootRole(
    repoId: RepoId,
    expireNotBefore: Option[Instant] = None): Future[SignedPayload[RootRole]] =
    Future
      .fromTry {
        Try {
          if (pendingRequests.asScala.getOrElse(repoId, false))
            throw KeysNotReady

          rootRoles.asScala(repoId)
        }.recover { case _: NoSuchElementException =>
          throw RootRoleNotFound
        }
      }
      .flatMap { existing =>
        expireNotBefore match {
          case Some(e) => refreshAndSaveRoot(repoId, existing, e)
          case None    => FastFuture.successful(existing)
        }
      }

  override def fetchUnsignedRoot(repoId: RepoId): Future[RootRole] =
    fetchRootRole(repoId).map(_.signed)

  override def updateRoot(repoId: RepoId, newRoot: SignedPayload[RootRole]): Future[Unit] =
    FastFuture.successful {
      rootRoles.computeIfPresent(
        repoId,
        (t: RepoId, oldRoot: SignedPayload[RootRole]) => {
          assert(oldRoot != null, "fake keyserver, Role does not exist")
          newRoot
        }
      )
    }

  override def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit] =
    FastFuture.successful {
      keys.asScala
        .get(repoId)
        .flatMap(_.values.find(_.pubkey.id == keyId))
        .getOrElse(throw RoleKeyNotFound)

      keys.computeIfPresent(
        repoId,
        (id: RepoId, existingKeys: Map[RoleType, TufKeyPair]) =>
          existingKeys.filter(_._2.pubkey.id != keyId)
      )
    }

  override def fetchTargetKeyPairs(repoId: RepoId): Future[Seq[TufKeyPair]] =
    FastFuture.successful {
      Seq(
        keys.asScala
          .getOrElse(repoId, throw RoleKeyNotFound)
          .getOrElse(RoleType.TARGETS, throw RoleKeyNotFound)
      )
    }

  override def fetchRootRole(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]] =
    fetchRootRole(repoId).filter(_.signed.version == version)

  override def fetchKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair] = Future.fromTry {
    Try {
      keys.asScala
        .get(repoId)
        .flatMap(_.values.find(_.pubkey.id == keyId))
        .getOrElse(throw KeyPairNotFound)
    }
  }

  private def addRoles(repoId: RepoId, roles: RoleType*): Future[Unit] = async {
    val rootRole = await(fetchUnsignedRoot(repoId))

    val rootKeyType = for {
      roleKeys <- rootRole.roles.get(RoleType.ROOT)
      keyId <- roleKeys.keyids.headOption
      tufKey <- rootRole.keys.get(keyId)
    } yield tufKey.keytype

    val keyType = rootKeyType.getOrElse(KeyType.default)

    val keyPair = keyType.crypto.generateKeyPair()

    val keys = roles.map(role => role -> keyPair)

    keys.foreach { case (role, key) =>
      updateRepoKeys(repoId, role, key)
    }

    val roleKeys = keys.map { case (role, key) =>
      role -> RoleKeys(Seq(key.pubkey.id), 1)
    }.toMap

    val newRoles = rootRole.roles ++ roleKeys
    val newKeys = rootRole.keys + (keyPair.pubkey.id -> keyPair.pubkey)

    val newRootRole = RootRole(
      roles = newRoles,
      keys = newKeys,
      version = rootRole.version + 1,
      expires = rootRole.expires.plus(1, ChronoUnit.DAYS)
    )
    val signed = await(sign(repoId, newRootRole))
    rootRoles.put(repoId, signed)
  }

  override def addOfflineUpdatesRole(repoId: RepoId): Future[Unit] =
    addRoles(repoId, RoleType.OFFLINE_UPDATES, RoleType.OFFLINE_SNAPSHOT)

  override def addRemoteSessionsRole(repoId: RepoId): Future[Unit] =
    addRoles(repoId, RoleType.REMOTE_SESSIONS)

  // Does not actually rotate, but for testing on reposerver is ok. Rotation is done on keyserver
  override def rotateRoot(repoId: RepoId): Future[Unit] = async {
    val oldRoot = await(fetchRootRole(repoId)).signed
    val newRootRole = oldRoot.copy(
      version = oldRoot.version + 1,
      expires = oldRoot.expires.plus(1, ChronoUnit.DAYS)
    )
    val signed = await(sign(repoId, newRootRole))
    rootRoles.put(repoId, signed)
  }

}

trait LongHttpRequest {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    60.seconds.dilated(system)
  )

}

trait FakeHttpClientSpec {

  class FakeHttpClient extends (HttpRequest => Future[HttpResponse]) {
    val fileUri: Uri = "http://testfile"
    lazy val fileBody = HttpEntity.apply(ContentTypes.`text/plain(UTF-8)`, "Test text 1".getBytes())

    override def apply(req: HttpRequest): Future[HttpResponse] = Future.successful {
      req match {
        case HttpRequest(_, uri, _, _, _) if uri.toString().endsWith("testfile") =>
          HttpResponse(entity = fileBody)
        case HttpRequest(_, uri, _, _, _) =>
          HttpResponse(StatusCodes.NotFound, entity = s"[fakehttpserver] $uri not found")
      }
    }

  }

  val fakeHttpClient = new FakeHttpClient
}

trait HttpClientSpecSupport {
  self: ResourceSpec =>

  def testHttpClient(req: HttpRequest): Future[HttpResponse] = {
    val p = Promise[HttpResponse]()
    req ~> Route.seal(routes) ~> check(p.success(response))
    p.future
  }

}

object ResourceSpec {

  final case class TargetInfo(name: TargetName,
                              version: TargetVersion,
                              targetFilename: TargetFilename,
                              localFilePath: Path,
                              checksum: Checksum)

  object TargetInfo {

    def generate(name: TargetName, version: TargetVersion): TargetInfo = {
      val targetFilename = eu.timepit.refined
        .refineV[ValidTargetFilename](s"${name.value}-${version.value}")
        .toOption
        .get

      val uploadFilePath = Files.createTempFile("s3upload", "txt")
      Files.write(
        uploadFilePath,
        "“Como todos los hombres de la Biblioteca, he viajado en mi juventud“".getBytes(
          StandardCharsets.UTF_8
        )
      )
      val uploadedFileChecksum =
        com.advancedtelematic.libtuf.crypt.Sha256FileDigest.from(uploadFilePath)
      TargetInfo(name, version, targetFilename, uploadFilePath, uploadedFileChecksum)
    }

  }

}

class FakeRemoteDelegationsClient()(implicit val system: ActorSystem)
    extends RemoteDelegationClient {

  private val remotes = new ConcurrentHashMap[Uri, Json]()

  private val uriHeaders = new ConcurrentHashMap[Uri, Map[String, String]]()

  def setRemote(uri: Uri,
                delegatedTargets: Json,
                headers: Map[String, String] = Map.empty): Unit = {
    remotes.put(uri, delegatedTargets)
    uriHeaders.put(uri, headers)
  }

  override def fetch[Resp](uri: Uri, headers: Map[String, String])(
    implicit um: FromEntityUnmarshaller[Resp]): Future[Resp] =
    if (!remotes.containsKey(uri))
      FastFuture.failed(
        Errors.DelegationRemoteFetchFailed(
          uri,
          StatusCodes.NotFound,
          s"[test] remote delegation not found: $uri"
        )
      )
    else if (uriHeaders.get(uri) != headers)
      FastFuture.failed(
        Errors.DelegationRemoteFetchFailed(
          uri,
          StatusCodes.NotFound,
          s"[test] request headers do not match expected headers: $uri"
        )
      )
    else {
      val delegationJson = remotes.get(uri)
      val entity =
        HttpEntity.Strict(ContentTypes.`application/json`, ByteString(delegationJson.spaces2))

      um(entity).recoverWith { case _ =>
        FastFuture.failed(Errors.DelegationRemoteParseFailed(uri, "[test] invalid json"))
      }
    }

}

trait ResourceSpec
    extends TufReposerverSpec
    with ScalatestRouteTest
    with MysqlDatabaseSpec
    with FakeHttpClientSpec
    with LongHttpRequest
    with Directives
    with RepositoryTestOps {

  import cats.syntax.show._
  import org.scalatest.OptionValues._

  def apiUri(path: String): String = "/api/v1/" + path

  def apiUriV2(path: String): String = "/api/v2/" + path

  val fakeKeyserverClient: FakeKeyserverClient = new FakeKeyserverClient

  val fakeRemoteDelegationClient = new FakeRemoteDelegationsClient

  val defaultNamespaceExtractor = NamespaceDirectives.defaultNamespaceExtractor

  val namespaceValidation = new NamespaceValidation(defaultNamespaceExtractor) {
    override def apply(repoId: RepoId): Directive1[Namespace] = defaultNamespaceExtractor
  }

  val storageRoot = Files.createTempDirectory("target-storage").toFile
  val localStorage = new LocalTargetStoreEngine(storageRoot)

  lazy val targetStore =
    new TargetStore(fakeKeyserverClient, localStorage, fakeHttpClient, messageBusPublisher)

  val memoryMessageBus = new MemoryMessageBus
  val messageBusPublisher = memoryMessageBus.publisher()

  lazy val routes = new TufReposerverRoutes(
    fakeKeyserverClient,
    namespaceValidation,
    targetStore,
    messageBusPublisher,
    fakeRemoteDelegationClient
  ).routes

  implicit lazy val tracing: ServerRequestTracing = new NullServerRequestTracing

  protected def uploadTargetFile(name: TargetName,
                                 version: TargetVersion,
                                 client: ReposerverHttpClient): TargetInfo = {
    val target = TargetInfo.generate(name, version)
    client.uploadTarget(target.targetFilename, target.localFilePath, 10.seconds).futureValue
    target
  }

  protected def updateTargetsMetadata(repoId: RepoId, targetInfo: TargetInfo): Unit = {
    import cats.syntax.option._
    import com.advancedtelematic.libtuf.data.TufCodecs._
    import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
    import io.circe.syntax._

    val rootRole = fakeKeyserverClient.fetchRootRole(repoId).futureValue
    val targetKeyId = rootRole.signed.roles(RoleType.TARGETS).keyids.head
    val keyPair = fakeKeyserverClient.fetchKeyPair(repoId, targetKeyId).futureValue

    val (checksumHeader, version) =
      Get(apiUri(s"repo/${repoId.show}/targets.json")) ~> routes ~> check {
        header("x-ats-role-checksum").value -> responseAs[SignedPayload[TargetsRole]].signed.version
      }

    val custom = TargetCustom(
      targetInfo.name,
      targetInfo.version,
      Seq.empty,
      targetFormat = TargetFormat.BINARY.some,
      cliUploaded = true.some
    ).asJson
    val newTargetsMap = Map(
      targetInfo.targetFilename -> ClientTargetItem(
        Map(targetInfo.checksum.method -> targetInfo.checksum.hash),
        targetInfo.localFilePath.toFile.length(),
        custom = custom.some
      )
    )
    val newTargets = TargetsRole(
      Instant.now().plus(30, ChronoUnit.DAYS),
      targets = newTargetsMap,
      version = version + 1
    )
    val signature =
      TufCrypto.signPayload(keyPair.privkey, newTargets.asJson).toClient(keyPair.pubkey.id)
    val signedPayload: SignedPayload[TargetsRole] =
      SignedPayload(List(signature), newTargets, newTargets.asJson)

    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload)
      .addHeader(checksumHeader) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  def downloadTarget(client: Backend[Future],
                     redirectEndpointSuyffix: String,
                     repoId: RepoId,
                     targetInfo: TargetInfo): Unit = {
    import sttp.client4.*
    import sttp.model.Uri

    Get(
      apiUri(s"repo/${repoId.show}/targets/" + targetInfo.targetFilename.value)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.Found
      val uri = Uri.parse(header("Location").value.value()).toOption.get
      uri.host.value should include(redirectEndpointSuyffix)

      val downloadPath = Files.createTempFile("s3download", "txt")
      val req = basicRequest.get(uri).response(asPathAlways(downloadPath))
      val resp = client.send(req).futureValue

      val downloadedChecksum = Sha256FileDigest.from(resp.body)

      downloadedChecksum shouldBe targetInfo.checksum
      downloadPath.toFile.length() shouldBe targetInfo.localFilePath.toFile.length()
    }
  }

}
