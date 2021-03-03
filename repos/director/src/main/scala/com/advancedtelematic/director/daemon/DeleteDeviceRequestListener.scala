package com.advancedtelematic.director.daemon

import com.advancedtelematic.director.db.DeviceRepositorySupport
import com.advancedtelematic.libats.http.Errors.MissingEntity
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class DeleteDeviceRequestListener()(implicit val db: Database, val ec: ExecutionContext)
                                            extends MsgOperation[DeleteDeviceRequest]  with DeviceRepositorySupport {

  val log = LoggerFactory.getLogger(this.getClass)

  override def apply(message: DeleteDeviceRequest): Future[Unit] = {
    deviceRepository.markDeleted(message.namespace, message.uuid).recover {
      case e: MissingEntity[_] =>
        log.warn(s"error deleting device ${message.uuid} from namespace ${message.namespace}: ${e.msg}")
    }
  }
}
