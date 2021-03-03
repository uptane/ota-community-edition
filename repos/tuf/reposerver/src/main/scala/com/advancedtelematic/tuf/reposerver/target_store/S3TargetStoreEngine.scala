package com.advancedtelematic.tuf.reposerver.target_store

import java.io.File
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.Date

import scala.async.Async._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.util.ByteString
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, TargetFilename}
import com.advancedtelematic.tuf.reposerver.target_store.TargetStoreEngine.{TargetRedirect, TargetRetrieveResult, TargetStoreResult}
import com.amazonaws.HttpMethod
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{CannedAccessControlList, GeneratePresignedUrlRequest, ObjectMetadata, PutObjectRequest}
import org.slf4j.LoggerFactory

import scala.concurrent._
import scala.concurrent.Future
import scala.util.Try

class S3TargetStoreEngine(credentials: S3Credentials)(implicit val system: ActorSystem) extends TargetStoreEngine {

  import system.dispatcher

  private val bucketId = credentials.bucketId

  private val log = LoggerFactory.getLogger(this.getClass)

  private lazy val s3client = AmazonS3ClientBuilder.standard()
    .withCredentials(credentials)
    .withRegion(credentials.region)
    .build()

  override def store(repoId: RepoId, filename: TargetFilename, fileData: Source[ByteString, Any]): Future[TargetStoreResult] = {
    val tempFile = File.createTempFile("s3file", ".tmp")

    // The s3 sdk requires us to specify the file size if using a stream
    // so we always need to cache the file into the filesystem before uploading
    val sink = FileIO.toPath(tempFile.toPath).mapMaterializedValue {
      _.flatMap { result =>
        if(result.wasSuccessful) {
          upload(repoId, tempFile, filename).andThen { case _ => Try(tempFile.delete()) }
        } else {
          Try(tempFile.delete())
          Future.failed(result.getError)
        }
      }
    }

    write(fileData, sink)
  }

  override def storeStream(repoId: RepoId, filename: TargetFilename, fileData: Source[ByteString, Any], size: Long): Future[TargetStoreResult] = {
    val storagePath = storageFilename(repoId, filename)
    val sink =  StreamConverters.asInputStream().mapMaterializedValue { is =>
      val meta = new ObjectMetadata()
      meta.setContentLength(size)
      val request = new PutObjectRequest(bucketId, storagePath.toString, is, meta).withCannedAcl(CannedAccessControlList.AuthenticatedRead)

      log.info(s"Uploading $filename to amazon s3 using streaming upload")

      val uploadF = async {
        await(Future { blocking { s3client.putObject(request) } })
        log.info(s"$filename with size $size uploaded to s3")
        await(Future { blocking { s3client.getUrl(bucketId, storagePath.toString) } })
      }

      uploadF.map(uri => Uri(uri.toString) -> size)
    }

    write(fileData, sink)
  }

  protected def upload(repoId: RepoId, file: File, filename: TargetFilename): Future[(Uri, Long)] = {
    val storagePath = storageFilename(repoId, filename)
    val request = new PutObjectRequest(credentials.bucketId, storagePath.toString, file).withCannedAcl(CannedAccessControlList.AuthenticatedRead)

    log.info(s"Uploading ${filename.value} to amazon s3")

    async {
      await(Future { blocking { s3client.putObject(request) } })
      val uri = await(Future { blocking { s3client.getUrl(bucketId, storagePath.toString) } })
      val metadata = await(Future { blocking { s3client.getObjectMetadata(bucketId, storagePath.toString) } })

      log.info(s"$filename uploaded to s3")

      (Uri(uri.toString), metadata.getContentLength)
    }
  }

  override def retrieve(repoId: RepoId, filename: TargetFilename): Future[TargetRetrieveResult] = {
    val storagePath = storageFilename(repoId, filename)
    val publicExpireTime = Duration.ofDays(1)
    val expire = java.util.Date.from(Instant.now.plus(publicExpireTime))
    Future {
      val signedUri = blocking {
        s3client.generatePresignedUrl(bucketId, storagePath.toString, expire)
      }

      TargetRedirect(Uri(signedUri.toURI.toString))
    }
  }

  override def delete(repoId: RepoId, filename: TargetFilename): Future[Unit] = Future {
    blocking {
      val storagePath = storageFilename(repoId, filename)
      s3client.deleteObject(bucketId, storagePath.toString)
    }
  }

  override def buildStorageUri(repoId: RepoId, filename: TargetFilename, length: Long): Future[Uri] = {
    val objectId = storageFilename(repoId, filename).toString
    val expiresAt = Date.from(Instant.now().plus(3, ChronoUnit.HOURS))

    log.info(s"Signing s3 url for $objectId")

    FastFuture.successful {
      val req = new GeneratePresignedUrlRequest(bucketId, objectId, HttpMethod.PUT)
      req.putCustomRequestHeader("Content-Length", length.toString)
      req.setExpiration(expiresAt)
      val url = s3client.generatePresignedUrl(req)
      log.debug(s"Signed s3 url for $objectId")
      url.toString
    }
  }
}

class S3Credentials(accessKey: String, secretKey: String, val bucketId: String, val region: Regions)
  extends AWSCredentials with AWSCredentialsProvider {
  override def getAWSAccessKeyId: String = accessKey

  override def getAWSSecretKey: String = secretKey

  override def refresh(): Unit = ()

  override def getCredentials: AWSCredentials = this
}
