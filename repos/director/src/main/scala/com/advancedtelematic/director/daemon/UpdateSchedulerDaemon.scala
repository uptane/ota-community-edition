package com.advancedtelematic.director.daemon

import cats.implicits.*
import com.advancedtelematic.director.db.UpdateSchedulerDBIO
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.Messages.{DeviceUpdateAssigned, DeviceUpdateEvent}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, blocking}

class UpdateSchedulerDaemon()(
  implicit db: Database,
  ec: ExecutionContext,
  msgBus: MessageBusPublisher) {

  private val dbio = new UpdateSchedulerDBIO()

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private val CHECK_INTERVAL = 1.second

  private val BACKOFF_INTERVAL = 3.seconds

  def start(): Future[Unit] = {
    log.info(
      s"starting update scheduler daemon. CHECK_INTERVAL=$CHECK_INTERVAL BACKOFF_INTERVAL=$BACKOFF_INTERVAL"
    )
    run()
  }

  def runOnce(): Future[Unit] = {
    log.debug("checking for pending scheduled updates")
    dbio
      .run()
      .flatMap { scheduledUpdates =>
        if (scheduledUpdates.isEmpty) {
          log.debug("no scheduled updates pending, trying later")
          Future {
            blocking(Thread.sleep(CHECK_INTERVAL.toMillis))
          }
        } else {
          log.info(s"$scheduledUpdates scheduled updates processed")

          val msgFuture = scheduledUpdates.toList.traverse_ { su =>
            msgBus.publishSafe(
              DeviceUpdateAssigned(
                su.ns,
                Instant.now(),
                su.correlationId,
                su.deviceId
              ): DeviceUpdateEvent
            )
          }

          msgFuture.map(_ => scheduledUpdates.size)
        }
      }
  }

  private def run(): Future[Unit] =
    runOnce()
      .flatMap { _ =>
        run()
      }
      .recoverWith { case ex =>
        log.error("could not process pending updates", ex)
        Future {
          blocking(Thread.sleep(BACKOFF_INTERVAL.toMillis))
        }.flatMap { _ =>
          run()
        }
      }

}
