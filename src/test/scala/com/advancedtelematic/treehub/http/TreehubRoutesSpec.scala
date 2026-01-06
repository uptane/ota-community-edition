package com.advancedtelematic.treehub.http

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.advancedtelematic.data.DataType.ObjectId
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.treehub.object_store.{BlobStore, ObjectStore}
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import com.amazonaws.SdkClientException

import java.nio.file.Path
import scala.concurrent.Future

class TreehubRoutesSpec extends TreeHubSpec with ResourceSpec {

  val errorBlobStore = new BlobStore {
    override def deleteObject(ns: DataType.Namespace, path: Path): Future[Done] =
      FastFuture.failed(new RuntimeException("[test] delete failed"))

    override def deleteObjects(ns: DataType.Namespace, pathPrefix: Path): Future[Done] =
      FastFuture.failed(new RuntimeException("[test] delete failed"))

    override def storeStream(namespace: DataType.Namespace, path: Path, size: Long, blob: Source[ByteString, _]): Future[Long] =
      ???

    override val supportsOutOfBandStorage: Boolean = false

    override def storeOutOfBand(namespace: DataType.Namespace, path: Path): Future[BlobStore.OutOfBandStoreResult] = ???

    override def buildResponse(namespace: DataType.Namespace, path: Path): Future[HttpResponse] = ???

    override def readFull(namespace: DataType.Namespace, path: Path): Future[ByteString] = ???

    override def exists(namespace: DataType.Namespace, path: Path): Future[Boolean] =
      FastFuture.failed(new SdkClientException("Timeout on waiting"))

  }

  val errorObjectStore = new ObjectStore(errorBlobStore)

  override lazy val routes = new TreeHubRoutes(
    namespaceExtractor, messageBus, errorObjectStore, deltas, fakeUsageUpdate).routes

  test("returns 408 when an aws.SdkClientException is thrown") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.RequestTimeout
    }
  }
}
