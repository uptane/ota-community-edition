package com.advancedtelematic.tuf.reposerver.target_store
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import java.util.{Base64, UUID}

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import akka.stream.{Attributes, Inlet, Materializer, SinkShape}
import akka.util.ByteString
import akka.NotUsed
import akka.actor.ActorSystem
import com.advancedtelematic.libats.data.DataType.{Checksum, HashMethod, ValidChecksum}
import com.advancedtelematic.libtuf.data.TufDataType
import com.advancedtelematic.libtuf.data.TufDataType.TargetFilename
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.tuf.reposerver.target_store.AzureTargetStoreEngine.BlobStorageSettings
import com.azure.core.util.logging.ClientLogger
import com.azure.identity.{ClientSecretCredentialBuilder, DefaultAzureCredentialBuilder}
import com.azure.storage.blob.{BlobAsyncClient, BlobClientBuilder, BlobContainerAsyncClient, BlobContainerClient, BlobContainerClientBuilder, BlobServiceAsyncClient, BlobServiceClientBuilder}
import com.azure.storage.blob.models.BlockBlobItem
import com.azure.storage.blob.sas.{BlobSasPermission, BlobServiceSasSignatureValues}
import com.azure.storage.blob.specialized.BlockBlobAsyncClient
import com.azure.storage.common.implementation.connectionstring.StorageConnectionString
import org.bouncycastle.util.encoders.Hex
import org.slf4j.LoggerFactory
import reactor.core.publisher.{Flux, Mono}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration

object AzureTargetStoreEngine {

  final case class BlobStorageSettings(connectionString: String, signatureTtl: FiniteDuration)

}

class AzureTargetStoreEngine(private val settings: BlobStorageSettings)(implicit mat: Materializer, ec: ExecutionContext) extends TargetStoreEngine {
  import scala.compat.java8.FutureConverters._

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private[this] def containerClientFor(repoId: TufDataType.RepoId): BlobContainerAsyncClient = {
    new BlobContainerClientBuilder()
      .connectionString(settings.connectionString)
      .containerName(containerName(repoId))
      .buildAsyncClient()
  }

  private[this] def genBlockId(): String = Base64.getEncoder.encodeToString(UUID.randomUUID.toString.getBytes(StandardCharsets.UTF_8))

  private[this] def uploadBlock(blobClient: BlockBlobAsyncClient)(data: ByteString): Future[String] = {
    val blockId = genBlockId()
    blobClient.stageBlock(blockId, Flux.just[ByteBuffer](data.toByteBuffer), data.length.toLong).toFuture.toScala.map(_ => blockId)
  }

  def upload(blobClient: BlockBlobAsyncClient): Sink[ByteString, Future[TargetStoreEngine.TargetStoreResult]] =
    Flow[ByteString]
      .alsoToMat(Sha256Digest.asSink)(Keep.right)
      .alsoToMat(Sink.fold(0)(_ + _.length))(Keep.both)
      .mapAsync(1)(uploadBlock(blobClient))
      .fold(ListBuffer.empty[String])((xs, x) => xs += x)
      .mapAsync(1) { xs =>
        import scala.collection.JavaConverters._
        blobClient.commitBlockList(xs.asJava).toFuture.toScala
      }
      .mapMaterializedValue {
        case (futureCs, futureLn) =>
          for {
            cs <- futureCs
            ln <- futureLn
          } yield TargetStoreEngine.TargetStoreResult(Uri(blobClient.getBlobUrl), cs, ln)
      }.to(Sink.ignore)

  override def storeStream(repoId: TufDataType.RepoId,
                           filename: TargetFilename,
                           fileData: Source[ByteString, Any],
                           size: Long): Future[TargetStoreEngine.TargetStoreResult] = {
    store(repoId, filename, fileData)
  }

  override def store(repoId: TufDataType.RepoId,
                     filename: TargetFilename,
                     fileData: Source[ByteString, Any]): Future[TargetStoreEngine.TargetStoreResult] = {
    val containerClient = containerClientFor(repoId)
    val blobClient = containerClient.getBlobAsyncClient(blobName(filename)).getBlockBlobAsyncClient
    ensureContainerExists(containerClient).flatMap { _ =>
      fileData.toMat(upload(blobClient))(Keep.right).run()
    }
  }

  def ensureContainerExists(containerClient: BlobContainerAsyncClient): Future[Unit] = {
    containerClient.exists().flatMap( exists => {
      if (exists) {
        Mono.empty[Unit]()
      } else {
        containerClient.create().map[Unit](_ => ())
      }
    }).toFuture.toScala
  }

  private[this] def containerName(repoId: TufDataType.RepoId): String = {
    repoId.uuid.toString
  }

  private[this] def blobName(filename: TargetFilename): String = {
    Sha256Digest.digest(filename.value.getBytes).hash.value
  }

  private[this] def signatureExpiryTime(): OffsetDateTime = OffsetDateTime.now().plusSeconds(settings.signatureTtl.toSeconds)

  override def buildStorageUri(repoId: TufDataType.RepoId, filename: TargetFilename, length: Long): Future[Uri] = {
    val containerClient = new BlobContainerClientBuilder()
      .connectionString(settings.connectionString)
      .containerName(containerName(repoId))
      .buildAsyncClient()
    ensureContainerExists(containerClient).map { _ =>
      val blobClient = containerClient.getBlobAsyncClient(blobName(filename))
      val sasPermissions = new BlobSasPermission().setCreatePermission(true).setWritePermission(true)
      val signatureValues = new BlobServiceSasSignatureValues(signatureExpiryTime(), sasPermissions)
      val signature = blobClient.generateSas(signatureValues)
      Uri(blobClient.getBlobUrl).withRawQueryString(signature)
    }
  }

  private[this] def mkBlobClient(repoId: TufDataType.RepoId, filename: TargetFilename): BlockBlobAsyncClient = {
    new BlobClientBuilder()
      .connectionString(settings.connectionString)
      .containerName(containerName(repoId))
      .blobName(blobName(filename))
      .buildAsyncClient().getBlockBlobAsyncClient
  }

  override def retrieve(repoId: TufDataType.RepoId,
                        filename: TargetFilename): Future[TargetStoreEngine.TargetRetrieveResult] = {
    val blobClient = mkBlobClient(repoId, filename)

    val sasPermissions = new BlobSasPermission().setReadPermission(true)
    val signatureValues = new BlobServiceSasSignatureValues(signatureExpiryTime(), sasPermissions)
    val signature = blobClient.generateSas(signatureValues)
    val uri = Uri(blobClient.getBlobUrl).withRawQueryString(signature)
    FastFuture.successful(TargetStoreEngine.TargetRedirect(uri))
  }

  override def delete(repoId: TufDataType.RepoId, filename: TargetFilename): Future[Unit] = {
    mkBlobClient(repoId, filename).delete().toFuture.toScala.map(_ => ())
  }

}
