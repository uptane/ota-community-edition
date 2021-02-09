package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.server.Directive1
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import com.advancedtelematic.campaigner.client.{ResolverClient, UserProfileClient}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

class DeviceResource(userProfileClient: UserProfileClient, resolverClient: ResolverClient, extractNamespace: Directive1[Namespace])
                    (implicit val db: Database, val ec: ExecutionContext) {

  val deviceCampaigns = new DeviceCampaigns(userProfileClient, resolverClient)

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
