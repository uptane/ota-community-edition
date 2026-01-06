package com.advancedtelematic.director.http

import org.apache.pekko.http.scaladsl.server.{Directives, _}
import com.advancedtelematic.libats.http.{ErrorHandler, NamespaceDirectives}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class DirectorRoutes(keyserverClient: KeyserverClient, allowEcuReplacement: Boolean)(
  implicit val db: Database,
  ec: ExecutionContext,
  messageBusPublisher: MessageBusPublisher) {
  import Directives._

  val extractNamespace = NamespaceDirectives.defaultNamespaceExtractor

  val routes: Route =
// Temporarily disabled to allow routing to go to dev registry before rejections are handled
//    handleRejections(rejectionHandler) {
    ErrorHandler.handleErrors {
      pathPrefix("api" / "v1") {
        new AdminResource(extractNamespace, keyserverClient).route ~
          new AssignmentsResource(extractNamespace).route ~
          new DeviceResource(extractNamespace, keyserverClient, allowEcuReplacement).route ~
          new TargetUpdateSpecsResource(extractNamespace).route ~
          new LegacyRoutes(extractNamespace).route ~
          new UpdateResource(extractNamespace).route
      } ~
        new DirectorDebugResource().route
    }
//    }

}
