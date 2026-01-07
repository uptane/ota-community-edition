package com.advancedtelematic.treehub.daemon

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.testkit.TestException
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.treehub.object_store.BlobStore
import com.advancedtelematic.treehub.object_store.BlobStore.OutOfBandStoreResult
import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.HttpResponse

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

class MockBlobStore(implicit ec: ExecutionContext) extends BlobStore {
  private var failingPaths = Seq[Path]()
  def addPathToFailingPaths(path: Path): Unit = {
    failingPaths = failingPaths :+ path
  }

  def resetFailingPaths(): Unit = {
    failingPaths = Seq()
  }

  // BlobStore trait overrides
  override def deleteObject(ns: DataType.Namespace, path: Path): Future[Done] = Future { throw TestException("failed") }
  override def deleteObjects(ns: DataType.Namespace, pathPrefix: Path): Future[Done] = Future {
    if (failingPaths.contains(pathPrefix))
      throw TestException("failed")
    else
      Done
  }
  override def storeStream(namespace: DataType.Namespace, path: Path, size: Long, blob: Source[ByteString, _]): Future[Long] = Future { 123L }
  override val supportsOutOfBandStorage = false
  override def storeOutOfBand(namespace: DataType.Namespace, path: Path): Future[OutOfBandStoreResult] = Future { throw TestException("failed") }
  override def buildResponse(namespace: DataType.Namespace, path: Path): Future[HttpResponse] = Future { throw TestException("failed") }
  override def readFull(namespace: DataType.Namespace, path: Path): Future[ByteString] = Future { throw TestException("failed") }
  override def exists(namespace: DataType.Namespace, path: Path): Future[Boolean] = Future { throw TestException("failed") }
}