package com.advancedtelematic.director.daemon

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.server.Directives
import com.advancedtelematic.director.{Settings, VersionInfo}
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.messaging.{
  BusListenerMetrics,
  MessageBus,
  MessageBusPublisher,
  MessageListenerSupport,
  MetricsBusMonitor
}
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeviceEventMessage,
  DeviceUpdateEvent
}
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.libtuf_server.data.Messages.TufTargetAdded
import com.advancedtelematic.metrics.MetricsSupport
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import com.advancedtelematic.libats.http.VersionDirectives.*
import com.advancedtelematic.libats.messaging.metrics.MonitoredBusListenerSupport
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.director.deviceregistry.daemon.{
  DeviceEventListener,
  DeviceUpdateEventListener
}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import DeviceMqttLifecycle.messageLike

import java.security.Security
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class DirectorDaemonBoot(override val globalConfig: Config,
                         override val dbConfig: Config,
                         override val metricRegistry: MetricRegistry)(
  implicit override val system: ActorSystem)
    extends BootApp
    with Directives
    with Settings
    with VersionInfo
    with BootMigrations
    with DatabaseSupport
    with MetricsSupport
    with MonitoredBusListenerSupport
    with MessageListenerSupport
    with PrometheusMetricsSupport {

  implicit val _db: slick.jdbc.MySQLProfile.backend.Database = db

  import system.dispatcher

  implicit lazy val messageBus: MessageBusPublisher = MessageBus.publisher(system, globalConfig)

  def bind(): Future[ServerBinding] = {
    startListener[TufTargetAdded](
      new TufTargetAddedListener,
      new MetricsBusMonitor(metricRegistry, "director-v2-tuf-target-added")
    )

    startMonitoredListener[DeviceEventMessage](new DeviceEventListener)
    startMonitoredListener[DeviceUpdateEvent](new DeviceUpdateEventListener(messageBus))
    startMonitoredListener[DeviceMqttLifecycle](new MqttLifecycleListener)

    new DeviceManifestReportedListener(globalConfig).start()

    val routes = versionHeaders(version) {
      prometheusMetricsRoutes ~
        DbHealthResource(
          versionMap,
          healthMetrics = Seq(new BusListenerMetrics(metricRegistry))
        ).route
    }

    val httpFut = Http().newServerAt(host, daemonPort).bindFlow(routes)

    val updateScheduler = new UpdateSchedulerDaemon().start()

    Future.sequence(List(httpFut, updateScheduler)).flatMap(_ => httpFut)
  }

}

object DaemonBoot extends BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  Security.addProvider(new BouncyCastleProvider())

  def main(args: Array[String]): Unit = {
    val directorDaemonFut =
      new DirectorDaemonBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()

    Await.result(directorDaemonFut, Duration.Inf)
  }

}
