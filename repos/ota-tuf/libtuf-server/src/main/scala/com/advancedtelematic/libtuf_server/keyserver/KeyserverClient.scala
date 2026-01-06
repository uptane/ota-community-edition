package com.advancedtelematic.libtuf_server.keyserver

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.Uri.Path.{Empty, Slash}
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{StatusCodes, *}
import cats.syntax.show.*
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{RawError, RemoteServiceError}
import com.advancedtelematic.libats.http.ServiceHttpClientSupport
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.tracing.TracingHttpClient
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TufRole}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.*
import com.advancedtelematic.libtuf.data.TufDataType.{
  KeyId,
  KeyType,
  RepoId,
  SignedPayload,
  TufKeyPair
}
import io.circe.{Codec, Json}

import java.time.Instant
import scala.concurrent.Future

object KeyserverClient {

  val KeysNotReady =
    RawError(ErrorCode("keys_not_ready"), StatusCodes.Locked, "Keys not ready in remote keyserver")

  val RootRoleNotFound = RawError(
    ErrorCode("root_role_not_found"),
    StatusCodes.FailedDependency,
    "root role was not found in upstream key store"
  )

  val RootRoleConflict =
    RawError(ErrorCode("root_role_conflict"), StatusCodes.Conflict, "root role already exists")

  val RoleKeyNotFound = RawError(
    ErrorCode("role_key_not_found"),
    StatusCodes.PreconditionFailed,
    s"can't sign since role was not found in upstream key store"
  )

  val KeyPairNotFound =
    RawError(ErrorCode("keypair_not_found"), StatusCodes.NotFound, "keypair not found in keyserver")

}

trait KeyserverClient {

  def createRoot(repoId: RepoId,
                 keyType: KeyType = KeyType.default,
                 forceSync: Boolean = true): Future[Json]

  def sign[T: Codec: TufRole](repoId: RepoId, payload: T): Future[SignedPayload[T]]

  def fetchRootRole(repoId: RepoId,
                    expiresNotBefore: Option[Instant] = None): Future[SignedPayload[RootRole]]

  def fetchRootRole(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]]

  def fetchUnsignedRoot(repoId: RepoId): Future[RootRole]

  def rotateRoot(repoId: RepoId): Future[Unit]

  def updateRoot(repoId: RepoId, signedPayload: SignedPayload[RootRole]): Future[Unit]

  def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit]

  def fetchKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair]

  def fetchTargetKeyPairs(repoId: RepoId): Future[Seq[TufKeyPair]]

  def addOfflineUpdatesRole(repoId: RepoId): Future[Unit]

  def addRemoteSessionsRole(repoId: RepoId): Future[Unit]
}

object KeyserverHttpClient extends ServiceHttpClientSupport {

  def apply(
    uri: Uri)(implicit system: ActorSystem, tracing: ServerRequestTracing): KeyserverHttpClient =
    new KeyserverHttpClient(uri, defaultHttpClient)

}

class KeyserverHttpClient(uri: Uri, httpClient: HttpRequest => Future[HttpResponse])(
  implicit system: ActorSystem,
  tracing: ServerRequestTracing)
    extends TracingHttpClient(httpClient, "keyserver")
    with KeyserverClient {

  import KeyserverClient._
  import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
  import io.circe.syntax._
  import com.advancedtelematic.libats.http.ServiceHttpClient._
  import system.dispatcher

  private def apiUri(path: Path) =
    uri.withPath(Empty / "api" / "v1" ++ Slash(path))

  override def createRoot(repoId: RepoId, keyType: KeyType, forceSync: Boolean): Future[Json] = {
    val entity = Json.obj(
      "threshold" -> 1.asJson,
      "keyType" -> keyType.asJson,
      "forceSync" -> forceSync.asJson
    )

    val req = HttpRequest(HttpMethods.POST, uri = apiUri(Path("root") / repoId.show))

    execJsonHttp[Json, Json](req, entity).handleErrors {
      case RemoteServiceError(_, response, _, _, _, _) if response.status == StatusCodes.Conflict =>
        Future.failed(RootRoleConflict)
      case RemoteServiceError(_, response, _, _, _, _) if response.status == StatusCodes.Locked =>
        Future.failed(KeysNotReady)
    }
  }

  override def sign[T: Codec](repoId: RepoId, payload: T)(
    implicit tufRole: TufRole[T]): Future[SignedPayload[T]] = {
    val req = HttpRequest(
      HttpMethods.POST,
      uri = apiUri(Path("root") / repoId.show / tufRole.roleType.show)
    )
    execJsonHttp[SignedPayload[T], Json](req, payload.asJson).handleErrors {
      case RemoteServiceError(_, response, _, _, _, _)
          if response.status == StatusCodes.PreconditionFailed =>
        Future.failed(RoleKeyNotFound)
    }
  }

  override def fetchRootRole(repoId: RepoId,
                             expireNotBefore: Option[Instant]): Future[SignedPayload[RootRole]] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show))

    val reqWithParams = expireNotBefore match {
      case Some(e) => req.withHeaders(RawHeader("x-trx-expire-not-before", e.toString))
      case None    => req
    }

    execHttpUnmarshalled[SignedPayload[RootRole]](reqWithParams).handleErrors {
      case RemoteServiceError(_, response, _, _, _, _) if response.status == StatusCodes.NotFound =>
        Future.failed(RootRoleNotFound)
      case RemoteServiceError(_, response, _, _, _, _) if response.status == StatusCodes.Locked =>
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
      case err @ RemoteServiceError(_, response, _, _, _, _)
          if response.status == StatusCodes.BadRequest =>
        Future.failed(err)
    }
  }

  override def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit] = {
    val req = HttpRequest(
      HttpMethods.DELETE,
      uri = apiUri(Path("root") / repoId.show / "private_keys" / keyId.value)
    )
    execHttpUnmarshalled[Unit](req).ok
  }

  override def fetchTargetKeyPairs(repoId: RepoId): Future[Seq[TufKeyPair]] = {
    val req = HttpRequest(
      HttpMethods.GET,
      uri = apiUri(Path("root") / repoId.show / "keys" / "targets" / "pairs")
    )
    execHttpUnmarshalled[Seq[TufKeyPair]](req).ok
  }

  override def fetchRootRole(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]] = {
    val req =
      HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show / version.toString))

    execHttpUnmarshalled[SignedPayload[RootRole]](req).handleErrors {
      case RemoteServiceError(_, response, _, _, _, _) if response.status == StatusCodes.NotFound =>
        Future.failed(RootRoleNotFound)
    }
  }

  override def fetchKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair] = {
    val req =
      HttpRequest(HttpMethods.GET, uri = apiUri(Path("root") / repoId.show / "keys" / keyId.value))

    execHttpUnmarshalled[TufKeyPair](req).handleErrors {
      case RemoteServiceError(_, response, _, _, _, _) if response.status == StatusCodes.NotFound =>
        Future.failed(KeyPairNotFound)
    }
  }

  override def addOfflineUpdatesRole(repoId: RepoId): Future[Unit] = {
    val req = HttpRequest(
      HttpMethods.PUT,
      uri = apiUri(Path("root") / repoId.show / "roles" / "offline-updates")
    )
    execHttpUnmarshalled[Unit](req).ok
  }

  override def addRemoteSessionsRole(repoId: RepoId): Future[Unit] = {
    val req = HttpRequest(
      HttpMethods.PUT,
      uri = apiUri(Path("root") / repoId.show / "roles" / "remote-sessions")
    )
    execHttpUnmarshalled[Unit](req).ok
  }

  override def rotateRoot(repoId: RepoId): Future[Unit] = {
    val req = HttpRequest(HttpMethods.PUT, uri = apiUri(Path("root") / repoId.show / "rotate"))
    execHttpUnmarshalled[Unit](req).handleErrors {
      case RemoteServiceError(_, response, _, _, _, _)
          if response.status == StatusCodes.PreconditionFailed =>
        Future.failed(RoleKeyNotFound)
    }
  }

}
