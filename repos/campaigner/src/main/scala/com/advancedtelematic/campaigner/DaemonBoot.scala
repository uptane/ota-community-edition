package com.advancedtelematic.campaigner

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import com.advancedtelematic.campaigner.actor._
import com.advancedtelematic.campaigner.client._
import com.advancedtelematic.campaigner.daemon._
import com.advancedtelematic.libats.http.tracing.NullServerRequestTracing
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig, ServiceHttpClientSupport}
import com.advancedtelematic.libats.messaging.MessageListenerSupport
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceEventMessage, DeviceUpdateEvent}
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.metrics.{MetricsSupport, MonitoredBusListenerSupport}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class CampaignerDaemon(override val appConfig: Config,
                       override val dbConfig: Config,
                       override val metricRegistry: MetricRegistry)
                      (implicit override val system: ActorSystem) extends BootApp
  with Settings
  with VersionInfo
  with BootMigrations
  with DatabaseSupport
  with MetricsSupport
  with DatabaseMetrics
  with CheckMigrations
  with MessageListenerSupport
  with MonitoredBusListenerSupport
  with PrometheusMetricsSupport
  with ServiceHttpClientSupport {

  import akka.http.scaladsl.server.Directives._
  import com.advancedtelematic.libats.http.LogDirectives._
  import com.advancedtelematic.libats.http.VersionDirectives._
  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def bind(): Future[ServerBinding] = {
    log.info("Starting campaigner daemon")

    implicit val tracing = new NullServerRequestTracing

    val director = new DirectorHttpClient(directorUri, defaultHttpClient)
    system.actorOf(CampaignSupervisor.props(
      director,
      schedulerPollingTimeout,
      schedulerDelay,
      schedulerBatchSize
    ),
      "campaign-supervisor"
    )

    startMonitoredListener[DeviceUpdateEvent](new DeviceUpdateEventListener)
    startMonitoredListener[DeviceEventMessage](new DeviceEventListener(director), skipProcessingErrors = true)

    val routes: Route = (versionHeaders(version) & logResponseMetrics(projectName)) {
      prometheusMetricsRoutes ~
        DbHealthResource(versionMap, metricRegistry = metricRegistry).route
    }

    Http().bindAndHandle(routes, host, port)
  }
}

object DaemonBoot extends BootApp with BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
    new CampaignerDaemon(appConfig, dbConfig, MetricsSupport.metricRegistry).bind()
}