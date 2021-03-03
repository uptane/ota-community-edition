/*
 * Copyright (c) 2018 HERE Technologies
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.daemon

import akka.Done
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import com.advancedtelematic.ota.deviceregistry.db.DeviceRepository
import com.advancedtelematic.ota.deviceregistry.common.Errors
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class DeleteDeviceListener()(implicit val db: Database, ec: ExecutionContext) extends MsgOperation[DeleteDeviceRequest] {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def apply(message: DeleteDeviceRequest): Future[Done] =
    db.run(DeviceRepository.delete(message.namespace, message.uuid).map(_ => Done))
      .recover {
        case Errors.MissingDevice =>
          log.info(s"Can't remove non-existent device: ${message.uuid}")
          Done
      }
}
