package com.advancedtelematic.tuf.keyserver.http

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import com.advancedtelematic.libats.http.{ErrorHandler, HealthCheck}
import com.advancedtelematic.libats.http.DefaultRejectionHandler._
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.metrics.MetricsSupport
import com.advancedtelematic.tuf.keyserver.VersionInfo
import com.codahale.metrics.MetricRegistry

import scala.concurrent.ExecutionContext
import slick.jdbc.MySQLProfile.api._

class TufKeyserverRoutes(dependencyChecks: Seq[HealthCheck] = Seq.empty, metricsRoutes: Route = Directives.reject,
                         metricRegistry: MetricRegistry = MetricsSupport.metricRegistry)
                        (implicit val db: Database, val ec: ExecutionContext, system: ActorSystem, mat: Materializer)
  extends VersionInfo {

  import Directives._

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new RootRoleResource().route
        } ~ DbHealthResource(versionMap, dependencies = dependencyChecks, metricRegistry = metricRegistry).route ~ metricsRoutes
      }
    }
}
