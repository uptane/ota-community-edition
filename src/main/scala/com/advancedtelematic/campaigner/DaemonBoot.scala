package com.advancedtelematic.campaigner

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.advancedtelematic.campaigner.actor._
import com.advancedtelematic.campaigner.client._
import com.advancedtelematic.campaigner.daemon._
import com.advancedtelematic.campaigner.db.{Campaigns, Repositories}
import com.advancedtelematic.libats.http.tracing.NullServerRequestTracing
import com.advancedtelematic.libats.http.{BootApp, ServiceHttpClientSupport}
import com.advancedtelematic.libats.messaging.MessageListenerSupport
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeleteDeviceRequest, DeviceEventMessage, DeviceUpdateEvent}
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseConfig}
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{MetricsSupport, MonitoredBusListenerSupport}

object DaemonBoot extends BootApp
  with Settings
  with VersionInfo
  with BootMigrations
  with DatabaseConfig
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

  implicit val _db = db

  log.info("Starting campaigner daemon")

  implicit val tracing = new NullServerRequestTracing

  val deviceRegistry = new DeviceRegistryHttpClient(deviceRegistryUri, defaultHttpClient)

  val director = new DirectorHttpClient(directorUri, defaultHttpClient)

  val routes: Route = (versionHeaders(version) & logResponseMetrics(projectName)) {
    prometheusMetricsRoutes ~
      DbHealthResource(versionMap).route
  }

  val campaigns = Campaigns()

  Http().bindAndHandle(routes, host, port)

  system.actorOf(CampaignSupervisor.props(
    director,
    campaigns,
    schedulerPollingTimeout,
    schedulerDelay,
    schedulerBatchSize
  ), "campaign-supervisor")

  startMonitoredListener[DeviceUpdateEvent](new DeviceUpdateEventListener(campaigns))
  startMonitoredListener[DeviceEventMessage](new DeviceEventListener(director, campaigns), skipProcessingErrors = true)
  startMonitoredListener[DeleteDeviceRequest](new DeleteDeviceRequestListener(director, campaigns))
}
