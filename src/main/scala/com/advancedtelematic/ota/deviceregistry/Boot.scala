/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.settings.{ParserSettings, ServerSettings}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http._
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.messaging._
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.{CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpConnectionMetrics, AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.ota.deviceregistry.db.DeviceRepository
import com.advancedtelematic.ota.deviceregistry.http.`application/toml`
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.Try

trait Settings {
  private lazy val _config = ConfigFactory.load().getConfig("ats.device-registry")

  val directorUri = Uri(_config.getString("http.client.director.uri"))

  val host = _config.getString("http.server.host")
  val port = _config.getInt("http.server.port")

  val daemonPort = if(_config.hasPath("http.server.daemon-port")) _config.getInt("http.server.daemon-port") else port
}

class DeviceRegistryBoot(override val globalConfig: Config,
                         override val dbConfig: Config,
                         override val metricRegistry: MetricRegistry)
                        (implicit override val system: ActorSystem) extends BootApp
  with AkkaHttpRequestMetrics
  with AkkaHttpConnectionMetrics
  with CheckMigrations
  with DatabaseSupport
  with DatabaseMetrics
  with Directives
  with MetricsSupport
  with PrometheusMetricsSupport
  with VersionInfo
  with Settings
  with ServiceHttpClientSupport {

  import VersionDirectives._

  lazy val authNamespace = NamespaceDirectives.fromConfig()

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import system.dispatcher

  def bind(): Future[ServerBinding] = {
    lazy val messageBus = MessageBus.publisher(system, globalConfig)

    lazy val namespaceAuthorizer = AllowUUIDPath.deviceUUID(authNamespace, deviceAllowed)

    def deviceAllowed(deviceId: DeviceId): Future[Namespace] =
      db.run(DeviceRepository.deviceNamespace(deviceId))

    val tracing = Tracing.fromConfig(globalConfig, projectName)

    val routes: Route =
      (LogDirectives.logResponseMetrics("device-registry") & requestMetrics(metricRegistry) & versionHeaders(version)) {
        prometheusMetricsRoutes ~
          tracing.traceRequests { implicit serverRequestTracing =>
            new DeviceRegistryRoutes(authNamespace, namespaceAuthorizer, messageBus).route
          }
      } ~ DbHealthResource(versionMap, healthMetrics = Seq(new BusListenerMetrics(metricRegistry))).route

    val parserSettings = ParserSettings.forServer(system).withCustomMediaTypes(`application/toml`.mediaType)
    val serverSettings = ServerSettings(system).withParserSettings(parserSettings)

    log.info(s"device registry started at http://$host:$port/")

    sys.addShutdownHook {
      Try(db.close())
      Try(system.terminate())
    }

    Http().newServerAt(host, port).withSettings(serverSettings).bindFlow(withConnectionMetrics(routes, metricRegistry))
  }
}

object Boot extends BootAppDefaultConfig with VersionInfo with BootAppDatabaseConfig {
  def main(args: Array[String]): Unit = {
    new DeviceRegistryBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
  }
}