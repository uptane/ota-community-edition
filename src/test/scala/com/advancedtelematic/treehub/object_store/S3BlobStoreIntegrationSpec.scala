package com.advancedtelematic.treehub.object_store

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.data.DataType.{ObjectId, ObjectIdOps, ObjectStatus, TObject}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.TreeHubSpec
import com.amazonaws.regions.Regions
import org.scalatest.time.{Seconds, Span}

import java.nio.file.Paths
import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class S3BlobStoreIntegrationSpec extends TreeHubSpec {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  implicit lazy val system: ActorSystem = ActorSystem("S3BlobStoreSpec")

  val ns = Namespace("S3BlobStoreIntegrationSpec")

  val s3BlobStore = S3BlobStore(s3Credentials, allowRedirects = false, root = Some(Paths.get("test-objects")))

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(15, Seconds))

  test("can construct custom")  {
    val creds = new S3Credentials("", "", "", Regions.fromName("eu-central-1"), "https://storage.googleapis.com")
    val s3BlobStore = S3BlobStore(creds, allowRedirects = false, root = Some(Paths.get("test-objects")))
  }

  test("can store object")  {
    val tobj = TObject(ns, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte.")

    val source = Source.single(blob)

    val size = s3BlobStore.storeStream(ns, tobj.id.asPrefixedPath, blob.size, source).futureValue

    size shouldBe 13
  }

  test("can retrieve an object") {
    val obj = new ClientTObject()

    val f = async {
      await(s3BlobStore.storeStream(ns, obj.objectId.asPrefixedPath, obj.blob.size, obj.byteSource))
      await(s3BlobStore.readFull(ns, obj.objectId.asPrefixedPath))
    }

    f.futureValue.size shouldBe obj.blob.size
    s3BlobStore.exists(ns, obj.objectId.asPrefixedPath).futureValue shouldBe true
  }

  test("can delete object") {
    val obj = new ClientTObject()

    val f = async {
      await(s3BlobStore.storeStream(ns, obj.objectId.asPrefixedPath, obj.blob.size, obj.byteSource))
    }.futureValue

    s3BlobStore.exists(ns, obj.objectId.asPrefixedPath).futureValue shouldBe true

    s3BlobStore.deleteObject(ns, obj.objectId.asPrefixedPath).futureValue

    s3BlobStore.exists(ns, obj.objectId.asPrefixedPath).futureValue shouldBe false
  }

  test("build response builds a redirect") {
    val redirectS3BlobStore = S3BlobStore(s3Credentials, allowRedirects = true, root = Some(Paths.get("test-objects")))

    val tobj = TObject(ns, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")
    val source = Source.single(blob)

    val response = async {
      await(redirectS3BlobStore.storeStream(ns, tobj.id.asPrefixedPath, blob.size, source))
      await(redirectS3BlobStore.buildResponse(tobj.namespace, tobj.id.asPrefixedPath))
    }.futureValue

    response.status shouldBe StatusCodes.Found
    response.headers.find(_.is("location")).get.value() should include(tobj.id.filename.toString)
  }

  test("build response a response containing the object content") {
    val tobj = TObject(ns, ObjectId.parse("ce720e82a727efa4b30a6ab73cefe31a8d4ec6c0d197d721f07605913d2a279a.commit").toOption.get, 0L, ObjectStatus.UPLOADED)
    val blob = ByteString("this is byte. Call me. maybe.")
    val source = Source.single(blob)

    val response = async {
      await(s3BlobStore.storeStream(ns, tobj.id.asPrefixedPath, blob.size, source))
      await(s3BlobStore.buildResponse(tobj.namespace, tobj.id.asPrefixedPath))
    }.futureValue

    response.status shouldBe StatusCodes.OK
    response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).futureValue.utf8String shouldBe "this is byte. Call me. maybe."
  }

  test("correctly deletes objects from blob storage") {}
    val basePath = "some_base_path"
    val pathsWithBase = Seq(s"$basePath/superblock", s"$basePath/0", s"$basePath/1", s"$basePath/2/123", s"$basePath/random")
    val otherPaths = Seq("some_other_path/superblock", "some_path/1")

    (pathsWithBase ++ otherPaths).foreach { pathString =>
      val blob = ByteString(s"this is byte for path $pathString")
      val source = Source.single(blob)
      val path = Paths.get(pathString)

      s3BlobStore.storeStream(ns, path, blob.size, source).futureValue shouldBe blob.size
      s3BlobStore.exists(ns, path).futureValue shouldBe true
    }

    s3BlobStore.deleteObjects(ns, Paths.get(basePath)).futureValue shouldBe Done

    pathsWithBase.foreach { pathString =>
      s3BlobStore.exists(ns, Paths.get(pathString)).futureValue shouldBe false
    }
    otherPaths.foreach { pathString =>
      s3BlobStore.exists(ns, Paths.get(pathString)).futureValue shouldBe true
    }
  }
