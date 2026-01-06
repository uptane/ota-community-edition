package com.advancedtelematic.director.db

import cats.implicits.*
import com.advancedtelematic.director.data.DataType.Update
import com.advancedtelematic.director.data.DbDataType.Assignment
import com.advancedtelematic.director.http.Errors
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.TransactionIsolation
import com.advancedtelematic.libats.codecs.CirceRefined.*
import com.advancedtelematic.libats.data.ErrorRepresentation.*
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UpdateSchedulerDBIO()(implicit val db: Database, val ec: ExecutionContext)
    extends UpdatesRepositorySupport
    with AssignmentsRepositorySupport
    with ProvisionedDeviceRepositorySupport {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  // Go to db, lock and fetch updates to assign
  private def findScheduledUpdates(): DBIO[Seq[Update]] = {
    // TODO: To run more than one instance of the scheduler we need to use `FOR UPDATE SKIP LOCKED` but this requires mariadb 10.6

    import SlickMapping.*

    Schema.updates
      .filter(_.status === (Update.Status.Scheduled: Update.Status))
      .filter(_.scheduledFor < Instant.now())
      .sortBy(_.scheduledFor.desc)
      .take(10)
      .forUpdate
      .result
  }

  private def startUpdate(update: Update): DBIO[Unit] =
    new AffectedEcusDBIO()
      .findAffectedEcusAction(
        update.ns,
        Seq(update.deviceId),
        update.targetSpecId,
        ignoreScheduledUpdates = true
      )
      .flatMap { assignmentResult =>
        val notAffected = assignmentResult.notAffected.get(update.deviceId)

        notAffected match {
          case Some(errors) =>
            log
              .atInfo()
              .addKeyValue("update-id", update.id.uuid.toString)
              .addKeyValue("device-id", update.deviceId.uuid.toString)
              .addKeyValue("errors", errors.view.mapValues(_.toErrorRepr).toMap.asJson)
              .log(s"cancelling scheduled update for ${update.deviceId}")

            updatesRepository.setStatusAction(
              update.ns,
              update.id,
              Update.Status.Cancelled,
              Errors.DeviceCannotBeUpdated(update.deviceId, errors).toErrorRepr.some
            )

          case None =>
            val ecuTargetIds = assignmentResult.affected

            val assignments = ecuTargetIds.map { case (ecu, ecuSerial) =>
              Assignment(
                update.ns,
                update.deviceId,
                ecu.ecuSerial,
                ecuSerial,
                update.correlationId,
                inFlight = false,
                createdAt = Instant.now()
              )
            }

            log
              .atDebug()
              .addKeyValue("update-id", update.id.uuid.toString)
              .addKeyValue("device-id", update.deviceId.uuid.toString)
              .log(s"creating ${assignments.length} for ecu ${update.deviceId}")

            DBIO.seq(
              assignmentsRepository
                .persistManyDBIO(provisionedDeviceRepository)(assignments.toList),
              updatesRepository
                .setStatusAction[Json](update.ns, update.id, Update.Status.Assigned, None)
            )
        }
      }

  def run(): Future[Seq[Update]] = {
    val scheduled = findScheduledUpdates()

    val io = scheduled.flatMap { seq =>
      DBIO.sequence(seq.map(startUpdate)).map(_ => seq)
    }

    db.run(io.transactionally.withTransactionIsolation(TransactionIsolation.Serializable))
  }

}
