package com.advancedtelematic.director.daemon

import cats.implicits.*
import cats.implicits.catsSyntaxOptionId
import com.advancedtelematic.director.data.DataType.{ScheduledUpdate, ScheduledUpdateId, StatusInfo}
import com.advancedtelematic.director.db.{ScheduledUpdatesRepositorySupport, UpdateSchedulerDBIO}
import com.advancedtelematic.director.deviceregistry.daemon.DeviceUpdateStatus
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus
import com.advancedtelematic.libats.data.DataType.{MultiTargetUpdateId, Namespace}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  DeviceUpdateAssigned,
  DeviceUpdateEvent
}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{blocking, ExecutionContext, Future}

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
            0
          }
        } else {
          log.info(s"$scheduledUpdates scheduled updates processed")

          val msgFuture = scheduledUpdates.toList.traverse_ { su =>
            val correlationId = MultiTargetUpdateId(su.updateId.uuid)
            msgBus.publishSafe(
              DeviceUpdateAssigned(
                su.ns,
                Instant.now(),
                correlationId,
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

class UpdateScheduler()(implicit db: Database, ec: ExecutionContext, msgBus: MessageBusPublisher)
    extends ScheduledUpdatesRepositorySupport {

  private val dbio = new UpdateSchedulerDBIO()

  def create(ns: Namespace,
             deviceId: DeviceId,
             updateId: UpdateId,
             scheduleAt: Instant): Future[ScheduledUpdateId] = {
    val scheduledUpdateId = ScheduledUpdateId.generate()

    for {
      id <- dbio.validateAndPersist(
        ScheduledUpdate(
          ns,
          scheduledUpdateId,
          deviceId,
          updateId,
          scheduleAt,
          ScheduledUpdate.Status.Scheduled
        )
      )
      _ <- msgBus.publishSafe(
        DeviceUpdateStatus(ns, deviceId, DeviceStatus.UpdateScheduled, Instant.now())
      )
    } yield id
  }

  def cancel(ns: Namespace, scheduledUpdateId: ScheduledUpdateId): Future[Unit] =
    scheduledUpdatesRepository.setStatus(
      ns,
      scheduledUpdateId,
      ScheduledUpdate.Status.Cancelled,
      CancelledByUser.some
    )

  case object CancelledByUser extends StatusInfo

  implicit val cancelledByUserEncoder: Encoder[CancelledByUser.type] = Encoder.instance { _ =>
    Json.obj("cancelled_by_user" -> "cancelled by user".asJson)
  }

}
