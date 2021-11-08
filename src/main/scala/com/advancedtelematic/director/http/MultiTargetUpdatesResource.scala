package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.advancedtelematic.director.data.AdminDataType.MultiTargetUpdate
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateId
import slick.jdbc.MySQLProfile.api.Database
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.db.MultiTargetUpdates
import com.advancedtelematic.libats.data.DataType.Namespace
import scala.concurrent.ExecutionContext
import com.advancedtelematic.libats.codecs.CirceCodecs._

class MultiTargetUpdatesResource(extractNamespace: Directive1[Namespace])(implicit val db: Database, val ec: ExecutionContext) {
  import Directives._

  val multiTargetUpdates = new MultiTargetUpdates()

  val route = extractNamespace { ns =>
    pathPrefix("multi_target_updates") {
      (get & pathPrefix(UpdateId.Path)) { uid =>
        // For some reason director-v1 accepts `{targets: ...}` but returns `{...}`
        // To make app compatible with director-v2, for now we do the same, but we should be returning what we accept:
        // complete(multiTargetUpdates.find(ns, uid))
        complete(multiTargetUpdates.find(ns, uid).map(_.targets))
      } ~
      (post & pathEnd) {
        entity(as[MultiTargetUpdate]) { mtuRequest =>
          val f = multiTargetUpdates.create(ns, mtuRequest).map {
            StatusCodes.Created -> _
          }

          complete(f)
        }
      }
    }
  }
}
