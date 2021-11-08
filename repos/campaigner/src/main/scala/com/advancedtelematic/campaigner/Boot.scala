package com.advancedtelematic.campaigner

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.campaigner.client.{DeviceRegistryHttpClient, ResolverHttpClient, UserProfileHttpClient}
import com.advancedtelematic.campaigner.db.Campaigns
import com.advancedtelematic.campaigner.http.Routes
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.{BootApp, ServiceHttpClientSupport}
import com.advancedtelematic.libats.slick.db.{CheckMigrations, DatabaseConfig}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.metrics.{AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport

trait Settings {
  import java.util.concurrent.TimeUnit

  import com.typesafe.config.ConfigFactory

  import scala.concurrent.duration._

  private lazy val _config = ConfigFactory.load()

  val host = _config.getString("server.host")
  val port = _config.getInt("server.port")

  val deviceRegistryUri = _config.getString("deviceRegistry.uri")
  val directorUri = _config.getString("director.uri")
  val userProfileUri = _config.getString("userProfile.uri")

  val schedulerPollingTimeout =
    FiniteDuration(_config.getDuration("scheduler.pollingTimeout").toNanos, TimeUnit.NANOSECONDS)
  val schedulerDelay =
    FiniteDuration(_config.getDuration("scheduler.delay").toNanos, TimeUnit.NANOSECONDS)
  val schedulerBatchSize =
    _config.getInt("scheduler.batchSize")
}

object Boot extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with DatabaseConfig
  with MetricsSupport
  with DatabaseMetrics
  with CheckMigrations
  with AkkaHttpRequestMetrics
  with PrometheusMetricsSupport
  with ServiceHttpClientSupport {

  implicit val _db = db

  log.info(s"Starting $version on http://$host:$port")

  def deviceRegistry(implicit tracing: ServerRequestTracing) = new DeviceRegistryHttpClient(deviceRegistryUri, defaultHttpClient)
  def userProfile(implicit tracing: ServerRequestTracing) = new UserProfileHttpClient(userProfileUri, defaultHttpClient)
  val resolver = new ResolverHttpClient(defaultHttpClient)

  val tracing = Tracing.fromConfig(config, projectName)

  val campaigns = Campaigns()

  val routes: Route =
    (versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
      prometheusMetricsRoutes ~
        tracing.traceRequests { implicit serverRequestTracing =>
          new Routes(deviceRegistry, resolver, userProfile, campaigns).routes
        }
    }

  Http().bindAndHandle(routes, host, port)
}
