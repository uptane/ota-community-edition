package com.advancedtelematic.treehub.daemon

import akka.stream.scaladsl.Source
import akka.testkit.TestException
import akka.util.ByteString
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.treehub.object_store.BlobStore

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
  override def deleteObject(ns: DataType.Namespace, path: Path) = Future { throw TestException("failed") }
  override def deleteObjects(ns: DataType.Namespace, pathPrefix: Path) = Future {
    if (failingPaths.contains(pathPrefix))
      throw TestException("failed")
    else
      akka.Done
  }
  override def storeStream(namespace: DataType.Namespace, path: Path, size: Long, blob: Source[ByteString, _]) = Future { 123L }
  override val supportsOutOfBandStorage = false
  override def storeOutOfBand(namespace: DataType.Namespace, path: Path) = Future { throw TestException("failed") }
  override def buildResponse(namespace: DataType.Namespace, path: Path) = Future { throw TestException("failed") }
  override def readFull(namespace: DataType.Namespace, path: Path) = Future { throw TestException("failed") }
  override def exists(namespace: DataType.Namespace, path: Path) = Future { throw TestException("failed") }
}