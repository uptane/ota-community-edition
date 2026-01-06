package com.advancedtelematic.director

import com.advancedtelematic.director.http.deviceregistry.TomlSupport.`application/toml`
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.settings.{ParserSettings, ServerSettings}
import com.advancedtelematic.director.db.deviceregistry.DeviceRepository
import com.advancedtelematic.director.deviceregistry.AllowUUIDPath
import com.advancedtelematic.director.http.DirectorRoutes
import com.advancedtelematic.director.http.deviceregistry.DeviceRegistryRoutes
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.DefaultRejectionHandler.rejectionHandler
import com.advancedtelematic.libats.http.LogDirectives.logResponseMetrics
import com.advancedtelematic.libats.http.VersionDirectives.versionHeaders
import com.advancedtelematic.libats.http.monitoring.ServiceHealthCheck
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.http.{
  BootApp,
  BootAppDatabaseConfig,
  BootAppDefaultConfig,
  NamespaceDirectives
}
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.{CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverHttpClient
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{
  AkkaHttpConnectionMetrics,
  AkkaHttpRequestMetrics,
  MetricsSupport
}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import java.security.Security
import scala.concurrent.Future

class DirectorBoot(override val globalConfig: Config,
                   override val dbConfig: Config,
                   override val metricRegistry: MetricRegistry)(
  implicit override val system: ActorSystem)
    extends BootApp
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

  implicit val _db: slick.jdbc.MySQLProfile.backend.Database = db
  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  log.info(s"Starting $nameVersion on http://$host:$port")

  private lazy val tracing = Tracing.fromConfig(globalConfig, projectName)

  private def keyserverClient(implicit tracing: ServerRequestTracing) = KeyserverHttpClient(tufUri)

  private implicit val msgPublisher: com.advancedtelematic.libats.messaging.MessageBusPublisher =
    MessageBus.publisher(system, globalConfig)

  private lazy val authNamespace = NamespaceDirectives.fromConfig()

  def deviceAllowed(deviceId: DeviceId): Future[Namespace] =
    db.run(DeviceRepository.deviceNamespace(deviceId))

  private lazy val namespaceAuthorizer = AllowUUIDPath.deviceUUID(authNamespace, deviceAllowed)

  def bind(): Future[ServerBinding] = {
    val routes: Route =
      DbHealthResource(versionMap, dependencies = Seq(new ServiceHealthCheck(tufUri))).route ~
        (logRequestResult("directorv2-request-result" -> requestLogLevel) & versionHeaders(
          nameVersion
        ) & requestMetrics(metricRegistry) & logResponseMetrics(
          projectName
        ) & tracing.traceRequests) { implicit requestTracing =>
          prometheusMetricsRoutes ~
            handleRejections(rejectionHandler) {
              new DirectorRoutes(keyserverClient, allowEcuReplacement).routes ~
                pathPrefix("device-registry") {
                  new DeviceRegistryRoutes(
                    NamespaceDirectives.defaultNamespaceExtractor,
                    namespaceAuthorizer,
                    msgPublisher
                  ).route
                }
            }
        }

    val parserSettings =
      ParserSettings.forServer(system).withCustomMediaTypes(`application/toml`.mediaType)
    val serverSettings = ServerSettings(system).withParserSettings(parserSettings)

    Http()
      .newServerAt(host, port)
      .withSettings(serverSettings)
      .bindFlow(withConnectionMetrics(routes, metricRegistry))
  }

}

object Boot extends BootAppDefaultConfig with VersionInfo with BootAppDatabaseConfig {
  Security.addProvider(new BouncyCastleProvider)

  def main(args: Array[String]): Unit =
    new DirectorBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()

}
