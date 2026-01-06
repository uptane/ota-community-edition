package com.advancedtelematic.tuf.reposerver.target_store

import java.nio.file.{Path, Paths}

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.TufDataType.{
  GetSignedUrlResult,
  InitMultipartUploadResult,
  MultipartUploadId,
  RepoId,
  TargetFilename,
  UploadPartETag
}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.tuf.reposerver.target_store.TargetStoreEngine.{
  TargetRetrieveResult,
  TargetStoreResult
}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object TargetStoreEngine {
  case class TargetStoreResult(uri: Uri, checksum: Checksum, size: Long)

  sealed trait TargetRetrieveResult
  case class TargetRedirect(uri: Uri) extends TargetRetrieveResult

  case class TargetBytes(bytes: Source[ByteString, Future[Done]], size: Long)
      extends TargetRetrieveResult

}

trait TargetStoreEngine {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def storeStream(repoId: RepoId,
                  filename: TargetFilename,
                  fileData: Source[ByteString, Any],
                  size: Long): Future[TargetStoreResult]

  def store(repoId: RepoId,
            filename: TargetFilename,
            fileData: Source[ByteString, Any]): Future[TargetStoreResult]

  def buildStorageUri(repoId: RepoId, filename: TargetFilename, length: Long): Future[Uri]

  def initiateMultipartUpload(repoId: RepoId,
                              filename: TargetFilename): Future[InitMultipartUploadResult]

  def buildSignedURL(repoId: RepoId,
                     filename: TargetFilename,
                     uploadId: MultipartUploadId,
                     partNumber: String,
                     md5: String,
                     contentLength: Int): Future[GetSignedUrlResult]

  def completeMultipartUpload(repoId: RepoId,
                              filename: TargetFilename,
                              uploadId: MultipartUploadId,
                              partETags: Seq[UploadPartETag]): Future[Unit]

  def retrieve(repoId: RepoId, filename: TargetFilename): Future[TargetRetrieveResult]

  def delete(repoId: RepoId, filename: TargetFilename): Future[Unit]

  protected def write(fileData: Source[ByteString, Any],
                      writeSink: Sink[ByteString, Future[(Uri, Long)]])(
    implicit system: ActorSystem,
    mat: Materializer): Future[TargetStoreResult] = {
    implicit val ec = system.dispatcher
    val digestCalculator = Sha256Digest.asSink

    val (digestF, resultF) = fileData
      .alsoToMat(digestCalculator)(Keep.right)
      .toMat(writeSink)(Keep.both)
      .run()

    val writeAsync = for {
      digest <- digestF
      (uri, sizeBytes) <- resultF
    } yield TargetStoreResult(uri, digest, sizeBytes)

    writeAsync.andThen(logResult)
  }

  private val logResult: PartialFunction[Try[TargetStoreResult], Unit] = {
    case Success(packageStoreResult) =>
      _log.debug(s"Package $packageStoreResult uploaded")
    case Failure(t) =>
      _log.error(s"Failed to write package", t)
  }

  protected def storageFilename(repoId: RepoId, targetFilename: TargetFilename): Path = {
    val prefixHash = Sha256Digest.digest(repoId.uuid.toString.getBytes).hash.value

    val fileNameHash = Sha256Digest.digest(targetFilename.value.getBytes).hash.value

    val (prefix, dir) = fileNameHash.splitAt(2)

    Paths.get(prefixHash, prefix, dir)
  }

}
