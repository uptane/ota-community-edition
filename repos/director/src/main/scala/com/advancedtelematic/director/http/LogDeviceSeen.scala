package com.advancedtelematic.director.http

import com.advancedtelematic.director.db.deviceregistry.DeviceRepository
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus
import com.advancedtelematic.director.deviceregistry.messages.DeviceActivated
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceSeen
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import scala.async.Async.*
import scala.concurrent.*

object LogDeviceSeen {

  def logDevice(ns: Namespace, device: DeviceId, now: Instant = Instant.now)(
    implicit ec: ExecutionContext,
    db: Database,
    msgBus: MessageBusPublisher): Future[Unit] = async {
    await(msgBus.publishSafe(DeviceSeen(ns, device)))

    val activated = await(db.run(DeviceRepository.updateLastSeen(device, now)))

    if (activated) {
      await(db.run(DeviceRepository.setDeviceStatus(device, DeviceStatus.UpToDate)))
      await(msgBus.publishSafe(DeviceActivated(ns, device, now)))
    }
  }

}
