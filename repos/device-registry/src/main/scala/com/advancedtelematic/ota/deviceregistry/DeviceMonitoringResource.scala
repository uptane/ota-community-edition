package com.advancedtelematic.ota.deviceregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Route}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceMetricsObservation
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, Json}
import org.slf4j.LoggerFactory

import java.time.Instant

protected case class DeviceObservationRequest(observedAt: Instant, payload: Json)

protected object DeviceObservationRequest {
  implicit val deviceObservationRequestDecoder = Decoder.instance { cursor =>
    for {
      observedAt <- cursor.get[Double]("date").map { epoch => Instant.ofEpochMilli((epoch * 1000).longValue()) } // Losing some precision here
      payload <- cursor.as[Json]
    } yield DeviceObservationRequest(observedAt, payload)
  }
}

class DeviceMonitoringResource(namespaceExtractor: Directive1[Namespace],
                               deviceNamespaceAuthorizer: Directive1[DataType.DeviceId],
                               messageBus: MessageBusPublisher
                              )(implicit system: ActorSystem) {
  import akka.http.scaladsl.server.Directives._
  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val route: Route =
    (pathPrefix("devices") & namespaceExtractor) { ns =>
      deviceNamespaceAuthorizer { uuid =>
        path("monitoring") {
          (post & entity(as[DeviceObservationRequest])) { req =>
            log.debug("device observation from client: {}", req.payload.noSpaces)

            val msg = DeviceMetricsObservation(ns, uuid, req.payload, Instant.now())
            val f = messageBus.publish(msg).map(_ => StatusCodes.NoContent)

            complete(f)
          }
        }
      }
    }
}
