package com.advancedtelematic.director.daemon

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives
import com.advancedtelematic.director.Boot.dbConfig
import com.advancedtelematic.director.{Settings, VersionInfo}
import com.advancedtelematic.libats.http.{BootApp, BootAppDefaultConfig}
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.messaging.{BusListenerMetrics, MessageListenerSupport, MetricsBusMonitor}
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.libtuf_server.data.Messages.TufTargetAdded
import com.advancedtelematic.metrics.MetricsSupport
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config

import scala.concurrent.Future

class DirectorDaemon(override val appConfig: Config, override val dbConfig: Config, override val metricRegistry: MetricRegistry)
                    (implicit val system: ActorSystem) extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with BootMigrations
  with DatabaseSupport
  with MetricsSupport
  with MessageListenerSupport
  with PrometheusMetricsSupport {

  import system.dispatcher

  def bind(): Future[ServerBinding] = {
    startListener[TufTargetAdded](new TufTargetAddedListener, new MetricsBusMonitor(metricRegistry, "director-v2-tuf-target-added"))

    startListener[DeleteDeviceRequest](new DeleteDeviceRequestListener, new MetricsBusMonitor(metricRegistry, "director-v2-delete-device-request"))

    val routes = versionHeaders(nameVersion) {
      prometheusMetricsRoutes ~
        DbHealthResource(versionMap, healthMetrics = Seq(new BusListenerMetrics(metricRegistry)), metricRegistry = metricRegistry).route
    }

    Http().bindAndHandle(routes, host, port)
  }
}

object DaemonBoot extends BootAppDefaultConfig with VersionInfo {
  new DirectorDaemon(appConfig, dbConfig, MetricsSupport.metricRegistry).bind()
}
