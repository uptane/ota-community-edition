package com.advancedtelematic.treehub.object_store

import akka.Done
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.treehub.object_store.BlobStore.UploadAt
import com.amazonaws.HttpMethod
import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.*
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.Date
import scala.async.Async.*
import scala.concurrent.{ExecutionContext, Future, blocking}

object S3BlobStore {
  def apply(s3Credentials: S3Credentials, allowRedirects: Boolean, root: Option[Path])
           (implicit ec: ExecutionContext, mat: Materializer): S3BlobStore =
    new S3BlobStore(s3Credentials, S3Client(s3Credentials), allowRedirects, root)
}

class S3BlobStore(s3Credentials: S3Credentials, s3client: AmazonS3, allowRedirects: Boolean, root: Option[Path])
                 (implicit ec: ExecutionContext, mat: Materializer) extends BlobStore {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val bucketId = s3Credentials.blobBucketId

  override def storeStream(namespace: Namespace, path: Path, size: Long, blob: Source[ByteString, ?]): Future[Long] = {
    val filename = objectFilename(namespace, path)

    val sink =  StreamConverters.asInputStream().mapMaterializedValue { is =>
      val meta = new ObjectMetadata()
      meta.setContentLength(size)
      val request = new PutObjectRequest(s3Credentials.blobBucketId, filename, is, meta).withCannedAcl(CannedAccessControlList.AuthenticatedRead)

      log.info(s"Uploading $filename to amazon s3")

      async {
        await(Future { blocking { s3client.putObject(request) } })
        log.info(s"$filename with size $size uploaded to s3")
        size
      }
    }

    blob.runWith(sink)
  }

  override def storeOutOfBand(namespace: Namespace, path: Path): Future[BlobStore.OutOfBandStoreResult] = {
    val filename = objectFilename(namespace, path)
    val expiresAt = Date.from(Instant.now().plus(1, ChronoUnit.HOURS))

    log.info(s"Requesting s3 pre signed url $filename")

    val f = Future {
      blocking {
        val url = s3client.generatePresignedUrl(s3Credentials.blobBucketId, filename, expiresAt, HttpMethod.PUT)
        log.debug(s"Signed s3 url for $filename")
        url
      }
    }

    f.map(url => UploadAt(url.toString))
  }

  private def streamS3Bytes(namespace: Namespace, path: Path): Future[Source[ByteString, ?]] = {
    val filename = objectFilename(namespace, path)
    Future {
      blocking {
        val s3ObjectInputStream = s3client.getObject(bucketId, filename).getObjectContent
        StreamConverters.fromInputStream(() => s3ObjectInputStream)
      }
    }
  }

  private def fetchPresignedUri(namespace: Namespace, path: Path): Future[Uri] = {
    val publicExpireTime = Duration.ofDays(1)
    val filename = objectFilename(namespace, path)
    val expire = java.util.Date.from(Instant.now.plus(publicExpireTime))
    Future {
      val signedUri = blocking {
        s3client.generatePresignedUrl(bucketId, filename, expire)
      }
      Uri(signedUri.toURI.toString)
    }
  }

  override def buildResponse(namespace: Namespace, path: Path): Future[HttpResponse] = {
    if(allowRedirects) {
      fetchPresignedUri(namespace, path).map { uri =>
        HttpResponse(StatusCodes.Found, headers = List(Location(uri)))
      }
    } else
      streamS3Bytes(namespace, path).map(buildResponseFromBytes)
  }

  override def readFull(namespace: Namespace, path: Path): Future[ByteString] = {
    val filename = objectFilename(namespace, path)
    val is = s3client.getObject(bucketId, filename).getObjectContent
    val source = StreamConverters.fromInputStream(() => is)
    source.runFold(ByteString.empty)(_ ++ _)
  }

  override def exists(namespace: Namespace, path: Path): Future[Boolean] = {
    val filename = objectFilename(namespace, path)
    Future { blocking { s3client.doesObjectExist(bucketId, filename) } }
  }

  private def namespaceDir(namespace: Namespace): Path =
    Paths.get(DigestCalculator.digest()(namespace.get))

  private def objectFilename(namespace: Namespace, path: Path): String = root match {
    case Some(r) => r.resolve(namespaceDir(namespace)).resolve(path).toString
    case None => namespaceDir(namespace).resolve(path).toString
  }

  override val supportsOutOfBandStorage: Boolean = true

  override def deleteObject(ns: Namespace, path: Path): Future[Done] =
    Future {
      blocking {
        s3client.deleteObject(bucketId, objectFilename(ns, path))
        Done
      }
    }

  import scala.jdk.CollectionConverters._
  override def deleteObjects(ns: Namespace, pathPrefix: Path): Future[Done] = {
    def loop(): Future[Done] = {
      val listRequest = new ListObjectsRequest()
        .withBucketName(bucketId)
        .withPrefix(objectFilename(ns, pathPrefix))
        .withMaxKeys(100)

      val resultF = Future {
        blocking(s3client.listObjects(listRequest))
      }

      resultF.flatMap { result =>
        val keys = result.getObjectSummaries.asScala.map(_.getKey).toList
        Future {
          blocking {
            val deleteRequest = new DeleteObjectsRequest(bucketId)
            deleteRequest.setKeys(keys.map(new KeyVersion(_)).asJava)

            s3client.deleteObjects(deleteRequest)
          }
        }.flatMap { _ =>
          if (result.isTruncated)
            loop()
          else
            FastFuture.successful(Done)
        }
      }
    }

    loop()
  }
}

object S3Client {
  private val _log = LoggerFactory.getLogger(this.getClass)

  def apply(s3Credentials: S3Credentials): AmazonS3 = {
    val defaultClientBuilder = AmazonS3ClientBuilder.standard().withCredentials(s3Credentials)

    if (s3Credentials.endpointUrl.nonEmpty) {
      _log.info(s"Using custom S3 url: ${s3Credentials.endpointUrl}")
      defaultClientBuilder
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Credentials.endpointUrl, s3Credentials.region.getName))
        .build()
    } else {
      defaultClientBuilder
        .withRegion(s3Credentials.region)
        .build()
    }
  }
}

class S3Credentials(accessKey: String, secretKey: String,
                    val blobBucketId: String,
                    val region: Regions,
                    val endpointUrl: String) extends AWSCredentials with AWSCredentialsProvider {
  override def getAWSAccessKeyId: String = accessKey

  override def getAWSSecretKey: String = secretKey

  override def refresh(): Unit = ()

  override def getCredentials: AWSCredentials = this
}
