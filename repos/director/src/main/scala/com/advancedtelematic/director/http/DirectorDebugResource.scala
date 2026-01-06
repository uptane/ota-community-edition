package com.advancedtelematic.director.http

import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.director.data.DeviceRequest.DeviceManifest
import com.advancedtelematic.director.db.{CompiledManifestExecutor, DirectorDbDebug}
import com.advancedtelematic.director.manifest.ManifestCompiler
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.debug.DebugRoutes
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.director.data.Codecs.*

import scala.concurrent.ExecutionContext

class DirectorDebugResource()(implicit val db: Database, val ec: ExecutionContext) {

  import com.advancedtelematic.libats.debug.DebugDatatype.*
  import com.advancedtelematic.libats.http.UUIDKeyAkka.*

  val debug = new DirectorDbDebug()

  import Directives.*
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*

  val route: Route = DebugRoutes.routes {
    Directives.concat(
      DebugRoutes.buildNavigation(debug.namespace_resources, debug.device_resources),
      DebugRoutes.buildGroupRoutes(NamespacePath, debug.namespace_resources),
      DebugRoutes.buildGroupRoutes(DeviceId.Path, debug.device_resources),
      path("device-state" / DeviceId.Path) { deviceId =>
        val f = db.run(new CompiledManifestExecutor().findStateAction(deviceId))
        complete(f)
      },
      (put & path("run-manifest" / DeviceId.Path) & entity(as[DeviceManifest])) {
        (deviceId, manifest) =>
          onSuccess(db.run(new CompiledManifestExecutor().findStateAction(deviceId))) {
            currentState =>
              complete(
                ManifestCompiler(Namespace("notused"), manifest)
                  .apply(currentState)
                  .map(_.knownState)
              )
          }
      }
    )
  }

}
