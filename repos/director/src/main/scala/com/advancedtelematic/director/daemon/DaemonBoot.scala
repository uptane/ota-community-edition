package com.advancedtelematic.director.daemon


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives
import com.advancedtelematic.director.{Settings, VersionInfo}
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.messaging.{BusListenerMetrics, MessageListenerSupport, MetricsBusMonitor}
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.libtuf_server.data.Messages.TufTargetAdded
import com.advancedtelematic.metrics.MetricsSupport
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import com.advancedtelematic.libats.http.VersionDirectives._
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.security.Security
import scala.concurrent.Future

class DirectorDaemonBoot(override val globalConfig: Config, override val dbConfig: Config,
                         override val metricRegistry: MetricRegistry)
                        (implicit override val system: ActorSystem) extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with BootMigrations
  with DatabaseSupport
  with MetricsSupport
  with MessageListenerSupport
  with PrometheusMetricsSupport {

  implicit val _db = db

  import system.dispatcher

  def bind(): Future[ServerBinding] = {
    startListener[TufTargetAdded](new TufTargetAddedListener, new MetricsBusMonitor(metricRegistry, "director-v2-tuf-target-added"))

    startListener[DeleteDeviceRequest](new DeleteDeviceRequestListener, new MetricsBusMonitor(metricRegistry, "director-v2-delete-device-request"))

    val routes = versionHeaders(version) {
      prometheusMetricsRoutes ~
        DbHealthResource(versionMap, healthMetrics = Seq(new BusListenerMetrics(metricRegistry))).route
    }

    Http().newServerAt(host, daemonPort).bindFlow(routes)
  }

}

object DaemonBoot extends BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  Security.addProvider(new BouncyCastleProvider())

  def main(args: Array[String]): Unit = {
    new DirectorDaemonBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
  }
}
