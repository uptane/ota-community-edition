package com.advancedtelematic.treehub.object_store

import akka.Done
import akka.http.scaladsl.model.*
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.object_store.BlobStore.OutOfBandStoreResult

import java.nio.file.Path
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object BlobStore {
  sealed trait OutOfBandStoreResult
  case class UploadAt(uri: Uri) extends OutOfBandStoreResult
}

trait BlobStore {
  def deleteObject(ns: Namespace, path: Path): Future[Done]

  def deleteObjects(ns: Namespace, pathPrefix: Path): Future[Done]

  def storeStream(namespace: Namespace, path: Path, size: Long, blob: Source[ByteString, _]): Future[Long]

  val supportsOutOfBandStorage: Boolean

  def storeOutOfBand(namespace: Namespace, path: Path): Future[OutOfBandStoreResult]

  def buildResponse(namespace: Namespace, path: Path): Future[HttpResponse]

  def readFull(namespace: Namespace, path: Path): Future[ByteString]

  def exists(namespace: Namespace, path: Path): Future[Boolean]

  protected def buildResponseFromBytes(source: Source[ByteString, ?]): HttpResponse = {
    val entity = HttpEntity(MediaTypes.`application/octet-stream`, source)
    HttpResponse(StatusCodes.OK, entity = entity)
  }
}
