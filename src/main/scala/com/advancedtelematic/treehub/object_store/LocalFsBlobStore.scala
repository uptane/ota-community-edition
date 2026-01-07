package com.advancedtelematic.treehub.object_store

import akka.Done
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.http.Errors
import org.slf4j.LoggerFactory

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object LocalFsBlobStore {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def apply(root: Path)(implicit mat: Materializer, ec: ExecutionContext): LocalFsBlobStore = {
    if(!root.toFile.exists() && !root.getParent.toFile.canWrite) {
      throw new IllegalArgumentException(s"Could not open $root as local blob store")
    } else if (!root.toFile.exists()) {
      Files.createDirectories(root)
      _log.info(s"Created local fs blob store directory: $root")
    }

    _log.info(s"local fs blob store set to $root")
    new LocalFsBlobStore(root)
  }
}

class LocalFsBlobStore(root: Path)(implicit ec: ExecutionContext, mat: Materializer) extends BlobStore {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def store(ns: Namespace, path: Path, blob: Source[ByteString, ?]): Future[(Path, Long)] = {
    val _path = objectPath(ns, path)
    ensureDirExists(_path)

    async {
      val b = await(blob.runReduce(_ ++ _))
      Files.write(_path, b.toArray)
      path -> b.size.toLong
    }
  }

  override def storeStream(namespace: Namespace, path: Path, size: Long, blob: Source[ByteString, ?]): Future[Long] =
    store(namespace, path, blob).map(_._2)

  override def storeOutOfBand(namespace: Namespace, path: Path): Future[BlobStore.OutOfBandStoreResult] =
    FastFuture.failed(Errors.OutOfBandStorageNotSupported)

  override def buildResponse(ns: Namespace, path: Path): Future[HttpResponse] = {
    exists(ns, path).flatMap {
      case true => FastFuture.successful {
        buildResponseFromBytes(FileIO.fromPath(objectPath(ns, path)))
      }
      case false => Future.failed(Errors.BlobNotFound)
    }
  }

  override def readFull(ns: Namespace, path: Path): Future[ByteString] = {
    buildResponse(ns, path).flatMap { response =>
      val dataBytes = response.entity.dataBytes
      dataBytes.runFold(ByteString.empty)(_ ++ _)
    }
  }

  override def exists(ns: Namespace, path: Path): Future[Boolean] = FastFuture.successful {
    val _path = objectPath(ns, path)
    Files.exists(_path)
  }

  private def objectPath(ns: Namespace, path: Path): Path = {
    val p = root.toAbsolutePath.resolve(ns.get).resolve(path)

    if (Files.notExists(p.getParent))
      Files.createDirectories(p.getParent)

    p
  }

  override val supportsOutOfBandStorage: Boolean = false

  override def deleteObject(ns: Namespace, path: Path): Future[Done] = FastFuture.successful {
    val p = objectPath(ns, path)

    Files.delete(p)
    Done
  }

  override def deleteObjects(ns: Namespace, pathPrefix: Path): Future[Done] = FastFuture.successful {
    val p = objectPath(ns, pathPrefix)

    log.info(s">>>> DELETE objects in path recursively: $p")

    Files.walkFileTree(
      p,
      new SimpleFileVisitor[Path] {
        override def visitFile(
                                file: Path,
                                attrs: BasicFileAttributes
                              ): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(
                                         dir: Path,
                                         exc: IOException
                                       ): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      }
    )

    Done
  }

  private def ensureDirExists(path: Path) =
    Try(Files.createDirectories(path.getParent)).failed.foreach { ex =>
      log.warn(s"Could not create directories: $path", ex)
    }
}
