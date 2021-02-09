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
import com.advancedtelematic.libats.auth.NamespaceDirectives
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
  private lazy val _config = ConfigFactory.load().getConfig("ats.deviceregistry")

  lazy val directorUri = Uri(_config.getString("http.client.director.uri"))

  lazy val host = _config.getString("http.server.host")
  lazy val port = _config.getInt("http.server.port")
}

class DeviceRegistryBoot(override val appConfig: Config,
                         override val dbConfig: Config,
                         override val metricRegistry: MetricRegistry)
                        (implicit override val system: ActorSystem) extends BootApp with AkkaHttpRequestMetrics
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

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import system.dispatcher

  def bind(): Future[ServerBinding] = {
    val authNamespace = NamespaceDirectives.fromConfig()

    def deviceAllowed(deviceId: DeviceId): Future[Namespace] =
      db.run(DeviceRepository.deviceNamespace(deviceId))

    val namespaceAuthorizer = AllowUUIDPath.deviceUUID(authNamespace, deviceAllowed)

    lazy val messageBus = MessageBus.publisher(system, appConfig)

    val tracing = Tracing.fromConfig(appConfig, projectName)

    val routes: Route =
      (LogDirectives.logResponseMetrics("device-registry") & requestMetrics(metricRegistry) & versionHeaders(nameVersion)) {
        prometheusMetricsRoutes ~
          tracing.traceRequests { implicit serverRequestTracing =>
            new DeviceRegistryRoutes(authNamespace, namespaceAuthorizer, messageBus).route
          } ~ DbHealthResource(versionMap, healthMetrics = Seq(new BusListenerMetrics(metricRegistry)), metricRegistry = metricRegistry).route
      }

    val parserSettings = ParserSettings(system).withCustomMediaTypes(`application/toml`.mediaType)
    val serverSettings = ServerSettings(system).withParserSettings(parserSettings)

    log.info(s"${nameVersion} started at http://$host:$port/")

    sys.addShutdownHook {
      Try(db.close())
      Try(system.terminate())
    }

    Http().bindAndHandle(withConnectionMetrics(routes, metricRegistry), host, port, settings = serverSettings)
  }
}

object Boot extends BootApp with BootAppDefaultConfig with BootAppDatabaseConfig with VersionInfo {
  new DeviceRegistryBoot(appConfig, dbConfig, MetricsSupport.metricRegistry).bind()
}
