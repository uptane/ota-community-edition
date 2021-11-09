package com.advancedtelematic.campaigner.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import com.advancedtelematic.campaigner.data.Codecs.uriDecoder
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.Errors.RemoteServiceError
import com.advancedtelematic.libats.http.HttpOps.HttpRequestOps
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.tracing.TracingHttpClient
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.{ExecutionContext, Future}

trait UserProfileClient {
  def externalResolverUri(ns: Namespace): Future[Option[Uri]]
}

object UserProfileClient {
  final case class NsSettings(namespace: Namespace, resolverUri: Option[Uri])

  object NsSettings {
    import com.advancedtelematic.libats.codecs.CirceCodecs.namespaceDecoder
    implicit val DecoderInstance = io.circe.generic.semiauto.deriveDecoder[NsSettings]
  }
}

class UserProfileHttpClient(uri: Uri, httpClient: HttpRequest => Future[HttpResponse])
                           (implicit ec: ExecutionContext, system: ActorSystem, tracing: ServerRequestTracing)
  extends TracingHttpClient(httpClient, "user-profile") with UserProfileClient {

  import com.advancedtelematic.libats.http.ServiceHttpClient._

  override def externalResolverUri(ns: Namespace): Future[Option[Uri]] = {
    val path = uri.path / "api" / "v1" / "namespace_settings" / ns.get
    val request = HttpRequest(HttpMethods.GET, uri.withPath(path)).withNs(ns)

    val errorHandler: PartialFunction[RemoteServiceError, Future[Option[Uri]]] = {
      case e if e.status == StatusCodes.NotFound => FastFuture.successful(None)
    }

    execHttpUnmarshalled[UserProfileClient.NsSettings](request)
      .map(_.map(_.resolverUri))
      .handleErrors(errorHandler)
  }
}
