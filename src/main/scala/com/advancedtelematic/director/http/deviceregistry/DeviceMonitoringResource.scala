package com.advancedtelematic.director.http.deviceregistry

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Route}
import com.advancedtelematic.director.deviceregistry.data.Codecs.ObservationPublishResultCodec
import com.advancedtelematic.director.deviceregistry.data.DataType.ObservationPublishResult
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  deviceMetricsObservationMessageLike,
  DeviceMetricsObservation
}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.{Decoder, Json}
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.Future
import scala.util.{Failure, Success}

protected case class DeviceObservationRequest(observedAt: Instant, payload: Json)

protected object DeviceObservationRequest {

  implicit val deviceObservationRequestDecoder: io.circe.Decoder[DeviceObservationRequest] =
    Decoder.instance { cursor =>
      for {
        observedAt <- cursor.get[Double]("date").map { epoch =>
          Instant.ofEpochMilli((epoch * 1000).longValue())
        } // Losing some precision here
        payload <- cursor.as[Json]
      } yield DeviceObservationRequest(observedAt, payload)
    }

}

class DeviceMonitoringResource(namespaceExtractor: Directive1[Namespace],
                               deviceNamespaceAuthorizer: Directive1[DataType.DeviceId],
                               messageBus: MessageBusPublisher)(implicit system: ActorSystem) {

  import akka.http.scaladsl.server.Directives.*
  import system.dispatcher

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val route: Route =
    (pathPrefix("devices") & namespaceExtractor) { ns =>
      deviceNamespaceAuthorizer { uuid =>
        pathPrefix("monitoring") {
          (post & entity(as[DeviceObservationRequest])) { req =>
            log.debug("device observation from client: {}", req.payload.noSpaces)

            val msg = DeviceMetricsObservation(ns, uuid, req.payload, Instant.now())
            val f = messageBus.publish(msg).map(_ => StatusCodes.NoContent)

            complete(f)
          } ~
            (path("fluentbit-metrics") & post & entity(as[List[DeviceObservationRequest]])) { req =>
              val f = req.map { r =>
                log.debug("device observation from client: {}", r.payload.noSpaces)
                val msg = DeviceMetricsObservation(ns, uuid, r.payload, Instant.now())
                messageBus.publish(msg).transformWith {
                  case Success(_) =>
                    Future.successful(ObservationPublishResult(publishedSuccessfully = true, msg))
                  case Failure(_) =>
                    Future.successful(ObservationPublishResult(publishedSuccessfully = false, msg))
                }
              }
              complete(Future.sequence(f).map { results =>
                if (results.exists(_.publishedSuccessfully == false)) {
                  StatusCodes.RangeNotSatisfiable -> results
                } else {
                  StatusCodes.NoContent -> List.empty[ObservationPublishResult]
                }
              })
            }
        }
      }
    }

}
