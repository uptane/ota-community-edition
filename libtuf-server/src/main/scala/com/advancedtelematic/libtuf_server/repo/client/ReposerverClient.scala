package com.advancedtelematic.libtuf_server.repo.client

import java.util.UUID


import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Path.Slash
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{RawError, RemoteServiceError}
import com.advancedtelematic.libats.http.HttpCodecs._
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.tracing.TracingHttpClient
import com.advancedtelematic.libats.http.ServiceHttpClientSupport
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, KeyType, RepoId, SignedPayload, TargetFilename, TargetName, TargetVersion}
import com.advancedtelematic.libtuf_server.data.Requests.CreateRepositoryRequest
import com.advancedtelematic.libtuf_server.repo.client.ReposerverClient.{KeysNotReady, NotFound, RootNotInKeyserver}
import io.circe.{Decoder, Encoder, Json}
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TargetsRole}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import io.circe.generic.semiauto._

object ReposerverClient {

  object RequestTargetItem {
    implicit val encoder: Encoder[RequestTargetItem] = deriveEncoder
    implicit val decoder: Decoder[RequestTargetItem] = deriveDecoder
  }

  case class RequestTargetItem(uri: Uri, checksum: Checksum,
                               targetFormat: Option[TargetFormat],
                               name: Option[TargetName],
                               version: Option[TargetVersion],
                               hardwareIds: Seq[HardwareIdentifier],
                               length: Long)

  val KeysNotReady = RawError(ErrorCode("keys_not_ready"), StatusCodes.Locked, "keys not ready")
  val RootNotInKeyserver = RawError(ErrorCode("root_role_not_in_keyserver"), StatusCodes.FailedDependency, "the root role was not found in upstream keyserver")
  val NotFound = RawError(ErrorCode("repo_resource_not_found"), StatusCodes.NotFound, "the requested repo resource was not found")
  val RepoConflict = RawError(ErrorCode("repo_conflict"), StatusCodes.Conflict, "repo already exists")
  val PrivateKeysNotInKeyserver = RawError(ErrorCode("private_keys_not_found"), StatusCodes.PreconditionFailed, "could not find required private keys. The repository might be using offline signing")
}


trait ReposerverClient {
  protected def ReposerverError(msg: String) = RawError(ErrorCode("reposerver_remote_error"), StatusCodes.BadGateway, msg)

  def createRoot(namespace: Namespace, keyType: KeyType): Future[RepoId]

  def fetchRoot(namespace: Namespace): Future[(RepoId, SignedPayload[RootRole])]

  def repoExists(namespace: Namespace)(implicit ec: ExecutionContext): Future[Boolean] =
    fetchRoot(namespace).transform {
      case Success(_) | Failure(KeysNotReady) => Success(true)
      case Failure(NotFound) | Failure(RootNotInKeyserver) => Success(false)
      case Failure(t) => Failure(t)
    }

  def addTarget(namespace: Namespace, fileName: String, uri: Uri, checksum: Checksum, length: Int,
                targetFormat: TargetFormat, name: Option[TargetName] = None, version: Option[TargetVersion] = None,
                hardwareIds: Seq[HardwareIdentifier] = Seq.empty): Future[Unit]

  def addTargetFromContent(namespace: Namespace, fileName: String, uri: Option[Uri], checksum: Checksum, length: Int,
                           targetFormat: TargetFormat,
                           content: Source[ByteString, Any],
                           name: TargetName, version: TargetVersion,
                           hardwareIds: Seq[HardwareIdentifier] = Seq.empty): Future[Unit]

  def targetExists(namespace: Namespace, targetFilename: TargetFilename): Future[Boolean]

  def fetchTargets(namespace: Namespace): Future[SignedPayload[TargetsRole]]
}

object ReposerverHttpClient extends ServiceHttpClientSupport {
  def apply(reposerverUri: Uri, authHeaders: Option[HttpHeader] = None)
           (implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer, tracing: ServerRequestTracing): ReposerverHttpClient =
    new ReposerverHttpClient(reposerverUri, defaultHttpClient, authHeaders)
}


class ReposerverHttpClient(reposerverUri: Uri, httpClient: HttpRequest => Future[HttpResponse], authHeaders: Option[HttpHeader] = None)
                          (implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer, tracing: ServerRequestTracing)
  extends TracingHttpClient(httpClient, "reposerver") with ReposerverClient {

  import ReposerverClient._
  import com.advancedtelematic.libats.http.ServiceHttpClient
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.syntax._
  import ServiceHttpClient._

  private def apiUri(path: Path) =
    reposerverUri.withPath(reposerverUri.path / "api" / "v1" ++ Slash(path))

  override def createRoot(namespace: Namespace, keyType: KeyType): Future[RepoId] =
    Marshal(CreateRepositoryRequest(keyType)).to[RequestEntity].flatMap { entity =>
      val req = HttpRequest(HttpMethods.POST, uri = apiUri(Path("user_repo")), entity = entity)

      execHttpUnmarshalledWithNamespace[RepoId](namespace, req).handleErrors {
        case error if error.status == StatusCodes.Conflict =>
          Future.failed(RepoConflict)

        case error if error.status == StatusCodes.Locked =>
          Future.failed(KeysNotReady)
      }
    }

  override def fetchRoot(namespace: Namespace): Future[(RepoId, SignedPayload[RootRole])] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("user_repo/root.json")))

    execHttpFullWithNamespace[SignedPayload[RootRole]](namespace, req).flatMap {
      case Left(error) if error.status == StatusCodes.NotFound =>
        FastFuture.failed(NotFound)
      case Left(error) if error.status == StatusCodes.Locked =>
        FastFuture.failed(KeysNotReady)
      case Left(error) if error.status == StatusCodes.FailedDependency =>
        FastFuture.failed(RootNotInKeyserver)
      case Right(r) =>
        r.httpResponse.headers.find(_.is("x-ats-tuf-repo-id")) match {
          case Some(repoIdHeader) =>
            FastFuture.successful(RepoId(UUID.fromString(repoIdHeader.value)) -> r.unmarshalled)
          case None =>
            FastFuture.failed(NotFound)
        }
    }
  }

  private def addTargetErrorHandler[T]: PartialFunction[RemoteServiceError, Future[T]] = {
    case error if error.status == StatusCodes.PreconditionFailed =>
      Future.failed(PrivateKeysNotInKeyserver)
    case error if error.status == StatusCodes.NotFound =>
      Future.failed(NotFound)
    case error if error.status == StatusCodes.Locked =>
      Future.failed(KeysNotReady)
  }

  override def addTarget(namespace: Namespace, fileName: String,
                         uri: Uri, checksum: Checksum, length: Int,
                         targetFormat: TargetFormat,
                         name: Option[TargetName] = None, version: Option[TargetVersion] = None,
                         hardwareIds: Seq[HardwareIdentifier] = Seq.empty
                        ): Future[Unit] = {
    val payload = payloadFrom(uri, checksum, length, name, version, hardwareIds, targetFormat)

    val entity = HttpEntity(ContentTypes.`application/json`, payload.noSpaces)

    val req = HttpRequest(HttpMethods.POST,
      uri = apiUri(Path("user_repo") / "targets" / fileName),
      entity = entity)

    execHttpUnmarshalledWithNamespace[Unit](namespace, req).handleErrors(addTargetErrorHandler)
  }

  override def targetExists(namespace: Namespace, targetFilename: TargetFilename): Future[Boolean] = {
    val req = HttpRequest(HttpMethods.HEAD, uri = apiUri(Path("user_repo") / "targets" / targetFilename.value))

    execHttpUnmarshalledWithNamespace[Unit](namespace, req).flatMap {
      case Left(err) if err.status == StatusCodes.NotFound => FastFuture.successful(false)
      case Left(err) => FastFuture.failed(err)
      case Right(_) => FastFuture.successful(true)
    }
  }

  override def fetchTargets(namespace: Namespace): Future[SignedPayload[TargetsRole]] = {
    val req = HttpRequest(HttpMethods.GET, uri = apiUri(Path("user_repo/targets.json")))

    execHttpUnmarshalledWithNamespace[SignedPayload[TargetsRole]](namespace, req).handleErrors {
      case error if error.status == StatusCodes.NotFound =>
        FastFuture.failed(NotFound)
    }
  }

  override protected def execHttpFullWithNamespace[T](namespace: Namespace, request: HttpRequest)
                                                  (implicit ct: ClassTag[T], ev: FromEntityUnmarshaller[T]): Future[ServiceHttpFullResponseEither[T]] = {
    val authReq = authHeaders match {
      case Some(a) => request.addHeader(a)
      case None => request
    }

    super.execHttpFullWithNamespace(namespace, authReq)
  }


  private def payloadFrom(uri: Uri, checksum: Checksum, length: Int, name: Option[TargetName],
                          version: Option[TargetVersion], hardwareIds: Seq[HardwareIdentifier], targetFormat: TargetFormat): Json =
    RequestTargetItem(uri, checksum, Some(targetFormat), name, version, hardwareIds, length).asJson

  override def addTargetFromContent(namespace: Namespace, fileName: String, uri: Option[Uri],
                                    checksum: Checksum, length: Int, targetFormat: TargetFormat, content: Source[ByteString, Any],
                                    name: TargetName, version: TargetVersion,
                                    hardwareIds: Seq[HardwareIdentifier]): Future[Unit] = {
    val params =
      Map(
        "name" -> name.value, "version" -> version.value,
        "targetFormat" -> targetFormat.toString)

    val hwparams =
      if (hardwareIds.isEmpty)
        Map.empty
      else
        Map("hardwareIds" -> hardwareIds.map(_.value).mkString(","))

    val uri = apiUri(Path("user_repo") / "targets" / fileName).withQuery(Query(params ++ hwparams))

    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart("file",
        HttpEntity(ContentTypes.`application/octet-stream`, length, content), Map("filename" -> fileName)))

    Marshal(multipartForm).to[RequestEntity].flatMap { form =>
      val req = HttpRequest(HttpMethods.PUT, uri, entity = form)
      execHttpUnmarshalledWithNamespace[Unit](namespace, req).handleErrors(addTargetErrorHandler)
    }
  }
}
