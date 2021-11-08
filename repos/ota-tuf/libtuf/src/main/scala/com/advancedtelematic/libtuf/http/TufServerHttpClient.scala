package com.advancedtelematic.libtuf.http

import java.io.{File, FileInputStream}
import java.net.{URI, URL}
import java.nio.file.{Path, Paths}
import java.security.MessageDigest
import java.util.Base64
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedRoleName, RootRole, TargetsRole}
import com.advancedtelematic.libtuf.data.ErrorCodes
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{CompleteUploadRequest, ETag, GetSignedUrlResult, InitMultipartUploadResult, KeyId, MultipartUploadId, SignedPayload, TargetFilename, TufKeyPair, UploadPartETag}
import com.advancedtelematic.libtuf.http.CliHttpClient.{CliHttpBackend, CliHttpClientError}
import com.advancedtelematic.libtuf.http.TufServerHttpClient.{RoleChecksumNotValid, RoleNotFound, TargetsResponse, UploadTargetTooBig}
import com.azure.storage.blob.BlobClientBuilder
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import org.slf4j.LoggerFactory
import sttp.client._
import sttp.model.{Header, HeaderNames, Headers, StatusCode, Uri}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, _}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

object TufServerHttpClient {
  case class TargetsResponse(targets: SignedPayload[TargetsRole], checksum: Option[Refined[String, ValidChecksum]])

  case object RoleChecksumNotValid extends Exception("could not overwrite targets, trying to update an older version of role. Did you run `targets pull` ?") with NoStackTrace

  case class RoleNotFound(msg: String) extends Exception(s"role not found: $msg") with NoStackTrace

  case class UploadTargetTooBig(msg: String) extends Exception(msg) with NoStackTrace
}

trait TufServerClient {
  def root(version: Option[Int] = None): Future[SignedPayload[RootRole]]

  def fetchKeyPair(keyId: KeyId): Future[TufKeyPair]

  def deleteKey(keyId: KeyId): Future[Unit]

  def pushSignedRoot(signedRoot: SignedPayload[RootRole]): Future[Unit]
}

trait ReposerverClient extends TufServerClient {
  def pushDelegation(name: DelegatedRoleName, delegation: SignedPayload[TargetsRole]): Future[Unit]

  def pullDelegation(name: DelegatedRoleName): Future[SignedPayload[TargetsRole]]

  def targets(): Future[TargetsResponse]

  def pushTargets(role: SignedPayload[TargetsRole], previousChecksum: Option[Refined[String, ValidChecksum]]): Future[Unit]

  def uploadTarget(targetFilename: TargetFilename, inputPath: Path, timeout: Duration): Future[Unit]
}

trait DirectorClient extends TufServerClient

abstract class TufServerHttpClient(uri: URI, httpBackend: CliHttpBackend)
                                  (implicit ec: ExecutionContext) extends CliHttpClient(httpBackend) {

  protected def uriPath: String

  protected def apiUri(path: String): Uri = {
    val completePath = Paths.get(uri.getPath, uriPath, path)
    val url = new URL(uri.toURL, completePath.toString)
    Uri.apply(url.toURI)
  }

  def root(version: Option[Int] = None): Future[SignedPayload[RootRole]] = {
    val filename = version match {
      case Some(v) => v + ".root.json"
      case None => "root.json"
    }

    val req = http.get(apiUri(filename))

    execHttp[SignedPayload[RootRole]](req) {
      case (404, error) => Future.failed(RoleNotFound(error.description))
    }.map(_.body)
  }

  def pushSignedRoot(signedRoot: SignedPayload[RootRole]): Future[Unit] = {
    val req = http.post(apiUri("root"))
    execJsonHttp[Unit, SignedPayload[RootRole]](req, signedRoot)()
  }

  def fetchKeyPair(keyId: KeyId): Future[TufKeyPair]

  def deleteKey(keyId: KeyId): Future[Unit]
}

class ReposerverHttpClient(uri: URI, httpBackend: CliHttpBackend)(implicit ec: ExecutionContext)
  extends TufServerHttpClient(uri, httpBackend) with ReposerverClient {

  private val _log = LoggerFactory.getLogger(this.getClass)

  protected def uriPath: String = "/api/v1/user_repo/"

  def fetchKeyPair(keyId: KeyId): Future[TufKeyPair] = {
    val req = http.get(apiUri("root/private_keys/" + keyId.value))
    execHttp[TufKeyPair](req)().map(_.body)
  }

  def deleteKey(keyId: KeyId): Future[Unit] = {
    val req = http.delete(apiUri("root/private_keys/" + keyId.value))
    execHttp[Unit](req)().map(_.body)
  }

  def targets(): Future[TargetsResponse] = {
    val req = http.get(apiUri("targets.json"))
    execHttp[SignedPayload[TargetsRole]](req)().map { response =>
      val checksumO = response.header("x-ats-role-checksum").flatMap { v => refineV[ValidChecksum](v).toOption }
      TargetsResponse(response.body, checksumO)
    }
  }

  def pushTargets(role: SignedPayload[TargetsRole], previousChecksum: Option[Refined[String, ValidChecksum]]): Future[Unit] = {
    val put = http.put(apiUri("targets"))
    val req = previousChecksum.map(e => put.header("x-ats-role-checksum", e.value)).getOrElse(put)

    execJsonHttp[Unit, SignedPayload[TargetsRole]](req, role) {
      case (412, errorRepr) if errorRepr.code.code == "role_checksum_mismatch" =>
        Future.failed(RoleChecksumNotValid)
      case (428, _) =>
        Future.failed(RoleChecksumNotValid)
    }
  }

  override def pushDelegation(name: DelegatedRoleName, delegation: SignedPayload[TargetsRole]): Future[Unit] = {
    val req = http.put(apiUri(s"delegations/${name.value}.json"))
    execJsonHttp[Unit, SignedPayload[TargetsRole]](req, delegation)()
  }

  override def pullDelegation(name: DelegatedRoleName): Future[SignedPayload[TargetsRole]] = {
    val req = http.get(apiUri(s"delegations/${name.value}.json"))
    execHttp[SignedPayload[TargetsRole]](req)().map(_.body)
  }

  override def uploadTarget(targetFilename: TargetFilename, inputPath: Path, timeout: Duration): Future[Unit] = {
    val multipartUploadResult = for {
      inputFile <- Future.fromTry(Try(inputPath.toFile))
      initResult <- initMultipartUpload(targetFilename, inputFile.length())
      result <- s3MultipartUpload(targetFilename, inputFile, initResult.uploadId, initResult.partSize, timeout)
    } yield result

    multipartUploadResult.recoverWith {
      case e: CliHttpClientError if e.remoteError.code == ErrorCodes.Reposerver.NotImplemented =>
        //Multipart upload is not supported for Azure Blob Storage.
        uploadByPreSignedUrl(targetFilename, inputPath, timeout)
    }
  }

  private[this] def uploadToAzure(uri: Uri, inputPath: Path, timeout: Duration): Future[Unit] = {
    val uploadResult = Try {
      val client = new BlobClientBuilder().endpoint(uri.toString()).buildClient()
      client.uploadFromFile(inputPath.toString, true)
    }
    Future.fromTry(uploadResult)
  }

  private def uploadByPreSignedUrl(targetFilename: TargetFilename, inputPath: Path, timeout: Duration): Future[Unit] = {
    val req = basicRequest.put(apiUri(s"uploads/" + targetFilename.value))
      .body(inputPath)
      .readTimeout(timeout)
      .followRedirects(false)
      .response(asByteArrayAlways)

    val httpF: Future[Unit] = httpBackend.send(req).flatMap {
      case r @ Response(_, StatusCode.Found, _, _, _) =>
        r.header("Location").fold[Either[String, Uri]](Left("No 'Location' header found."))(x => Uri.parse(x)) match {
          case Left(err) =>
            Future.failed(new Throwable(err))

          case Right(uri) if uri.host.endsWith("core.windows.net") =>
            uploadToAzure(uri, inputPath, timeout)

          case Right(uri) =>
            execHttp[Unit](req.put(uri))(PartialFunction.empty).map(_ => ())
        }

      case resp =>
        handleResponse[Unit](req, resp) {
          case (StatusCode.PayloadTooLarge.code, err) if err.code == ErrorCodes.Reposerver.PayloadTooLarge =>
            _log.debug(s"Error from server: $err")
            Future.failed(UploadTargetTooBig(err.description))
        }.map(_ => ())
    }
    _log.info("Uploading file, this may take a while")

    def wait(): Future[Unit] = {
      if(httpF.isCompleted) {
        println("")
        httpF
      } else {
        Future {
          blocking {
            print(".")
            Thread.sleep(1000)
          }
        }.flatMap(_ => wait())
      }
    }

    wait()
  }

  private def s3MultipartUpload(targetFilename: TargetFilename, inputFile: File, uploadId: MultipartUploadId, partSize: Long, timeout: Duration): Future[Unit] = {

    val totalSize = inputFile.length()
    val inputStream: FileInputStream = new FileInputStream(inputFile)

    def processChunk(inputStream: FileInputStream, part: Int = 1, bytesProcessed: Long = 0, eTags: Seq[UploadPartETag] = Seq.empty): Future[Seq[UploadPartETag]] = {

      val available = inputStream.available()
      val bufferSize = Math.min(partSize, available).toInt
      val byteBuffer = new Array[Byte](bufferSize)
      inputStream.read(byteBuffer)

      if (bufferSize == 0) {
        inputStream.close()
        Future.successful(eTags)
      } else {
        getSignedUrlAndUploadPart(targetFilename, uploadId, part, byteBuffer, timeout)
          .flatMap { tag =>
            printProgress(totalSize, bytesProcessed + bufferSize)
            processChunk(inputStream, part + 1, bytesProcessed + bufferSize, tag +: eTags)
          }
      }
    }

    for {
      tags <- processChunk(inputStream)
      _ <- completeMultipartUpload(targetFilename, uploadId, tags)
    } yield {
      _log.info(s"Upload completed")
    }
  }

  private def printProgress(total: Long, current: Long): Unit = {
    def toMb(value: Long) = BigDecimal(value.toDouble / 1024 / 1024).setScale(2, BigDecimal.RoundingMode.HALF_UP)

    val str = s"Uploading: uploaded ${toMb(current)}Mb of ${toMb(total)}Mb"
    _log.info(str)
  }

  private def initMultipartUpload(targetFilename: TargetFilename, fileSize: Long): Future[InitMultipartUploadResult] = {
    val req = http
      .post(apiUri(s"multipart/initiate/" + targetFilename.value).params("fileSize" -> fileSize.toString))
      .followRedirects(false)

    execHttp[InitMultipartUploadResult](req)().map(_.body)
  }

  private def getSignedUrlAndUploadPart(targetFilename: TargetFilename, uploadId: MultipartUploadId, part: Int, data: Array[Byte], timeout: Duration): Future[UploadPartETag] = {
    val digest = MessageDigest.getInstance("MD5").digest(data)
    val md5Hash = Base64.getEncoder.encodeToString(digest)
    val contentLength = data.length

    for {
      url <- getSignedUrl(targetFilename, uploadId, part, md5Hash, contentLength)
      eTag <- uploadPart(url, part, data, md5Hash, timeout)
    } yield eTag
  }

  private def getSignedUrl(targetFilename: TargetFilename, uploadId: MultipartUploadId, part: Int, md5Hash: String, length: Int): Future[URI] = {
    val url = apiUri(s"multipart/url/${targetFilename.value}")
      .params(("part" -> part.toString),
        ("uploadId" -> uploadId.value),
        ("md5" -> md5Hash),
        ("contentLength" -> length.toString))

    val req = http.get(url).followRedirects(false)

    val rs = execHttp[GetSignedUrlResult](req, retryingHttpClient())().map(_.body)

    rs.onComplete {
      case Success(v) => _log.debug(s"Get signed URL response: $v")
      case Failure(e) => _log.error(s"Get signed URL error: ${e.getMessage}")
    }

    rs.map(_.uri)
  }

  private def uploadPart(uri: URI, part: Int, data: Array[Byte], md5Hash: String, timeout: Duration): Future[UploadPartETag] = {

    val req = http.put(Uri(uri))
      .body(data)
      .readTimeout(timeout)
      .header(HeaderNames.ContentMd5, md5Hash)
      .header(Header.contentLength(data.length))
      .followRedirects(false)

    retryingHttpClient()(req).flatMap { response =>
      response.header(HeaderNames.Etag) match {
        case Some(eTag) =>
          _log.debug("Uploaded part ETag: " + eTag)
          Future.successful(UploadPartETag(part, ETag(eTag)))
        case None =>
          Future.failed(new Exception("ETag not found in response headers"))
      }
    }
  }

  def completeMultipartUpload(targetFilename: TargetFilename, uploadId: MultipartUploadId, partETags: Seq[UploadPartETag]): Future[Unit] = {
    val req = http
      .put(apiUri(s"multipart/complete/" + targetFilename.value))
      .followRedirects(false)

    val rs = execJsonHttp[Unit, CompleteUploadRequest](req, CompleteUploadRequest(uploadId, partETags), retryingHttpClient())()

    rs.onComplete {
      case Success(v) => _log.debug(s"Complete multipart upload response: $v")
      case Failure(e) => _log.error(s"Complete multipart upload error: ${e.getMessage}")
    }

    rs.map(_ => ())
  }
}

class DirectorHttpClient(uri: URI, httpBackend: CliHttpBackend)
                        (implicit ec: ExecutionContext) extends TufServerHttpClient(uri, httpBackend) with DirectorClient {

  // assumes talking to the Director through the API gateway
  protected def uriPath: String = "/api/v1/director/admin/repo/"

  def fetchKeyPair(keyId: KeyId): Future[TufKeyPair] = {
    val req = http.get(apiUri("private_keys/" + keyId.value))
    execHttp[TufKeyPair](req)().map(_.body)
  }

  def deleteKey(keyId: KeyId): Future[Unit] = {
    val req = http.delete(apiUri("private_keys/" + keyId.value))
    execHttp[Unit](req)().map(_.body)
  }
}
