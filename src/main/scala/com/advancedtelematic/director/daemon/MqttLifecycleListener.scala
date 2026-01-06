package com.advancedtelematic.director.daemon

import com.advancedtelematic.director.db.deviceregistry.DeviceRepository
import com.advancedtelematic.director.deviceregistry.data.DataType.MqttStatus
import com.advancedtelematic.director.http.deviceregistry.Errors
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import enumeratum.EnumEntry
import slick.jdbc.MySQLProfile.api.*
import io.circe.syntax.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import enumeratum.*
import enumeratum.EnumEntry.Lowercase
import io.circe.{Codec, Decoder, Encoder, Json}
import org.slf4j.LoggerFactory
import com.advancedtelematic.libats.messaging_datatype.MessageLike

sealed trait EventType extends EnumEntry with Lowercase

object EventType extends Enum[EventType] {

  val values = findValues

  case object Connected extends EventType
  case object Disconnected extends EventType
}

final case class DeviceMqttLifecycle(deviceId: DeviceId,
                                     eventType: EventType,
                                     payload: Json,
                                     timestamp: Instant)

object DeviceMqttLifecycle {

  import DeviceId.*

  implicit val eventTypeDecoder: Decoder[EventType] = Circe.decoder(EventType)
  implicit val eventTypeEncoder: Encoder[EventType] = Circe.encoder(EventType)

  implicit val deviceMqttLifecycleCodec: Codec[DeviceMqttLifecycle] =
    io.circe.generic.semiauto.deriveCodec

  implicit val messageLike: MessageLike[DeviceMqttLifecycle] =
    MessageLike.derive[DeviceMqttLifecycle](_.deviceId.uuid.toString)

}

class MqttLifecycleListener()(implicit val db: Database, val ec: ExecutionContext)
    extends MsgOperation[DeviceMqttLifecycle] {

  import DeviceRepository.setMqttStatus
  import DeviceMqttLifecycle.*

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def apply(msg: DeviceMqttLifecycle): Future[?] = {
    log
      .atInfo()
      .addKeyValue("deviceId", msg.deviceId.asJson)
      .addKeyValue("type", msg.eventType.asJson)
      .log("received deviceMqtt lifcycle msg")

    val f = msg.eventType match {
      case EventType.Connected =>
        db.run(setMqttStatus(msg.deviceId, MqttStatus.Online, msg.timestamp))
      case EventType.Disconnected =>
        db.run(setMqttStatus(msg.deviceId, MqttStatus.Offline, msg.timestamp))
    }

    f.recover { case Errors.MissingDevice =>
      log
        .atDebug()
        .addKeyValue("deviceId", msg.deviceId.uuid.toString)
        .log("mqtt status not updated, device not found")
    }
  }

}
