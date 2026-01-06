package com.advancedtelematic.tuf.reposerver.delegations

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.coding.Coders
import org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.{deflate, gzip}
import org.apache.pekko.http.scaladsl.model.headers.{`Accept-Encoding`, HttpEncodings, RawHeader}
import org.apache.pekko.http.scaladsl.model.{
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  Uri
}
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.tuf.reposerver.http.Errors
import org.slf4j.LoggerFactory

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}

trait RemoteDelegationClient {

  def fetch[Resp](uri: Uri, headers: Map[String, String])(
    implicit um: FromEntityUnmarshaller[Resp]): Future[Resp]

}

class HttpRemoteDelegationClient()(implicit val ec: ExecutionContext, system: ActorSystem)
    extends RemoteDelegationClient {
  private val _http = Http()

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def fetch[Resp](uri: Uri, headers: Map[String, String])(
    implicit um: FromEntityUnmarshaller[Resp]): Future[Resp] = async {
    val req = HttpRequest(HttpMethods.GET, uri).addHeader(
      `Accept-Encoding`(gzip, deflate, HttpEncodings.identity)
    )
    val reqWithHeaders = headers.foldLeft(req) { case (r, (n, v)) => r.addHeader(RawHeader(n, v)) }
    val resp = await(_http.singleRequest(reqWithHeaders).map(decodeResponse))

    val f =
      if (resp.status.isFailure())
        unmarshallStringOrError(resp.entity)
          .flatMap { statusStr =>
            FastFuture.failed(Errors.DelegationRemoteFetchFailed(uri, resp.status, statusStr))
          }
      else
        unmarshallJsonOrError(resp.entity)
          .recoverWith { case err =>
            FastFuture.failed(Errors.DelegationRemoteParseFailed(uri, err.getMessage))
          }

    await(f)
  }

  private def unmarshallJsonOrError[R](entity: HttpEntity)(
    implicit um: FromEntityUnmarshaller[R]): Future[R] =
    um.apply(entity)
      .recoverWith { case err =>
        if (log.isDebugEnabled) {
          unmarshallStringOrError(entity).recover { case err => err.getMessage }.foreach { payload =>
            log.warn("Could not unmarshall remote response. Response was: " + payload)
          }
        }

        FastFuture.failed(err)
      }

  private def unmarshallStringOrError(entity: HttpEntity): Future[String] =
    Unmarshaller.stringUnmarshaller.apply(entity).recover { case err =>
      s"Could not unmarshall response: ${err.getMessage}"
    }

  private def decodeResponse(response: HttpResponse): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip =>
        Coders.Gzip
      case HttpEncodings.deflate =>
        Coders.Deflate
      case HttpEncodings.identity =>
        Coders.NoCoding
      case other =>
        log.warn(s"Unknown encoding [$other], not decoding")
        Coders.NoCoding
    }

    decoder.decodeMessage(response)
  }

}
