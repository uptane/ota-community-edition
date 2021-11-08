package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.server.{Directive1, Directives, Route}
import com.advancedtelematic.campaigner.VersionInfo
import com.advancedtelematic.campaigner.client.{DeviceRegistryClient, ResolverClient, UserProfileClient}
import com.advancedtelematic.campaigner.db.Campaigns
import com.advancedtelematic.libats.auth.NamespaceDirectives
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.DefaultRejectionHandler.rejectionHandler
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.slick.monitoring.DbHealthResource
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class Routes(deviceRegistry: DeviceRegistryClient,
             resolver: ResolverClient,
             userProfile: UserProfileClient,
             campaigns: Campaigns)(implicit val db: Database, ec: ExecutionContext)
    extends VersionInfo {

  import Directives._

  val extractAuth = NamespaceDirectives.fromConfig()

  lazy val defaultNamespaceExtractor: Directive1[Namespace] =
    NamespaceDirectives.defaultNamespaceExtractor.map(_.namespace)

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v2") {
          new CampaignResource(extractAuth, deviceRegistry, campaigns).route ~
          new DeviceResource(userProfile, resolver, campaigns.repositories, defaultNamespaceExtractor).route ~
          new UpdateResource(defaultNamespaceExtractor, deviceRegistry, resolver, userProfile, campaigns.repositories.updateRepo).route
        } ~ DbHealthResource(versionMap).route
      }
    }

}
