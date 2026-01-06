package com.advancedtelematic.tuf.reposerver.target_store

import java.io.File
import java.nio.file.{FileAlreadyExistsException, Files}
import java.nio.file.attribute.PosixFilePermission._
import java.nio.file.attribute.PosixFilePermissions

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source}
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libtuf.data.TufDataType
import com.advancedtelematic.libtuf.data.TufDataType.{MultipartUploadId, RepoId, TargetFilename}
import com.advancedtelematic.tuf.reposerver.http.Errors
import com.advancedtelematic.tuf.reposerver.target_store.TargetStoreEngine.{
  TargetBytes,
  TargetRetrieveResult,
  TargetStoreResult
}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.Try

object LocalTargetStoreEngine {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def apply(
    root: String)(implicit system: ActorSystem, mat: Materializer): LocalTargetStoreEngine = {
    val f = new File(root)
    if (!f.exists() && !f.getParentFile.canWrite) {
      throw new IllegalArgumentException(s"Could not open $root as local target store")
    } else if (!f.exists()) {
      Files.createDirectory(f.toPath)
      _log.info(s"Created local fs blob store directory: $root")
    }

    _log.info(s"local fs blob store set to $root")
    new LocalTargetStoreEngine(f)
  }

}

class LocalTargetStoreEngine(root: File)(implicit val system: ActorSystem, val mat: Materializer)
    extends TargetStoreEngine {
  import system.dispatcher

  override def store(repoId: RepoId,
                     filename: TargetFilename,
                     fileData: Source[ByteString, Any]): Future[TargetStoreResult] = {
    val sink = localFileSink(repoId, filename, fileData)
    write(fileData, sink)
  }

  override def storeStream(repoId: RepoId,
                           filename: TargetFilename,
                           fileData: Source[ByteString, Any],
                           size: Long): Future[TargetStoreResult] =
    store(repoId, filename, fileData)

  override def retrieve(repoId: RepoId, filename: TargetFilename): Future[TargetRetrieveResult] = {
    val storePath = root.toPath.resolve(storageFilename(repoId, filename))

    if (!storePath.toFile.canRead)
      Future.failed(Errors.TargetNotFoundError)
    else {
      val size = storePath.toFile.length()

      val source = FileIO.fromPath(storePath).mapMaterializedValue(_ => FastFuture.successful(Done))

      Future.successful(TargetBytes(source, size))
    }
  }

  override def delete(repoId: RepoId, filename: TargetFilename): Future[Unit] = Future.fromTry {
    Try {
      val storePath = root.toPath.resolve(storageFilename(repoId, filename))
      Files.delete(storePath)
    }
  }

  protected def localFileSink(
    repoId: RepoId,
    filename: TargetFilename,
    fileData: Source[ByteString, Any]): Sink[ByteString, Future[(Uri, Long)]] = {
    val storePath = root.toPath.resolve(storageFilename(repoId, filename))

    Files.createDirectories(storePath.getParent)

    val uri = Uri(storePath.toAbsolutePath.toString)

    try
      Files.createFile(
        storePath,
        PosixFilePermissions.asFileAttribute(java.util.EnumSet.of(OWNER_READ, OWNER_WRITE))
      )
    catch { case _: FileAlreadyExistsException => () }

    FileIO.toPath(storePath).mapMaterializedValue {
      _.map { result =>
        uri -> result.count
      }
    }
  }

  private lazy val notSupportedForLocalStorageError =
    FastFuture.failed(
      new IllegalArgumentException(
        "out of band storage of target is not supported for local storage"
      )
    )

  override def buildStorageUri(repoId: RepoId,
                               filename: TargetFilename,
                               length: Long): Future[Uri] =
    notSupportedForLocalStorageError

  override def initiateMultipartUpload(
    repoId: RepoId,
    filename: TargetFilename): Future[TufDataType.InitMultipartUploadResult] =
    notSupportedForLocalStorageError

  override def buildSignedURL(repoId: RepoId,
                              filename: TargetFilename,
                              uploadId: MultipartUploadId,
                              partNumber: String,
                              md5: String,
                              contentLength: Int): Future[TufDataType.GetSignedUrlResult] =
    notSupportedForLocalStorageError

  override def completeMultipartUpload(repoId: RepoId,
                                       filename: TargetFilename,
                                       uploadId: TufDataType.MultipartUploadId,
                                       partETags: Seq[TufDataType.UploadPartETag]): Future[Unit] =
    notSupportedForLocalStorageError

}
