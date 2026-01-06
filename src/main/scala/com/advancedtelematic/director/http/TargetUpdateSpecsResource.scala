package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.*
import com.advancedtelematic.director.data.AdminDataType.TargetUpdateSpec
import slick.jdbc.MySQLProfile.api.Database
import com.advancedtelematic.libats.http.UUIDKeyPekko.*
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DataType.TargetSpecId
import com.advancedtelematic.director.db.TargetUpdateSpecs
import com.advancedtelematic.director.http.Errors.InvalidMtu
import com.advancedtelematic.libats.data.DataType.Namespace

import scala.concurrent.ExecutionContext
import com.advancedtelematic.libats.codecs.CirceCodecs.*

class TargetUpdateSpecsResource(extractNamespace: Directive1[Namespace])(
  implicit val db: Database,
  val ec: ExecutionContext) {
  import Directives.*

  private val targetUpdateSpecs = new TargetUpdateSpecs()

  val route = extractNamespace { ns =>
    pathPrefix("multi_target_updates") {
      (get & pathPrefix(TargetSpecId.Path)) { uid =>
        // For some reason director-v1 accepts `{targets: ...}` but returns `{...}`
        // To make app compatible with director-v2, for now we do the same, but we should be returning what we accept:
        complete(targetUpdateSpecs.find(ns, uid).map(_.targets))
      } ~
        (post & pathEnd) {
          entity(as[TargetUpdateSpec]) { mtuRequest =>
            if (mtuRequest.targets.isEmpty) {
              throw InvalidMtu("targets cannot be empty")
            }

            val f = targetUpdateSpecs.create(ns, mtuRequest).map {
              StatusCodes.Created -> _
            }
            complete(f)
          }
        }
    }
  }

}
