package com.advancedtelematic.tuf.reposerver

import java.security.Security
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.http.LogDirectives._
import com.advancedtelematic.libats.http.VersionDirectives._
import com.advancedtelematic.libats.http.monitoring.ServiceHealthCheck
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.{BootMigrations, DatabaseSupport}
import com.advancedtelematic.libats.slick.monitoring.DatabaseMetrics
import com.advancedtelematic.libtuf_server.keyserver.KeyserverHttpClient
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.advancedtelematic.metrics.{MetricsSupport, PekkoHttpRequestMetrics}
import com.advancedtelematic.tuf.reposerver
import com.advancedtelematic.tuf.reposerver.delegations.HttpRemoteDelegationClient
import com.advancedtelematic.tuf.reposerver.http.{NamespaceValidation, TufReposerverRoutes}
import com.advancedtelematic.tuf.reposerver.target_store._
import com.amazonaws.regions.Regions
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.{Config, ConfigFactory}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{duration, Future}

trait Settings {

  lazy val _config = ConfigFactory.load().getConfig("ats.reposerver")

  lazy val host = _config.getString("http.server.host")
  lazy val port = _config.getInt("http.server.port")

  lazy val keyServerUri = Uri(_config.getString("http.client.keyserver.uri"))

  lazy val targetStoreRoot = _config.getString("storage.localStorageRoot")

  lazy val s3Credentials = {
    val accessKey = _config.getString("storage.s3.accessKey")
    val secretKey = _config.getString("storage.s3.secretKey")
    val bucketId = _config.getString("storage.s3.bucketId")
    val region = Regions.fromName(_config.getString("storage.s3.region"))
    new S3Credentials(accessKey, secretKey, bucketId, region)
  }

  lazy val azureSettings = {
    val azureConfig = _config.getConfig("storage.azure")
    val connectionString = azureConfig.getString("connectionString")
    val signatureTtl =
      FiniteDuration.apply(azureConfig.getDuration("signatureTtl").getSeconds, duration.SECONDS)
    reposerver.target_store.AzureTargetStoreEngine
      .BlobStorageSettings(connectionString, signatureTtl)
  }

  lazy val outOfBandUploadLimit = _config.getBytes("storage.outOfBandUploadLimit")

  lazy val useS3 = _config.getString("storage.type").equals("s3")

  lazy val useAzure = _config.getString("storage.type").equals("azure")

  lazy val multipartUploadPartSize = _config.getBytes("storage.multipart.partSize")

  lazy val userRepoSizeLimit = _config.getLong("http.server.sizeLimit")

  // not using Config.getDuration() here because that parses different formats than what Pekko uses
  lazy val userRepoUploadRequestTimeout = Duration(
    _config.getString("http.server.uploadRequestTimeout")
  )

  lazy val pekkoRequestTimeout = _config.getString("pekko.http.server.request-timeout")

  lazy val pekkoIdleTimeout = _config.getString("pekko.http.server.idle-timeout")
}

class ReposerverBoot(override val globalConfig: Config,
                     override val dbConfig: Config,
                     override val metricRegistry: MetricRegistry)(
  implicit override val system: ActorSystem)
    extends BootApp
    with Directives
    with Settings
    with VersionInfo
    with DatabaseSupport
    with BootMigrations
    with MetricsSupport
    with DatabaseMetrics
    with PekkoHttpRequestMetrics
    with PrometheusMetricsSupport {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  import system.dispatcher

  def bind(): Future[ServerBinding] = {
    log.info(s"Starting ${nameVersion} on http://$host:$port")

    def keyStoreClient(implicit requestTracing: ServerRequestTracing) = KeyserverHttpClient(
      keyServerUri
    )

    val messageBusPublisher = MessageBus.publisher(system, globalConfig)

    val targetStoreEngine = if (useS3) {
      new S3TargetStoreEngine(s3Credentials)
    } else if (useAzure) {
      new AzureTargetStoreEngine(azureSettings)
    } else {
      LocalTargetStoreEngine(targetStoreRoot)
    }

    def targetStore(implicit requestTracing: ServerRequestTracing) =
      TargetStore(keyStoreClient, targetStoreEngine, messageBusPublisher)

    val keyserverHealthCheck = new ServiceHealthCheck(keyServerUri)

    val remoteDelegationClient = new HttpRemoteDelegationClient()

    implicit val tracing = Tracing.fromConfig(globalConfig, "reposerver")

    val routes: Route =
      (versionHeaders(nameVersion) & requestMetrics(metricRegistry) & logResponseMetrics(
        projectName
      ) & logRequestResult(("reposerver", Logging.DebugLevel))) {
        tracing.traceRequests { implicit requestTracing =>
          new TufReposerverRoutes(
            keyStoreClient,
            NamespaceValidation.withDatabase,
            targetStore,
            messageBusPublisher,
            remoteDelegationClient,
            prometheusMetricsRoutes,
            Seq(keyserverHealthCheck),
            metricRegistry
          ).routes
        }
      }

    Http().newServerAt(host, port).bindFlow(routes)
  }

}

object Boot extends BootAppDefaultConfig with VersionInfo with BootAppDatabaseConfig {
  Security.addProvider(new BouncyCastleProvider)

  def main(args: Array[String]): Unit =
    new ReposerverBoot(globalConfig, dbConfig, MetricsSupport.metricRegistry).bind()

}
