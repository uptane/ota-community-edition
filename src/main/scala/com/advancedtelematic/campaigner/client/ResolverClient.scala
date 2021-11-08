package com.advancedtelematic.campaigner.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.advancedtelematic.campaigner.data.DataType.ExternalUpdateId
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.HttpOps.HttpRequestOps
import com.advancedtelematic.libats.http.ServiceHttpClient
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Decoder

import scala.concurrent.Future

trait ResolverClient {
  def availableUpdatesFor(resolverUri: Uri, ns: Namespace, devices: Set[DeviceId]): Future[Seq[ExternalUpdateId]]

  def updatesForDevice(resolverUri: Uri, ns: Namespace, deviceId: DeviceId): Future[List[ExternalUpdate]]
}

final case class ExternalUpdate(deviceId: DeviceId, updateId: ExternalUpdateId, size: Long)

object ExternalUpdate {

  implicit val EncoderInstance: Decoder[ExternalUpdate] =
    Decoder.forProduct3("device", "updateId", "size")(ExternalUpdate.apply)

}

class ResolverHttpClient(httpClient: HttpRequest => Future[HttpResponse])(implicit system: ActorSystem,
                                                                          mat: Materializer)
    extends ServiceHttpClient(httpClient)
    with ResolverClient {

  import akka.http.scaladsl.client.RequestBuilding._
  import ServiceHttpClient._
  import system.dispatcher

  override def availableUpdatesFor(resolverUri: Uri,
                                   ns: Namespace,
                                   devices: Set[DeviceId]): Future[Seq[ExternalUpdateId]] = {
    val query   = Uri.Query(Map("ids" -> devices.map(_.uuid).mkString(",")))
    val request = HttpRequest(HttpMethods.GET, resolverUri.withQuery(query)).withNs(ns)
    execHttpUnmarshalled[Seq[ExternalUpdateId]](request).ok
  }

  override def updatesForDevice(resolverUri: Uri, ns: Namespace, deviceId: DeviceId): Future[List[ExternalUpdate]] = {
    val uri = resolverUri.withQuery(Uri.Query("device" -> deviceId.uuid.toString))
    execHttpUnmarshalled[List[ExternalUpdate]](Get(uri).withNs(ns)).ok
  }
}
