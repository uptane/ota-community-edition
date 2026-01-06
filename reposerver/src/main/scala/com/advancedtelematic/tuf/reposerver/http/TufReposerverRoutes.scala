package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.server.{Directives, _}
import org.apache.pekko.stream.Materializer
import com.advancedtelematic.libats.http.DefaultRejectionHandler._
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.metrics.{HealthCheck, MetricsSupport}
import com.advancedtelematic.tuf.reposerver.VersionInfo
import com.advancedtelematic.tuf.reposerver.delegations.RemoteDelegationClient
import com.advancedtelematic.tuf.reposerver.target_store.TargetStore
import com.codahale.metrics.MetricRegistry
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class TufReposerverRoutes(keyserverClient: KeyserverClient,
                          namespaceValidation: NamespaceValidation,
                          targetStore: TargetStore,
                          messageBusPublisher: MessageBusPublisher,
                          remoteDelegationClient: RemoteDelegationClient,
                          metricsRoutes: Route = Directives.reject,
                          dependencyChecks: Seq[HealthCheck] = Seq.empty,
                          metricRegistry: MetricRegistry = MetricsSupport.metricRegistry)(
  implicit val db: Database,
  val ec: ExecutionContext,
  mat: Materializer)
    extends VersionInfo {

  import Directives._

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        concat(
          pathPrefix("api" / "v1") {
            new RepoResource(
              keyserverClient,
              namespaceValidation,
              targetStore,
              new TufTargetsPublisher(messageBusPublisher),
              remoteDelegationClient
            ).route
          },
          pathPrefix("api" / "v2")(new RepoTargetsResource(namespaceValidation).route),
          DbHealthResource(
            versionMap,
            dependencies = dependencyChecks,
            metricRegistry = metricRegistry
          ).route,
          metricsRoutes
        )
      }
    }

}
