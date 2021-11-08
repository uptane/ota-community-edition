package com.advancedtelematic.campaigner.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import cats.syntax.show._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.http.HttpOps.HttpRequestOps
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.tracing.TracingHttpClient
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import io.circe.Decoder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

trait DeviceRegistryClient {
  def devicesInGroup(namespace: Namespace,
                     groupId: GroupId,
                     offset: Long,
                     limit: Long): Future[Seq[DeviceId]]

  def allDevicesInGroup(namespace: Namespace,
                         groupId: GroupId,
                         patience: Duration = 10.seconds)
                        (implicit ec: ExecutionContext) : Future[Seq[DeviceId]] = {
    val start = System.currentTimeMillis

    def fetchDevices(groupId: GroupId, offset: Long, limit: Long): Future[Seq[DeviceId]] = {
       devicesInGroup(namespace, groupId, offset, limit)
        .flatMap { ds =>
          val spent = (System.currentTimeMillis - start).millis
          ds match {
            case Nil => FastFuture.successful(Seq.empty[DeviceId])
            case _ if patience - spent < 0.millis => FastFuture.failed(new TimeoutException)
            case _ => fetchDevices(groupId, offset + ds.length.toLong, limit).map(ds ++ _)
          }
        }
    }

    fetchDevices(groupId, offset = 0, limit = 1024)
  }

  def fetchOemId(ns: Namespace, deviceIds: DeviceId): Future[String]
}

class DeviceRegistryHttpClient(uri: Uri, httpClient: HttpRequest => Future[HttpResponse])
    (implicit system: ActorSystem, mat: Materializer, tracing: ServerRequestTracing)
    extends TracingHttpClient(httpClient, "device-registry") with DeviceRegistryClient {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import com.advancedtelematic.libats.http.ServiceHttpClient._
  import system.dispatcher

  override def devicesInGroup(ns: Namespace, groupId: GroupId, offset: Long, limit: Long): Future[Seq[DeviceId]] = {
    val path  = uri.path / "api" / "v1" / "device_groups" / groupId.show / "devices"
    val query = Uri.Query(Map("offset" -> offset.toString, "limit" -> limit.toString))
    val req = HttpRequest(HttpMethods.GET, uri.withPath(path).withQuery(query)).withNs(ns)
    execHttpUnmarshalled[PaginationResult[DeviceId]](req).ok.map(_.values)
  }

  override def fetchOemId(ns: Namespace, deviceId: DeviceId): Future[String] = {
    val path  = uri.path / "api" / "v1" / "devices" / deviceId.show
    val req = HttpRequest(HttpMethods.GET, uri.withPath(path)).withNs(ns)
    implicit val um = unmarshaller[String](Decoder.instance(_.get("deviceId")))
    execHttpUnmarshalled[String](req).ok
  }
}
