package com.advancedtelematic.tuf.reposerver.target_store

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import org.apache.pekko.stream.{Materializer, SinkShape}
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libtuf.data.TufDataType
import com.advancedtelematic.libtuf.data.TufDataType.{MultipartUploadId, RepoId, TargetFilename}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.tuf.reposerver.http.Errors
import com.advancedtelematic.tuf.reposerver.target_store.AzureTargetStoreEngine.BlobStorageSettings
import com.azure.storage.blob.sas.{BlobSasPermission, BlobServiceSasSignatureValues}
import com.azure.storage.blob.specialized.BlockBlobAsyncClient
import com.azure.storage.blob.{
  BlobClientBuilder,
  BlobContainerAsyncClient,
  BlobContainerClientBuilder
}
import org.slf4j.LoggerFactory
import reactor.core.publisher.{Flux, Mono}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.{Base64, UUID}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object AzureTargetStoreEngine {

  final case class BlobStorageSettings(connectionString: String, signatureTtl: FiniteDuration)

}

class AzureTargetStoreEngine(private val settings: BlobStorageSettings)(
  implicit mat: Materializer,
  ec: ExecutionContext)
    extends TargetStoreEngine {

  import scala.jdk.FutureConverters._
  import scala.concurrent.duration._

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private[this] val GroupWithinBytes = 1024 * 1024 * 25 // 25Mb
  private[this] val GroupWithinTime = 2.seconds

  private[this] def containerClientFor(repoId: TufDataType.RepoId): BlobContainerAsyncClient =
    new BlobContainerClientBuilder()
      .connectionString(settings.connectionString)
      .containerName(containerName(repoId))
      .buildAsyncClient()

  private[this] def genBlockId(): String =
    Base64.getEncoder.encodeToString(UUID.randomUUID.toString.getBytes(StandardCharsets.UTF_8))

  private[this] def uploadBlock(blobClient: BlockBlobAsyncClient)(
    data: ByteString): Future[String] = {
    val blockId = genBlockId()
    blobClient
      .stageBlock(blockId, Flux.just[ByteBuffer](data.toByteBuffer), data.length.toLong)
      .toFuture
      .asScala
      .map(_ => blockId)
  }

  def upload(blobClient: BlockBlobAsyncClient)
    : Sink[ByteString, Future[TargetStoreEngine.TargetStoreResult]] = {

    val checksumSink = Flow[ByteString].toMat(Sha256Digest.asSink)(Keep.right)
    val lengthSink = Flow[ByteString].toMat(Sink.fold(0)(_ + _.length))(Keep.right)
    val uploadSink = Flow[ByteString]
      .mapAsync(1)(uploadBlock(blobClient))
      .fold(ListBuffer.empty[String])((xs, x) => xs += x)
      .mapAsync(1) { xs =>
        import scala.jdk.CollectionConverters._
        blobClient.commitBlockList(xs.asJava).toFuture.asScala
      }
      .toMat(Sink.head)(Keep.right)

    val combinedSink =
      Sink.fromGraph(GraphDSL.createGraph(checksumSink, lengthSink, uploadSink)(Tuple3.apply) {
        implicit builder => (checksumS, lengthS, uploadS) =>
          import GraphDSL.Implicits._
          val broadcast = builder.add(Broadcast[ByteString](3))
          broadcast.out(0) ~> checksumS
          broadcast.out(1) ~> lengthS
          broadcast.out(2) ~> uploadS
          SinkShape(broadcast.in)
      })

    Flow[ByteString]
      .groupedWeightedWithin(GroupWithinBytes, GroupWithinTime)(_.size)
      .map(_.fold(ByteString.empty)(_.concat(_)))
      .toMat(combinedSink)(Keep.right)
      .mapMaterializedValue { case (checksumF, lengthF, uploadResultF) =>
        for {
          _ <- uploadResultF
          cs <- checksumF
          ln <- lengthF
        } yield TargetStoreEngine.TargetStoreResult(Uri(blobClient.getBlobUrl), cs, ln)
      }
  }

  override def storeStream(repoId: TufDataType.RepoId,
                           filename: TargetFilename,
                           fileData: Source[ByteString, Any],
                           size: Long): Future[TargetStoreEngine.TargetStoreResult] =
    store(repoId, filename, fileData)

  override def store(
    repoId: TufDataType.RepoId,
    filename: TargetFilename,
    fileData: Source[ByteString, Any]): Future[TargetStoreEngine.TargetStoreResult] = {
    val containerClient = containerClientFor(repoId)
    val blobClient = containerClient.getBlobAsyncClient(blobName(filename)).getBlockBlobAsyncClient
    ensureContainerExists(containerClient).flatMap { _ =>
      fileData.toMat(upload(blobClient))(Keep.right).run()
    }
  }

  def ensureContainerExists(containerClient: BlobContainerAsyncClient): Future[Unit] =
    containerClient
      .exists()
      .flatMap { exists =>
        if (exists) {
          Mono.empty[Unit]()
        } else {
          containerClient.create().map[Unit](_ => ())
        }
      }
      .toFuture
      .asScala

  private[this] def containerName(repoId: TufDataType.RepoId): String =
    repoId.uuid.toString

  private[this] def blobName(filename: TargetFilename): String =
    Sha256Digest.digest(filename.value.getBytes).hash.value

  private[this] def signatureExpiryTime(): OffsetDateTime =
    OffsetDateTime.now().plusSeconds(settings.signatureTtl.toSeconds)

  override def buildStorageUri(repoId: TufDataType.RepoId,
                               filename: TargetFilename,
                               length: Long): Future[Uri] = {
    val containerClient = new BlobContainerClientBuilder()
      .connectionString(settings.connectionString)
      .containerName(containerName(repoId))
      .buildAsyncClient()
    ensureContainerExists(containerClient).map { _ =>
      val blobClient = containerClient.getBlobAsyncClient(blobName(filename))
      val sasPermissions =
        new BlobSasPermission().setCreatePermission(true).setWritePermission(true)
      val signatureValues = new BlobServiceSasSignatureValues(signatureExpiryTime(), sasPermissions)
      val signature = blobClient.generateSas(signatureValues)
      Uri(blobClient.getBlobUrl).withRawQueryString(signature)
    }
  }

  private[this] def mkBlobClient(repoId: TufDataType.RepoId,
                                 filename: TargetFilename): BlockBlobAsyncClient =
    new BlobClientBuilder()
      .connectionString(settings.connectionString)
      .containerName(containerName(repoId))
      .blobName(blobName(filename))
      .buildAsyncClient()
      .getBlockBlobAsyncClient

  override def retrieve(
    repoId: TufDataType.RepoId,
    filename: TargetFilename): Future[TargetStoreEngine.TargetRetrieveResult] = {
    val blobClient = mkBlobClient(repoId, filename)

    val sasPermissions = new BlobSasPermission().setReadPermission(true)
    val signatureValues = new BlobServiceSasSignatureValues(signatureExpiryTime(), sasPermissions)
    val signature = blobClient.generateSas(signatureValues)
    val uri = Uri(blobClient.getBlobUrl).withRawQueryString(signature)
    FastFuture.successful(TargetStoreEngine.TargetRedirect(uri))
  }

  override def delete(repoId: TufDataType.RepoId, filename: TargetFilename): Future[Unit] =
    mkBlobClient(repoId, filename).delete().toFuture.asScala.map(_ => ())

  private lazy val multipartUploadIsNotSupportedError =
    FastFuture.failed(
      Errors.NotImplemented("Multipart upload is not supported by Azure blob storage")
    )

  override def initiateMultipartUpload(
    repoId: RepoId,
    filename: TargetFilename): Future[TufDataType.InitMultipartUploadResult] =
    multipartUploadIsNotSupportedError

  override def buildSignedURL(repoId: RepoId,
                              filename: TargetFilename,
                              uploadId: MultipartUploadId,
                              partNumber: String,
                              md5: String,
                              contentLength: Int): Future[TufDataType.GetSignedUrlResult] =
    multipartUploadIsNotSupportedError

  override def completeMultipartUpload(repoId: RepoId,
                                       filename: TargetFilename,
                                       uploadId: TufDataType.MultipartUploadId,
                                       partETags: Seq[TufDataType.UploadPartETag]): Future[Unit] =
    multipartUploadIsNotSupportedError

}
