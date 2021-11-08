package com.advancedtelematic.libtuf.http

import com.advancedtelematic.libats.data
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libtuf.http.CliHttpClient.{CliHttpBackend, CliHttpClientError, CliRequest, CliResponse, HttpClient}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.slf4j.LoggerFactory
import sttp.client.SttpClientException.{ConnectException, ReadException}
import sttp.client.{Request, Response, SttpBackend}
import sttp.model.{MediaType, StatusCode}

import java.io.{BufferedInputStream, BufferedOutputStream, BufferedWriter, ByteArrayInputStream, ByteArrayOutputStream, OutputStreamWriter, PipedInputStream, PipedOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace
import scala.util.{Success, Try}

object CliHttpClient {
  type CliHttpBackend = SttpBackend[Future, Nothing, Nothing]

  type CliRequest = Request[Array[Byte], Nothing]
  type CliResponse = Response[Array[Byte]]
  type HttpClient = CliRequest => Future[CliResponse]

  case class CliHttpClientError(msg: String, remoteError: ErrorRepresentation) extends Throwable(msg) with NoStackTrace

  def UnknownErrorRepr(msg: String) = ErrorRepresentation(data.ErrorCode("unknown_error_repr"), msg)

  val defaultRetryStatuses =
    Set(
      StatusCode.InternalServerError,
      StatusCode.BadGateway,
      StatusCode.ServiceUnavailable,
      StatusCode.GatewayTimeout,
      StatusCode.TooManyRequests
    )

  val defaultBackoffs = List(1.second, 2.seconds, 4.seconds, 8.seconds, 16.seconds, 32.seconds)
}

abstract class CliHttpClient(httpBackend: CliHttpBackend)(implicit ec: ExecutionContext) {

  private val log = LoggerFactory.getLogger(this.getClass)

  import sttp.client._

  protected def http = basicRequest.response(asByteArrayAlways)

  private def defaultErrorHandler[T](): PartialFunction[(Int, ErrorRepresentation), Future[T]] = PartialFunction.empty

  protected val defaultHttpClient: HttpClient = (request: CliRequest) => httpBackend.send[Array[Byte]](request)

  private lazy val gzipMinSizeBytes = sys.env.get("CLI_GZIP_MIN_BYTES").map(_.toLong).getOrElse(500000L) // 0.5 mb

  protected def execJsonHttp[Res: ClassTag : Decoder, Req: Encoder]
  (request: CliRequest, entity: Req, httpClient: HttpClient = defaultHttpClient)
  (errorHandler: PartialFunction[(Int, ErrorRepresentation), Future[Res]] = defaultErrorHandler()): Future[Res] = {

    val bodyBytes = entity.asJson.noSpaces.getBytes

    val req = if(bodyBytes.length >= gzipMinSizeBytes) {
      val baos = new ByteArrayOutputStream()
      val gzip = new GZIPOutputStream(baos)
      gzip.write(bodyBytes)
      gzip.close()

      val body = ByteArrayBody(baos.toByteArray, Option(MediaType.ApplicationGzip))

      request
        .body(body)
        .header("Content-Encoding", "gzip")
        .contentType(MediaType.ApplicationJson)
    } else
      request
        .body(bodyBytes)
        .contentType(MediaType.ApplicationJson)

    execHttp(req, httpClient)(errorHandler).map(_.body)
  }

  private def tryErrorParsing(response: Response[Array[Byte]]): ErrorRepresentation = {
    def fallbackToResponseParse: String = {
      tryParseResponse[Json](response).map { errorRepr =>
        errorRepr.noSpaces
      }.recover { case _ =>
        new String(response.body)
      }.getOrElse {
        s"Unknown error|$response"
      }
    }

    tryParseResponse[ErrorRepresentation](response).getOrElse(CliHttpClient.UnknownErrorRepr(fallbackToResponseParse))
  }

  def tryParseResponse[T: ClassTag : Decoder](response: Response[Array[Byte]]): Try[T] =
    if (implicitly[ClassTag[T]].runtimeClass.equals(classOf[Unit]))
      Success(()).asInstanceOf[Try[T]]
    else
      io.circe.parser.parse(new String(response.body)).flatMap(_.as[T]).toTry

  protected def handleErrorResponse[T](request: Request[Array[Byte], Nothing], resp: Response[Array[Byte]])
                                      (errorHandler: PartialFunction[(Int, ErrorRepresentation), Future[T]] = defaultErrorHandler()): Future[Response[T]] = {
    val parsedErr = tryErrorParsing(resp)

    if (errorHandler.isDefinedAt(resp.code.code, parsedErr)) {
      errorHandler(resp.code.code, parsedErr).map(err => resp.copy(body = err))
    } else {
      log.debug(s"request failed: $request")
      Future.failed {
        val msg = s"${this.getClass.getSimpleName}|${request.method}|http/${resp.code}|${request.uri}|${parsedErr.description}"
        CliHttpClientError(msg, parsedErr)
      }
    }
  }

  protected def handleResponse[T: ClassTag : Decoder](request: Request[Array[Byte], Nothing], resp: Response[Array[Byte]])
                                                     (errorHandler: PartialFunction[(Int, ErrorRepresentation), Future[T]] = defaultErrorHandler()): Future[Response[T]] = {
    if (resp.isSuccess)
      Future.fromTry(tryParseResponse[T](resp).map(parsed => resp.copy(body = parsed)))
    else {
      handleErrorResponse(request, resp)(errorHandler)
    }
  }

  protected def execHttp[T: ClassTag : Decoder](request: CliRequest, httpClient: HttpClient = defaultHttpClient)
                                               (errorHandler: PartialFunction[(Int, ErrorRepresentation), Future[T]] = defaultErrorHandler()): Future[Response[T]] =
    httpClient(request).flatMap(handleResponse(request, _)(errorHandler))

  protected def retryingHttpClient(statuses: Set[StatusCode] = CliHttpClient.defaultRetryStatuses,
                                   backoffs: List[Duration] = CliHttpClient.defaultBackoffs,
                                   httpClient: HttpClient = defaultHttpClient): HttpClient = (request: CliRequest) => {

    def retryOrResult(result: Future[CliResponse], msg: String) = backoffs match {
      case head :: tail =>
        log.warn(s"$msg. Retrying in $head. Request: $request")
        wait(head).flatMap(_ => retryingHttpClient(statuses, tail, httpClient)(request))
      case Nil => result
    }

    httpClient(request).flatMap { response =>
      if (statuses.contains(response.code)) {
        retryOrResult(Future.successful(response), s"Received status code ${response.code}")
      } else {
        Future.successful(response)
      }
    }.recoverWith {
      case e: ConnectException =>
        retryOrResult(Future.failed(e), s"Connection error: ${e.getMessage}")
      case e: ReadException =>
        retryOrResult(Future.failed(e), s"Read error: ${e.getMessage}")
    }
  }

  /**
   * Don't use this on server side.
   */
  private def wait(duration: Duration): Future[Unit] = Future {
    Thread.sleep(duration.toMillis)
    ()
  }

}
