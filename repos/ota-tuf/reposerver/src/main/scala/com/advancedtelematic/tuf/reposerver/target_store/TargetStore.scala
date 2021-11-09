package com.advancedtelematic.tuf.reposerver.target_store

import java.net.URL
import java.time.Instant
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libtuf.data.ClientDataType.TargetCustom
import com.advancedtelematic.libtuf.data.TufDataType.{GetSignedUrlResult, InitMultipartUploadResult, MultipartUploadId, RepoId, TargetFilename, UploadPartETag}
import com.advancedtelematic.libtuf_server.data.Messages.PackageStorageUsage
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.tuf.reposerver.db.TargetItemRepositorySupport
import com.advancedtelematic.tuf.reposerver.target_store.TargetStoreEngine.{TargetBytes, TargetRedirect}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.Database
import com.advancedtelematic.libtuf_server.repo.server.DataType._
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType._
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.TargetItem
import com.advancedtelematic.tuf.reposerver.http.Errors
import cats.implicits._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

object TargetStore {
  def apply(roleKeyStore: KeyserverClient,
            targetStoreEngine: TargetStoreEngine,
            messageBusPublisher: MessageBusPublisher)
           (implicit db: Database, ec: ExecutionContext, system: ActorSystem, mat: Materializer): TargetStore = {
    val _http = Http()
    new TargetStore(roleKeyStore, targetStoreEngine, req => _http.singleRequest(req), messageBusPublisher)
  }
}

class TargetStore(roleKeyStore: KeyserverClient,
                  engine: TargetStoreEngine,
                  httpClient: HttpRequest => Future[HttpResponse],
                  messageBusPublisher: MessageBusPublisher)
                 (implicit val db: Database, val ec: ExecutionContext)
  extends TargetItemRepositorySupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  case class DownloadError(msg: String) extends Exception(msg) with NoStackTrace

  def publishUploadMessages(repoId: RepoId): Future[Unit] = {
    val f = for {
      (namespace, usage) <- targetItemRepo.usage(repoId)
      _ <- messageBusPublisher.publish(PackageStorageUsage(namespace.get, Instant.now, usage))
    } yield ()

    f.handleError { ex =>
      _log.warn("Could not publish bus message", ex)
    }
  }

  def store(repoId: RepoId, targetFile: TargetFilename, fileData: Source[ByteString, Any], custom: TargetCustom, size: Option[Long]): Future[TargetItem] = {
    for {
      storeResult <-
        size match {
          case Some(size) => engine.storeStream(repoId, targetFile, fileData, size)
          case _ => engine.store(repoId, targetFile, fileData)
        }
      _ <- publishUploadMessages(repoId)
    } yield TargetItem(repoId, targetFile, storeResult.uri.some, storeResult.checksum, storeResult.size, Some(custom))
  }

  def storeFromUri(repoId: RepoId, targetFile: TargetFilename, fileUri: Uri, custom: TargetCustom): Future[TargetItem] = {
    httpClient(HttpRequest(uri = fileUri)).flatMap { response =>
      response.status match {
        case StatusCodes.OK => store(repoId, targetFile, response.entity.dataBytes, custom, size = None)
        case _ => Future.failed(DownloadError("Unable to download file " + fileUri.toString))
      }
    }
  }

  def buildStorageUrl(repoId: RepoId, targetFilename: TargetFilename, length: Long): Future[Uri] =
    engine.buildStorageUri(repoId, targetFilename, length)

  def initiateMultipartUpload(repoId: RepoId, filename: TargetFilename): Future[InitMultipartUploadResult] = {
    engine.initiateMultipartUpload(repoId, filename)
  }

  def buildSignedURL(repoId: RepoId, filename: TargetFilename, uploadId: MultipartUploadId, partNumber: String, md5: String, contentLength: Int): Future[GetSignedUrlResult] = {
    engine.buildSignedURL(repoId, filename, uploadId, partNumber, md5, contentLength)
  }

  def completeMultipartUpload(repoId: RepoId, filename: TargetFilename, uploadId: MultipartUploadId, partETags: Seq[UploadPartETag]): Future[Unit] = {
    engine.completeMultipartUpload(repoId, filename, uploadId, partETags)
  }

  def retrieve(repoId: RepoId, targetFilename: TargetFilename): Future[HttpResponse] = {
    targetItemRepo.findByFilename(repoId, targetFilename).flatMap { item =>
      item.storageMethod match {
        case StorageMethod.Managed | StorageMethod.CliManaged =>
          retrieveFromManaged(repoId, targetFilename)
        case StorageMethod.Unmanaged if item.uri.isDefined =>
          redirectToUnmanaged(item.uri.get)
        case StorageMethod.Unmanaged =>
          FastFuture.failed(Errors.NoUriForUnamanagedTarget)
      }
    }
  }

  def find(repoId: RepoId, targetFilename: TargetFilename): Future[TargetItem] = {
    targetItemRepo.findByFilename(repoId, targetFilename)
  }

  def delete(repoId: RepoId, filename: TargetFilename): Future[Unit] =
    targetItemRepo.findByFilename(repoId, filename).flatMap(delete)

  def delete(item: TargetItem): Future[Unit] = {
    item.storageMethod match {
      case StorageMethod.Managed | StorageMethod.CliManaged =>
        engine.delete(item.repoId, item.filename)
      case StorageMethod.Unmanaged =>
        FastFuture.successful(())
    }
  }

  private def redirectToUnmanaged(uri: Uri): Future[HttpResponse] = FastFuture.successful {
    HttpResponse(StatusCodes.Found, List(Location(uri)))
  }

  private def retrieveFromManaged(repoId: RepoId, targetFilename: TargetFilename): Future[HttpResponse] = {
    // TODO: Publish usage/inflight https://advancedtelematic.atlassian.net/browse/PRO-2803
    engine.retrieve(repoId, targetFilename).map {
      case TargetBytes(bytes, size) =>
        val entity = HttpEntity(MediaTypes.`application/octet-stream`, size, bytes)
        HttpResponse(StatusCodes.OK, entity = entity)

      case TargetRedirect(uri) =>
        HttpResponse(StatusCodes.Found, List(Location(uri)))
    }
  }
}
