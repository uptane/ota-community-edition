package com.advancedtelematic.ota.deviceregistry

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages
import com.advancedtelematic.libats.messaging_datatype.Messages.SystemInfo
import io.circe.Json

import scala.concurrent.{ExecutionContext, Future}

class SystemInfoUpdatePublisher(messageBus: MessageBusPublisher)(implicit ec: ExecutionContext) {
  // Best effort parsing of a json uploading by a client, if not successful parsing anything, just return empty
  def parse(json: Json): SystemInfo = {
    val product =
      json.hcursor.downField("product").as[String].toOption
    SystemInfo(product)
  }

  def publishSafe(ns: Namespace,
                  deviceId: DeviceId,
                  systemInfo: Option[Json],
                 ): Future[Unit] = {
    val info = systemInfo.map(parse)
    val msg = Messages.DeviceSystemInfoChanged(ns, deviceId, info)
    messageBus.publishSafe(msg).map(_ => ())
  }
}
