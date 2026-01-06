package com.advancedtelematic.tuf.keyserver.http

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.server.{Directives, _}
import org.apache.pekko.stream.Materializer
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.http.DefaultRejectionHandler._
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.metrics.{HealthCheck, MetricsSupport}
import com.advancedtelematic.tuf.keyserver.VersionInfo
import com.codahale.metrics.MetricRegistry

import scala.concurrent.ExecutionContext
import slick.jdbc.MySQLProfile.api._

class TufKeyserverRoutes(dependencyChecks: Seq[HealthCheck] = Seq.empty,
                         metricsRoutes: Route = Directives.reject,
                         metricRegistry: MetricRegistry = MetricsSupport.metricRegistry)(
  implicit val db: Database,
  val ec: ExecutionContext,
  mat: Materializer)
    extends VersionInfo {

  import Directives._

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new RootRoleResource().route
        } ~ DbHealthResource(
          versionMap,
          dependencies = dependencyChecks,
          metricRegistry = metricRegistry
        ).route ~ metricsRoutes
      }
    }

}
