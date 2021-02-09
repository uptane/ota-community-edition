package com.advancedtelematic.libtuf.http

import com.advancedtelematic.libats.data
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libtuf.http.CliHttpClient.{CliHttpBackend, CliHttpClientError}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.slf4j.LoggerFactory
import sttp.client.SttpBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace
import scala.util.{Success, Try}

object CliHttpClient {
  type CliHttpBackend = SttpBackend[Future, Nothing, Nothing]

  case class CliHttpClientError(msg: String, remoteError: ErrorRepresentation) extends Throwable(msg) with NoStackTrace

  def UnknownErrorRepr(msg: String) = ErrorRepresentation(data.ErrorCode("unknown_error_repr"), msg)
}

abstract class CliHttpClient(httpBackend: CliHttpBackend)(implicit ec: ExecutionContext) {

  private val log = LoggerFactory.getLogger(this.getClass)

  import sttp.client._

  protected def http = basicRequest.response(asByteArrayAlways)

  private def defaultErrorHandler[T](): PartialFunction[(Int, ErrorRepresentation), Future[T]] = PartialFunction.empty

  protected def execJsonHttp[Res: ClassTag : Decoder, Req: Encoder]
  (request: Request[Array[Byte], Nothing], entity: Req)
  (errorHandler: PartialFunction[(Int, ErrorRepresentation), Future[Res]] = defaultErrorHandler()): Future[Res] = {

    val req = request
      .body(entity.asJson.noSpaces.getBytes)
      .contentType("application/json")

    execHttp(req)(errorHandler).map(_.body)
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

  protected def execHttp[T: ClassTag : Decoder](request: Request[Array[Byte], Nothing])
                                               (errorHandler: PartialFunction[(Int, ErrorRepresentation), Future[T]] = defaultErrorHandler()): Future[Response[T]] =
    httpBackend.send[Array[Byte]](request).flatMap(handleResponse(request, _)(errorHandler))
}
