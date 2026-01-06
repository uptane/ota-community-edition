package com.advancedtelematic.tuf.reposerver.http

import java.net.URL

import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{StatusCodes, Uri}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.http.Errors.RawError
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{
  CompleteUploadRequest,
  ETag,
  GetSignedUrlResult,
  InitMultipartUploadResult,
  MultipartUploadId,
  RepoId,
  TargetFilename,
  UploadPartETag
}
import com.advancedtelematic.tuf.reposerver.Settings
import com.advancedtelematic.tuf.reposerver.target_store.TargetStoreEngine.{
  TargetRetrieveResult,
  TargetStoreResult
}
import com.advancedtelematic.tuf.reposerver.target_store.{TargetStore, TargetStoreEngine}
import com.advancedtelematic.tuf.reposerver.util.{
  RepoResourceSpecUtil,
  ResourceSpec,
  TufReposerverSpec
}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._

import scala.concurrent.Future

class RepoResourceMultipartUploadSpec
    extends TufReposerverSpec
    with ResourceSpec
    with RepoResourceSpecUtil
    with Settings {

  private def namespaceHeader(namespace: Namespace): RawHeader =
    RawHeader("x-ats-namespace", namespace.get)

  private val testInitMultipartUploadResult =
    InitMultipartUploadResult(MultipartUploadId("uploadID"), partSize = multipartUploadPartSize)

  private val testSignedURLResult = GetSignedUrlResult(new URL("https://fake.s3.url.com"))

  private val fakeStorage: TargetStoreEngine = new TargetStoreEngine {

    private val DefaultMockResultForNotUsedMethod =
      Future.failed(new Exception("Default mock result"))

    override def storeStream(repoId: RepoId,
                             filename: TargetFilename,
                             fileData: Source[ByteString, Any],
                             size: Long): Future[TargetStoreResult] =
      DefaultMockResultForNotUsedMethod

    override def store(repoId: RepoId,
                       filename: TargetFilename,
                       fileData: Source[ByteString, Any]): Future[TargetStoreResult] =
      DefaultMockResultForNotUsedMethod

    override def buildStorageUri(repoId: RepoId,
                                 filename: TargetFilename,
                                 length: Long): Future[Uri] =
      DefaultMockResultForNotUsedMethod

    override def retrieve(repoId: RepoId, filename: TargetFilename): Future[TargetRetrieveResult] =
      DefaultMockResultForNotUsedMethod

    override def delete(repoId: RepoId, filename: TargetFilename): Future[Unit] =
      DefaultMockResultForNotUsedMethod

    override def initiateMultipartUpload(
      repoId: RepoId,
      filename: TargetFilename): Future[InitMultipartUploadResult] =
      Future.successful(testInitMultipartUploadResult)

    override def buildSignedURL(repoId: RepoId,
                                filename: TargetFilename,
                                uploadId: MultipartUploadId,
                                partNumber: String,
                                md5: String,
                                contentLength: Int): Future[GetSignedUrlResult] =
      Future.successful(testSignedURLResult)

    override def completeMultipartUpload(repoId: RepoId,
                                         filename: TargetFilename,
                                         uploadId: MultipartUploadId,
                                         partETags: Seq[UploadPartETag]): Future[Unit] =
      Future.unit

  }

  private lazy val fakeTargetStore: TargetStore =
    new TargetStore(fakeKeyserverClient, fakeStorage, fakeHttpClient, messageBusPublisher)

  override lazy val routes: Route = new TufReposerverRoutes(
    fakeKeyserverClient,
    namespaceValidation,
    fakeTargetStore,
    messageBusPublisher,
    fakeRemoteDelegationClient
  ).routes

  def createRepo(): Namespace = {
    val namespace = Namespace.generate
    Post(apiUri(s"user_repo")).addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
    namespace
  }

  test("multipart upload flow work correct") {
    val namespace = createRepo()

    val rs = Post(apiUri(s"user_repo/multipart/initiate/testFile.bin?fileSize=10000000"))
      .addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[InitMultipartUploadResult] shouldBe testInitMultipartUploadResult
      entityAs[InitMultipartUploadResult]
    }

    Get(
      apiUri(
        s"user_repo/multipart/url/testFile.bin?part=1&uploadId=${rs.uploadId.value}&contentLength=10000000&md5=hash"
      )
    )
      .addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[GetSignedUrlResult] shouldBe testSignedURLResult
    }

    Put(
      apiUri(s"user_repo/multipart/complete/testFile.bin"),
      CompleteUploadRequest(
        testInitMultipartUploadResult.uploadId,
        Seq(UploadPartETag(1, ETag("testETag")))
      )
    )
      .addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("init multipart upload fail if file size is too large") {
    val namespace = createRepo()

    val testFileSize = outOfBandUploadLimit + 1

    Post(apiUri(s"user_repo/multipart/initiate/testFile.bin?fileSize=$testFileSize"))
      .addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.PayloadTooLarge
      entityAs[
        ErrorRepresentation
      ].description shouldBe s"File being uploaded is too large ($testFileSize), maximum size is $outOfBandUploadLimit"
    }
  }

  test("get signed URL should fail if part size is too large") {
    val namespace = createRepo()

    val testPartSize = multipartUploadPartSize + 1

    Get(
      apiUri(
        s"user_repo/multipart/url/testFile.bin?part=1&uploadId=uploadIdValue&contentLength=$testPartSize&md5=hash"
      )
    )
      .addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.PayloadTooLarge
      entityAs[
        ErrorRepresentation
      ].description shouldBe s"Part of the file being uploaded is too large ($testPartSize), maximum part size for multipart upload is $multipartUploadPartSize"
    }
  }

  test("get signed URL should fail if total calculated file size is too large") {
    val namespace = createRepo()

    val totalFileSize = outOfBandUploadLimit + 10
    val testPartNumber = totalFileSize / multipartUploadPartSize + 1
    val lastPartSize = totalFileSize % multipartUploadPartSize

    Get(
      apiUri(
        s"user_repo/multipart/url/testFile.bin?part=$testPartNumber&uploadId=uploadIdValue&contentLength=$lastPartSize&md5=hash"
      )
    )
      .addHeader(namespaceHeader(namespace)) ~> routes ~> check {
      status shouldBe StatusCodes.PayloadTooLarge
      entityAs[
        ErrorRepresentation
      ].description shouldBe s"File being uploaded is too large ($totalFileSize), maximum size is $outOfBandUploadLimit"
    }
  }

}
