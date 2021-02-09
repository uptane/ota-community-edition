package com.advancedtelematic.treehub

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.treehub.client._
import com.advancedtelematic.treehub.daemon.StaleObjectArchiveActor
import com.advancedtelematic.treehub.delta_store.{LocalDeltaStorage, S3DeltaStorage}
import com.advancedtelematic.treehub.http.{TreeHubRoutes, Http => TreeHubHttp}
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore, S3BlobStore}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class TreehubBoot(override val appConfig: Config,
                  override val dbConfig: Config,
                  override val metricRegistry: MetricRegistry)
                 (implicit override val system: ActorSystem) extends BootApp
  with BootMigrations
  with DatabaseSupport
  with MetricsSupport
  with DatabaseMetrics
  with AkkaHttpRequestMetrics
  with PrometheusMetricsSupport
  with CheckMigrations
  with VersionInfo
  with Directives
  with Settings {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import system.dispatcher

  def bind(): Future[ServerBinding] = {

    log.info(s"Starting ${nameVersion} on http://$host:$port")

    val deviceRegistry = new DeviceRegistryHttpClient(deviceRegistryUri, deviceRegistryMyApi)

    val tokenValidator = TreeHubHttp.tokenValidator
    val namespaceExtractor = TreeHubHttp.extractNamespace.map(_.namespace.get).map(Namespace.apply)
    val deviceNamespace = TreeHubHttp.deviceNamespace(deviceRegistry)

    lazy val objectStorage = {
      if (useS3) {
        log.info("Using s3 storage for object blobs")
        S3BlobStore(s3Credentials, allowRedirectsToS3)
      } else {
        log.info(s"Using local storage a t$localStorePath for object blobs")
        LocalFsBlobStore(localStorePath.resolve("object-storage"))
      }
    }

    lazy val deltaStorage = {
      if (useS3) {
        log.info("Using s3 storage for object blobs")
        S3DeltaStorage(s3Credentials)
      } else {
        log.info(s"Using local storage at $localStorePath for object blobs")
        new LocalDeltaStorage(localStorePath.resolve("delta-storage"))
      }
    }

    val objectStore = new ObjectStore(objectStorage)
    val msgPublisher = MessageBus.publisher(system, appConfig)
    val tracing = Tracing.fromConfig(appConfig, projectName)

    val usageHandler = system.actorOf(UsageMetricsRouter(msgPublisher, objectStore), "usage-router")

    if (objectStorage.supportsOutOfBandStorage) {
      system.actorOf(StaleObjectArchiveActor.withBackOff(objectStorage, staleObjectExpireAfter, autoStart = true), "stale-objects-archiver")
    }

    val routes: Route =
      (versionHeaders(nameVersion) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
        prometheusMetricsRoutes ~
          tracing.traceRequests { _ =>
            new TreeHubRoutes(tokenValidator, namespaceExtractor,
              deviceNamespace, msgPublisher,
              objectStore, deltaStorage, usageHandler).routes
          }
      }

    Http().bindAndHandle(routes, host, port)
  }
}

object Boot extends BootAppDefaultConfig with VersionInfo with BootAppDatabaseConfig {
  new TreehubBoot(appConfig, dbConfig, MetricsSupport.metricRegistry).bind()
}
