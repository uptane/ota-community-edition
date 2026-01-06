package com.advancedtelematic.director.http.deviceregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directive1, Directives, Route}
import akka.stream.Materializer
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.DefaultRejectionHandler.rejectionHandler
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext

/** Base API routing class.
  */
class DeviceRegistryRoutes(namespaceExtractor: Directive1[Namespace],
                           deviceNamespaceAuthorizer: Directive1[DeviceId],
                           messageBus: MessageBusPublisher)(
  implicit db: Database,
  system: ActorSystem,
  mat: Materializer,
  exec: ExecutionContext)
    extends Directives {

  val route: Route =
    pathPrefix("api") {
      pathPrefix("v1") {
        handleRejections(rejectionHandler) {
          ErrorHandler.handleErrors {
            new DevicesResource(namespaceExtractor, messageBus, deviceNamespaceAuthorizer).route ~
              new DeviceMonitoringResource(
                namespaceExtractor,
                deviceNamespaceAuthorizer,
                messageBus
              ).route ~
              new SystemInfoResource(
                messageBus,
                namespaceExtractor,
                deviceNamespaceAuthorizer
              ).route ~
              new PublicCredentialsResource(
                namespaceExtractor,
                messageBus,
                deviceNamespaceAuthorizer
              ).route ~
              new PackageListsResource(namespaceExtractor).route ~
              new GroupsResource(namespaceExtractor, deviceNamespaceAuthorizer).route
          }
        }
      }
    }

}
