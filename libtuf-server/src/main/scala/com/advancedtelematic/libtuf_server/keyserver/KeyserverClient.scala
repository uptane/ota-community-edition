package com.advancedtelematic.libtuf_server.keyserver

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.{Empty, Slash}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{StatusCodes, _}
import cats.syntax.show._
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{RawError, RemoteServiceError}
import com.advancedtelematic.libats.http.ServiceHttpClientSupport
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.tracing.TracingHttpClient
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.{RoleType, _}
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, KeyId, KeyType, RepoId, SignedPayload, TufKeyPair}
import io.circe.Json

import scala.concurrent.Future

object KeyserverClient {
  val KeysNotReady = RawError(ErrorCode("keys_not_ready"), StatusCodes.Locked, "Keys not ready in remote keyserver")
  val RootRoleNotFound = RawError(ErrorCode("root_role_not_found"), StatusCodes.FailedDependency, "root role was not found in upstream key store")
  val RootRoleConflict = RawError(ErrorCode("root_role_conflict"), StatusCodes.Conflict, "root role already exists")
  val RoleKeyNotFound = RawError(ErrorCode("role_key_not_found"), StatusCodes.PreconditionFailed, s"can't sign since role was not found in upstream key store")
  val KeyError = RawError(ErrorCode("key_error"), StatusCodes.BadRequest, "key cannot be processed")
  val KeyPairNotFound = RawError(ErrorCode("keypair_not_found"), StatusCodes.NotFound, "keypair not found in keyserver")
}

trait KeyserverClient {
  def createRoot(repoId: RepoId, keyType: KeyType = KeyType.default, forceSync: Boolean = false): Future[Json]

  def sign(repoId: RepoId, roleType: RoleType, payload: Json): Future[JsonSignedPayload]

  def fetchRootRole(repoId: RepoId): Future[SignedPayload[RootRole]]

  def fetchRootRole(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]]

  def fetchUnsignedRoot(repoId: RepoId): Future[RootRole]

  def updateRoot(repoId: RepoId, signedPayload: SignedPayload[RootRole]): Future[Unit]

  def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit]

  def fetchKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair]

  def fetchTargetKeyPairs(repoId: RepoId): Future[Seq[TufKeyPair]]
}

object KeyserverHttpClient extends ServiceHttpClientSupport {
  def apply(uri: Uri)(implicit system: ActorSystem, tracing: ServerRequestTracing): KeyserverHttpClient =
    new KeyserverHttpClient(uri, defaultHttpClient)
}

class KeyserverHttpClient(uri: Uri, httpClient: HttpRequest => Future[HttpResponse])
                         (implicit system: ActorSystem, tracing: ServerRequestTracing)
  extends TracingHttpClient(httpClient, "keyserver") with KeyserverClient {

  import KeyserverClient._
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.syntax._
  import com.advancedtelematic.libats.http.ServiceHttpClient._
  import system.dispatcher

  private def apiUri(path: Path) =
    uri.withPath(Empty / "api" / "v1" ++ Slash(path))

  override def createRoot(repoId: RepoId, keyType: KeyType, forceSync: Boolean): Future[Json] = {
    val entity = Json.obj("threshold" -> 1.asJson, "keyType" -> keyType.asJson)

    val req = HttpRequest(HttpMethods.POST, uri = apiUri(Path("root") / repoId.show))

    val finalReq =
      if(forceSync)
        req.addHeader(RawHeader("x-ats-tuf-force-sync", "keys"))
      else
        req

    execJsonHttp[Json, Json](finalReq, entity).handleErrors {
      case RemoteServiceError(_, StatusCodes.Conflict, _, _, _, _)  =>
        Future.failed(RootRoleConflict)
      case RemoteServiceError(_, StatusCodes.Locked, _, _, _, _) =>
        Future.failed(KeysNotReady)
    }
  }

  override def sign(repoId: RepoId, roleType: RoleType, payload: Json): Future[JsonSignedPayload] = {
    val req = HttpRequest(HttpMethods.POST, uri = apiUri(Path("root") / repoId.show / roleType.show))
    execJsonHttp[JsonSignedPayload, Json](req, payload).handleErrors {
      case RemoteServiceError(_, StatusCodes.PreconditionFailed, _, _, _, _) =>
        Future.failed(RoleKeyNotFound)
    }
  }

  override def fetchRootRole(repoId: RepoId): Future[SignedPayload[RootRole]] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show))

    execHttpUnmarshalled[SignedPayload[RootRole]](req).handleErrors {
      case RemoteServiceError(_, StatusCodes.NotFound, _, _, _, _) =>
        Future.failed(RootRoleNotFound)
      case RemoteServiceError(_, StatusCodes.Locked, _, _, _, _) =>
        Future.failed(KeysNotReady)
    }
  }

  override def fetchUnsignedRoot(repoId: RepoId): Future[RootRole] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show / "unsigned"))
    execHttpUnmarshalled[RootRole](req).ok
  }

  override def updateRoot(repoId: RepoId, signedPayload: SignedPayload[RootRole]): Future[Unit] = {
    val req = HttpRequest(HttpMethods.POST, uri = apiUri(Path("root") / repoId.show / "unsigned"))
    execJsonHttp[Unit, SignedPayload[RootRole]](req, signedPayload).handleErrors {
      case remoteError if remoteError.status == StatusCodes.BadRequest =>
        Future.failed(remoteError)
    }
  }

  override def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit] = {
    val req = HttpRequest(HttpMethods.DELETE, uri = apiUri(Path("root") / repoId.show / "private_keys" / keyId.value))
    execHttpUnmarshalled[Unit](req).ok
  }

  override def fetchTargetKeyPairs(repoId: RepoId): Future[Seq[TufKeyPair]] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show / "keys" / "targets" / "pairs"))
    execHttpUnmarshalled[Seq[TufKeyPair]](req).ok
  }

  override def fetchRootRole(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show / version.toString))

    execHttpUnmarshalled[SignedPayload[RootRole]](req).handleErrors {
      case RemoteServiceError(_, StatusCodes.NotFound, _, _, _, _) =>
        Future.failed(RootRoleNotFound)
    }
  }

  override def fetchKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show / "keys" / keyId.value))

    execHttpUnmarshalled[TufKeyPair](req).handleErrors {
      case RemoteServiceError(_, StatusCodes.NotFound, _, _, _, _) =>
        Future.failed(KeyPairNotFound)
    }
  }
}
