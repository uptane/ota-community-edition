package com.advancedtelematic.director.http

import akka.http.scaladsl.server.{Directives, _}
import com.advancedtelematic.libats.auth.NamespaceDirectives
import com.advancedtelematic.libats.http.DefaultRejectionHandler.rejectionHandler
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext


class DirectorRoutes(keyserverClient: KeyserverClient, allowEcuReplacement: Boolean)
                    (implicit val db: Database, ec: ExecutionContext, messageBusPublisher: MessageBusPublisher) {
  import Directives._

  val extractNamespace = NamespaceDirectives.defaultNamespaceExtractor.map(_.namespace)

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new AdminResource(extractNamespace, keyserverClient).route ~
          new AssignmentsResource(extractNamespace).route ~
          new DeviceResource(extractNamespace, keyserverClient, allowEcuReplacement).route ~
          new MultiTargetUpdatesResource(extractNamespace).route ~
          new LegacyRoutes(extractNamespace).route
        }
      }
    }
}
