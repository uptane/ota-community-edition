/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.daemon

import akka.Done
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceSeen
import com.advancedtelematic.ota.deviceregistry.data.DeviceStatus
import com.advancedtelematic.ota.deviceregistry.db.DeviceRepository
import com.advancedtelematic.ota.deviceregistry.common.Errors
import com.advancedtelematic.ota.deviceregistry.messages.DeviceActivated
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api._

class DeviceSeenListener(messageBus: MessageBusPublisher)
                        (implicit db: Database, ec: ExecutionContext) extends MsgOperation[DeviceSeen] {

  val _logger = LoggerFactory.getLogger(this.getClass)

  override def apply(msg: DeviceSeen): Future[Done] =
    db.run(DeviceRepository.updateLastSeen(msg.uuid, msg.lastSeen))
      .flatMap {
        case (activated, ns) =>
          if (activated) {
            messageBus
              .publishSafe(DeviceActivated(ns, msg.uuid, msg.lastSeen))
              .flatMap { _ =>
                db.run(DeviceRepository.setDeviceStatus(msg.uuid, DeviceStatus.UpToDate))
              }
          } else {
            Future.successful(Done)
          }
      }
      .recover {
        case Errors.MissingDevice =>
          _logger.warn(s"Ignoring event for missing or deleted device: $msg")
        case ex =>
          _logger.warn(s"Could not process $msg", ex)
      }
      .map { _ =>
        Done
      }
}
