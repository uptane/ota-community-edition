package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import com.advancedtelematic.campaigner.client.{ResolverClient, UserProfileClient}
import com.advancedtelematic.campaigner.db.Repositories
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.ExecutionContext

class DeviceResource(userProfileClient: UserProfileClient,
                     resolverClient: ResolverClient,
                     repositories: Repositories,
                     extractNamespace: Directive1[Namespace])(implicit val ec: ExecutionContext) {

  val deviceCampaigns = new DeviceCampaigns(userProfileClient, resolverClient, repositories)

  val route =
    extractNamespace { ns =>
      pathPrefix("device" / DeviceId.Path) { deviceId =>
        path("campaigns") {
          get {
            complete(deviceCampaigns.findScheduledCampaigns(ns, deviceId))
          }
        }
      }
    }
}
