package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.*
import akka.stream.Materializer
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.data.ErrorRepresentation.*
import com.advancedtelematic.libats.http.{DefaultRejectionHandler, ErrorHandler}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.treehub.VersionInfo
import com.advancedtelematic.treehub.delta_store.StaticDeltas
import com.advancedtelematic.treehub.object_store.ObjectStore
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.amazonaws.SdkClientException
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext

class TreeHubRoutes(namespaceExtractor: Directive1[Namespace],
                    messageBus: MessageBusPublisher,
                    objectStore: ObjectStore,
                    deltas: StaticDeltas,
                    usageHandler: UsageMetricsRouter.HandlerRef)
                   (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {

  import Directives.*

  def allRoutes(nsExtract: Directive1[Namespace]): Route = {
    new ConfResource().route ~
    new ObjectResource(nsExtract, objectStore, usageHandler).route ~
    new RefResource(nsExtract, objectStore).route ~
    new ManifestResource(nsExtract, messageBus).route ~
    new DeltaResource(nsExtract, deltas, usageHandler).route
  }

  val treehubExceptionHandler = handleExceptions(ExceptionHandler.apply {
    case ex: SdkClientException if ex.getMessage.contains("Timeout on waiting") =>
      complete(StatusCodes.RequestTimeout -> ErrorRepresentation(ErrorCodes.TimeoutOnWaiting, ex.getMessage))
  })

  val routes: Route =
    handleRejections(DefaultRejectionHandler.rejectionHandler) {
      ErrorHandler.handleErrors {
        treehubExceptionHandler {
          pathPrefix("api" / "v2") {
            allRoutes(namespaceExtractor)
          } ~
            pathPrefix("api" / "v3") {
              allRoutes(namespaceExtractor)
            } ~ DbHealthResource(versionMap).route
        }
      }
    }
}
