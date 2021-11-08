package com.advancedtelematic.director


import akka.actor.ActorSystem

import java.security.Security
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.director.http.DirectorRoutes
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.http.LogDirectives.logResponseMetrics
import com.advancedtelematic.libats.http.VersionDirectives.versionHeaders
import com.advancedtelematic.libats.http.monitoring.ServiceHealthCheck
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.{CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverHttpClient
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpConnectionMetrics, AkkaHttpRequestMetrics, MetricsSupport}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import scala.concurrent.Future


class DirectorBoot(override val globalConfig: Config,
                   override val dbConfig: Config,
                   override val metricRegistry: MetricRegistry)
                  (implicit override val system: ActorSystem) extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with DatabaseSupport
  with MetricsSupport
  with DatabaseMetrics
  with AkkaHttpRequestMetrics
  with AkkaHttpConnectionMetrics
  with PrometheusMetricsSupport
  with CheckMigrations {

  implicit val _db = db
  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  log.info(s"Starting $version on http://$host:$port")

  lazy val tracing = Tracing.fromConfig(globalConfig, projectName)

  def keyserverClient(implicit tracing: ServerRequestTracing) = KeyserverHttpClient(tufUri)
  implicit val msgPublisher = MessageBus.publisher(system, globalConfig)

  def bind(): Future[ServerBinding] = {
    val routes: Route =
      DbHealthResource(versionMap, dependencies = Seq(new ServiceHealthCheck(tufUri))).route ~
        (logRequestResult("directorv2-request-result" -> requestLogLevel) & versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName) & tracing.traceRequests) { implicit requestTracing =>
          prometheusMetricsRoutes ~
            new DirectorRoutes(keyserverClient, allowEcuReplacement).routes
        }

    Http().newServerAt(host, port).bindFlow(withConnectionMetrics(routes, metricRegistry))
  }
}

object Boot extends BootAppDefaultConfig with VersionInfo with BootAppDatabaseConfig {
  Security.addProvider(new BouncyCastleProvider)

  def main(args: Array[String]): Unit = {
    new DirectorBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
  }
}

