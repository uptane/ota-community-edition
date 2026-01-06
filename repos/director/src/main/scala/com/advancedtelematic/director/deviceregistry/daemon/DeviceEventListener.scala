/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.daemon

import org.apache.pekko.Done
import com.advancedtelematic.director.db.deviceregistry.EventJournal
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceEventMessage
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import java.sql.SQLIntegrityConstraintViolationException
import scala.concurrent.{ExecutionContext, Future}

class DeviceEventListener()(implicit val db: Database, ec: ExecutionContext)
    extends MsgOperation[DeviceEventMessage] {

  private[this] val journal = new EventJournal()(db, ec)
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def apply(message: DeviceEventMessage): Future[Done] =
    journal.recordEvent(message.event).map(_ => Done).recover {
      case e: SQLIntegrityConstraintViolationException =>
        log.error(s"Can't record event $message", e)
        Done
    }

}
