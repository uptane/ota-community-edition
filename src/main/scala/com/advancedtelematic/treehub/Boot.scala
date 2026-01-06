package com.advancedtelematic.treehub

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.http.LogDirectives.*
import com.advancedtelematic.libats.http.VersionDirectives.*
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig, NamespaceDirectives}
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.{BootMigrations, CheckMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{AkkaHttpConnectionMetrics, AkkaHttpRequestMetrics, MetricsSupport}
import com.advancedtelematic.treehub.daemon.{DeletedDeltaCleanupActor, StaleObjectArchiveActor}
import com.advancedtelematic.treehub.delta_store.StaticDeltas
import com.advancedtelematic.treehub.http.TreeHubRoutes
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore, S3BlobStore}
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import scala.concurrent.Future

// BootApp with Directives with Settings with VersionInfo
class TreehubBoot(override val globalConfig: Config,
                  override val dbConfig: Config,
                  override val metricRegistry: MetricRegistry)
                 (implicit override val system: ActorSystem)
  extends BootApp
    with BootMigrations
    with DatabaseSupport
    with MetricsSupport
    with DatabaseMetrics
    with AkkaHttpRequestMetrics
    with AkkaHttpConnectionMetrics
    with PrometheusMetricsSupport
    with CheckMigrations
    with VersionInfo
    with Directives
    with Settings {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import system.dispatcher

  def bind(): Future[ServerBinding] = {

    log.info(s"Starting $nameVersion on http://$host:$port")

    val namespaceExtractor = NamespaceDirectives.defaultNamespaceExtractor

    lazy val objectStorage = {
      if (useS3) {
        log.info("Using s3 storage for object blobs")
        S3BlobStore(s3Credentials, allowRedirectsToS3, root = None)
      } else {
        log.info(s"Using local storage a t$localStorePath for object blobs")
        LocalFsBlobStore(localStorePath.resolve("object-storage"))
      }
    }

    lazy val deltaBlobStorage = {
      if (useS3) {
        log.info("Using s3 storage for object blobs")
        // ostree up to at least version 2023.5 does not follow redirects for static deltas
        S3BlobStore(s3Credentials, allowRedirects = false, root = Some(Paths.get("deltas")))
      } else {
        log.info(s"Using local storage at $localStorePath for object blobs")
        LocalFsBlobStore(localStorePath.resolve("deltas"))
      }
    }

    val objectStore = new ObjectStore(objectStorage)
    val msgPublisher = MessageBus.publisher(system, globalConfig)
    val tracing = Tracing.fromConfig(globalConfig, projectName)

    val usageHandler = system.actorOf(UsageMetricsRouter(msgPublisher, objectStore), "usage-router")

    if (objectStorage.supportsOutOfBandStorage) {
      system.actorOf(StaleObjectArchiveActor.withBackOff(objectStorage, staleObjectExpireAfter, autoStart = true), "stale-objects-archiver")
    }

    system.actorOf(DeletedDeltaCleanupActor.withBackOff(deltaBlobStorage, autoStart = true), "deleted-deltas-cleanup")

    val routes: Route =
      (versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName)) {
        prometheusMetricsRoutes ~
          tracing.traceRequests { _ =>
            new TreeHubRoutes(
              namespaceExtractor,
              msgPublisher,
              objectStore,
              new StaticDeltas(deltaBlobStorage),
              usageHandler
            ).routes
          }
      }

    Http().newServerAt(host, port).bindFlow(withConnectionMetrics(routes, metricRegistry))
  }
}

object Boot extends BootAppDefaultConfig with VersionInfo with BootAppDatabaseConfig {

  def main(args: Array[String]): Unit =
    new TreehubBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()
}
