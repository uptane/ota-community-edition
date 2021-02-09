package com.advancedtelematic.campaigner

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.campaigner.client.{DeviceRegistryHttpClient, ResolverHttpClient, UserProfileHttpClient}
import com.advancedtelematic.campaigner.http.Routes
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig, ServiceHttpClientSupport}
import com.advancedtelematic.libats.slick.db.{CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.metrics.{AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import scala.concurrent.Future

trait Settings {
  import java.util.concurrent.TimeUnit

  import com.typesafe.config.ConfigFactory

  import scala.concurrent.duration._

  private lazy val _config = ConfigFactory.load().getConfig("ats.campaigner")

  val host = _config.getString("http.server.host")
  val port = _config.getInt("http.server.port")

  val deviceRegistryUri = _config.getString("http.client.deviceregistry.uri")
  val directorUri = _config.getString("http.client.director.uri")
  val userProfileUri = _config.getString("http.client.userprofile.uri")

  val schedulerPollingTimeout =
    FiniteDuration(_config.getDuration("scheduler.pollingTimeout").toNanos, TimeUnit.NANOSECONDS)
  val schedulerDelay =
    FiniteDuration(_config.getDuration("scheduler.delay").toNanos, TimeUnit.NANOSECONDS)
  val schedulerBatchSize =
    _config.getInt("scheduler.batchSize")
}

class CampaignerBoot(override val appConfig: Config,
                     override val dbConfig: Config,
                     override val metricRegistry: MetricRegistry)
                    (implicit override val system: ActorSystem) extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with DatabaseSupport
  with MetricsSupport
  with DatabaseMetrics
  with CheckMigrations
  with AkkaHttpRequestMetrics
  with PrometheusMetricsSupport
  with ServiceHttpClientSupport {

  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def bind(): Future[ServerBinding] = {
    log.info(s"Starting ${nameVersion} on http://$host:$port")

    def deviceRegistry(implicit tracing: ServerRequestTracing) = new DeviceRegistryHttpClient(deviceRegistryUri, defaultHttpClient)
    def userProfile(implicit tracing: ServerRequestTracing) = new UserProfileHttpClient(userProfileUri, defaultHttpClient)
    val resolver = new ResolverHttpClient(defaultHttpClient)

    val tracing = Tracing.fromConfig(appConfig, projectName)

    val routes: Route =
      (versionHeaders(nameVersion) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
        prometheusMetricsRoutes ~
          tracing.traceRequests { implicit serverRequestTracing =>
            new Routes(deviceRegistry, resolver, userProfile).routes
          }
      }

    Http().bindAndHandle(routes, host, port)
  }
}

object Boot extends BootApp with BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  new CampaignerBoot(appConfig, dbConfig, MetricsSupport.metricRegistry).bind()
}
