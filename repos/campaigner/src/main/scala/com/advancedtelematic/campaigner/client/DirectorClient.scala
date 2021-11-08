package com.advancedtelematic.campaigner.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.syntax.show._
import com.advancedtelematic.campaigner.data.DataType.ExternalUpdateId
import com.advancedtelematic.libats.data.DataType.{CorrelationId, MultiTargetUpdateId, Namespace}
import com.advancedtelematic.libats.http.HttpOps.HttpRequestOps
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.codecs.CirceAnyVal._
import java.util.UUID

import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.tracing.TracingHttpClient
import io.circe._
import io.circe.generic.semiauto._

import scala.concurrent.Future

final case class AssignUpdateRequest(
  correlationId: CorrelationId,
  devices: Seq[DeviceId],
  mtuId: ExternalUpdateId,
  dryRun: Boolean = false)

object AssignUpdateRequest {
  def apply(devices: Seq[DeviceId], updateId: ExternalUpdateId, dryRun: Boolean): AssignUpdateRequest =
    AssignUpdateRequest(
      MultiTargetUpdateId(UUID.fromString(updateId.value)),
      devices, updateId, dryRun)

  implicit val assignUpdateRequestEncoder: Encoder[AssignUpdateRequest] = deriveEncoder
}

trait DirectorClient {

  def setMultiUpdateTarget(
    ns: Namespace,
    updateId: ExternalUpdateId,
    devices: Seq[DeviceId],
    correlationId: CorrelationId): Future[Seq[DeviceId]]

  def findAffected(ns: Namespace, updateId: ExternalUpdateId, devices: Seq[DeviceId]): Future[Seq[DeviceId]]

  def cancelUpdate(
    ns: Namespace,
    devices: Seq[DeviceId]): Future[Seq[DeviceId]]

  def cancelUpdate(
    ns: Namespace,
    device: DeviceId): Future[Unit]
}

class DirectorHttpClient(uri: Uri, httpClient: HttpRequest => Future[HttpResponse])
    (implicit system: ActorSystem, mat: Materializer, tracing: ServerRequestTracing)
    extends TracingHttpClient(httpClient, "director") with DirectorClient {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.syntax._
  import com.advancedtelematic.libats.http.ServiceHttpClient._
  import system.dispatcher

  override def setMultiUpdateTarget(
    ns: Namespace,
    updateId: ExternalUpdateId,
    devices: Seq[DeviceId],
    correlationId: CorrelationId): Future[Seq[DeviceId]] = {
    val path   = uri.path / "api" / "v1" / "assignments"
    val entity = HttpEntity(
      ContentTypes.`application/json`,
      AssignUpdateRequest(correlationId, devices, updateId).asJson.noSpaces)
    val req = HttpRequest(
      HttpMethods.POST,
      uri.withPath(path),
      entity = entity).withNs(ns)
    execHttpUnmarshalled[Seq[DeviceId]](req).ok
  }

  override def cancelUpdate(
    ns: Namespace,
    devices: Seq[DeviceId]): Future[Seq[DeviceId]] = {

    val path   = uri.path / "api" / "v1" / "assignments"
    val entity = HttpEntity(ContentTypes.`application/json`, devices.asJson.noSpaces)
    val req = HttpRequest(HttpMethods.PATCH, uri.withPath(path), entity = entity).withNs(ns)
    execHttpUnmarshalled[Seq[DeviceId]](req).ok
  }

  override def cancelUpdate(
    ns: Namespace,
    device: DeviceId): Future[Unit] = {

    val path = uri.path / "api" / "v1" / "assignments" / device.show
    val req = HttpRequest(HttpMethods.DELETE, uri.withPath(path)).withNs(ns)
    execHttpUnmarshalled[Unit](req).ok
  }

  override def findAffected(ns: Namespace, updateId: ExternalUpdateId, devices: Seq[DeviceId]): Future[Seq[DeviceId]] = {
    val path   = uri.path / "api" / "v1" / "assignments"
    val entity = HttpEntity(
      ContentTypes.`application/json`,
      AssignUpdateRequest(devices, updateId, true).asJson.noSpaces)
    val req = HttpRequest(HttpMethods.POST, uri.withPath(path), entity = entity).withNs(ns)
    execHttpUnmarshalled[Seq[DeviceId]](req).ok
  }
}
