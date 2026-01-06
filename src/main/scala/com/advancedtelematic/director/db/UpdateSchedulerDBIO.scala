package com.advancedtelematic.director.db

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.*
import com.advancedtelematic.director.data.DataType.{ScheduledUpdate, ScheduledUpdateId, StatusInfo}
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus
import com.advancedtelematic.director.db.deviceregistry.DeviceRepository
import com.advancedtelematic.director.db.Schema
import com.advancedtelematic.director.data.DbDataType.{Assignment, EcuTargetId}
import com.advancedtelematic.director.http.Errors.UpdateScheduleError
import com.advancedtelematic.libats.data.DataType.MultiTargetUpdateId
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import eu.timepit.refined.api.RefType
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Encoder, Json}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.{GetResult, SetParameter, TransactionIsolation}

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UpdateSchedulerDBIO()(implicit val db: Database, val ec: ExecutionContext)
    extends ScheduledUpdatesRepositorySupport
    with AssignmentsRepositorySupport
    with ProvisionedDeviceRepositorySupport {
  import UpdateSchedulerDBIO.*

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private implicit val setUpdateId: SetParameter[UpdateId] =
    SetParameter.SetString.contramap(_.uuid.toString)

  private implicit val setDeviceId: SetParameter[DeviceId] =
    SetParameter.SetString.contramap(_.uuid.toString)

  // Go to db, lock and fetch updates to assign
  private def findScheduledUpdates(): DBIO[Seq[ScheduledUpdate]] = {
    // TODO: To run more than one instance of the scheduler we need to use `FOR UPDATE SKIP LOCKED` but this requires mariadb 10.6

    import SlickMapping.*

    Schema.scheduledUpdates
      .filter(_.status === (ScheduledUpdate.Status.Scheduled: ScheduledUpdate.Status))
      .filter(_.scheduledAt < Instant.now())
      .sortBy(_.scheduledAt.desc)
      .take(10)
      .forUpdate
      .result
  }

  case class EcuInfoRow(ecuTargetId: EcuTargetId,
                        ecuIdentifier: Option[EcuIdentifier],
                        updateHardwareId: HardwareIdentifier,
                        ecuHardwareId: Option[HardwareIdentifier],
                        fromTargetId: Option[EcuTargetId],
                        currentTargetId: Option[EcuTargetId],
                        assignmentExists: Boolean)

  private def findEcuSerialsForUpdate(deviceId: DeviceId, updateId: UpdateId)
    : DBIO[ValidatedNel[InvalidReason, NonEmptyList[(EcuTargetId, EcuIdentifier)]]] = {

    implicit val getResult: GetResult[EcuInfoRow] = GetResult { pr =>
      EcuInfoRow(
        EcuTargetId(UUID.fromString(pr.nextString())),
        pr.nextStringOption().map(str => EcuIdentifier.from(str).valueOr(throw _)),
        RefType
          .applyRef[HardwareIdentifier](pr.nextString())
          .valueOr(err => throw new IllegalArgumentException(err)),
        pr.nextStringOption().flatMap(str => RefType.applyRef[HardwareIdentifier](str).toOption),
        pr.nextStringOption().map(str => EcuTargetId(UUID.fromString(str))),
        pr.nextStringOption().map(str => EcuTargetId(UUID.fromString(str))),
        pr.nextStringOption().isDefined
      )
    }

    val ecusio =
      sql"""
            SELECT ecu_targets.id, ecus.ecu_serial, hu.hardware_identifier, ecus.hardware_identifier, hu.from_target_id, ecus.current_target, a.ecu_serial
            FROM #${Schema.hardwareUpdates.baseTableRow.tableName} hu
              JOIN #${Schema.ecuTargets.baseTableRow.tableName} ecu_targets ON hu.to_target_id = ecu_targets.id
              LEFT JOIN #${Schema.allEcus.baseTableRow.tableName} ecus ON ecus.hardware_identifier = hu.hardware_identifier AND ecus.deleted = false AND ecus.device_id = $deviceId
              LEFT JOIN #${Schema.assignments.baseTableRow.tableName} a ON a.ecu_serial = ecus.ecu_serial
              WHERE
                hu.id = $updateId
        """.as[EcuInfoRow]

    ecusio.map { ecus =>
      if (ecus.isEmpty)
        HardwareUpdateMissing(updateId).invalidNel
      else
        NonEmptyList
          .fromListUnsafe(ecus.toList)
          .map {
            case EcuInfoRow(
                  ecuTargetId,
                  Some(ecuSerial),
                  _,
                  _,
                  fromTargetId,
                  currentTargetId,
                  false
                ) if fromTargetId == currentTargetId || fromTargetId.isEmpty =>
              (ecuTargetId, ecuSerial).validNel[InvalidReason]
            case EcuInfoRow(
                  _,
                  Some(_),
                  _,
                  _,
                  fromTargetId,
                  currentTargetId,
                  false
                ) => // incompatible fromTarget in compatible ecu
              IncompatibleFromTarget(updateId, currentTargetId, fromTargetId).invalidNel
            case EcuInfoRow(
                  _,
                  None,
                  updateHardwareId,
                  ecuHardwareId,
                  _,
                  _,
                  _
                ) => // no compatible ecu found
              IncompatibleEcuHardware(updateId, updateHardwareId, ecuHardwareId).invalidNel
            case EcuInfoRow(_, _, _, _, _, _, true) => // an assignment already exists for ecu
              EcuAssignmentExists(updateId).invalidNel
          }
          .sequence
    }
  }

  private def startUpdate(update: ScheduledUpdate): DBIO[Unit] =
    findEcuSerialsForUpdate(update.deviceId, update.updateId).flatMap {
      case Validated.Valid(ecuTargetIds) =>
        val correlationId = MultiTargetUpdateId(update.updateId.uuid)

        val assignments = ecuTargetIds.map { case (ecuTargetId, ecuSerial) =>
          Assignment(
            update.ns,
            update.deviceId,
            ecuSerial,
            ecuTargetId,
            correlationId,
            inFlight = false,
            createdAt = Instant.now()
          )
        }

        log
          .atDebug()
          .addKeyValue("scheduled-update-id", update.id.uuid.toString)
          .addKeyValue("device-id", update.deviceId.uuid.toString)
          .log(s"creating ${assignments.length} for ecu ${update.deviceId}")

        DBIO.seq(
          assignmentsRepository.persistManyDBIO(provisionedDeviceRepository)(assignments.toList),
          scheduledUpdatesRepository.setStatusAction(
            update.ns,
            update.id,
            ScheduledUpdate.Status.Assigned
          )
        )
      case Validated.Invalid(errors) =>
        log
          .atInfo()
          .addKeyValue("scheduled-update-id", update.id.uuid.toString)
          .addKeyValue("device-id", update.deviceId.uuid.toString)
          .addKeyValue("errors", errors.asJson)
          .log(s"cancelling scheduled update for ${update.deviceId}")

        scheduledUpdatesRepository.setStatusAction(
          update.ns,
          update.id,
          ScheduledUpdate.Status.Cancelled,
          InvalidEcuStatus(errors).some
        )
    }

  def validateAndPersist(scheduledUpdate: ScheduledUpdate): Future[ScheduledUpdateId] = db.run {
    scheduledUpdatesRepository
      .scheduledUpdateExistsFor(scheduledUpdate.ns, scheduledUpdate.deviceId)
      .flatMap {
        case Some(id) =>
          DBIO.failed(
            UpdateScheduleError(
              scheduledUpdate.deviceId,
              NonEmptyList.of(ScheduledUpdateExists(id): InvalidReason)
            )
          )
        case None =>
          findEcuSerialsForUpdate(scheduledUpdate.deviceId, scheduledUpdate.updateId).flatMap {
            case Valid(_) =>
              for {
                id <- scheduledUpdatesRepository.persistAction(scheduledUpdate)
                _ <- DeviceRepository.setDeviceStatusAction(
                  scheduledUpdate.deviceId,
                  DeviceStatus.UpdateScheduled
                )
              } yield id
            case Invalid(errors) =>
              DBIO.failed(UpdateScheduleError(scheduledUpdate.deviceId, errors))
          }
      }
      .withTransactionIsolation(TransactionIsolation.Serializable)
      .transactionally
  }

  def run(): Future[Seq[ScheduledUpdate]] = {
    val scheduled = findScheduledUpdates()

    val io = scheduled.flatMap { seq =>
      DBIO.sequence(seq.map(startUpdate)).map(_ => seq)
    }

    db.run(io.transactionally.withTransactionIsolation(TransactionIsolation.Serializable))
  }

}

object UpdateSchedulerDBIO {

  import com.advancedtelematic.libats.codecs.CirceRefined.*
  import io.circe.generic.auto.*
  import io.circe.syntax.*

  sealed trait InvalidReason

  case class IncompatibleFromTarget(updateId: UpdateId,
                                    ecuFromTarget: Option[EcuTargetId],
                                    updateFromTarget: Option[EcuTargetId])
      extends InvalidReason

  case class ScheduledUpdateExists(scheduledUpdateId: ScheduledUpdateId) extends InvalidReason

  case class IncompatibleEcuHardware(updateId: UpdateId,
                                     updateHardware: HardwareIdentifier,
                                     ecuHardware: Option[HardwareIdentifier])
      extends InvalidReason

  case class EcuAssignmentExists(updateId: UpdateId) extends InvalidReason

  case class HardwareUpdateMissing(updateId: UpdateId) extends InvalidReason

  implicit val ecuStatusForUpdateInvalidReasonEncoder: Encoder[InvalidReason] = Encoder.instance {
    case e: IncompatibleFromTarget  => Json.obj("incompatible_from_target" -> e.asJson)
    case e: IncompatibleEcuHardware => Json.obj("incompatible_ecu_hardware" -> e.asJson)
    case e: EcuAssignmentExists     => Json.obj("ecu_assignment_exists" -> e.asJson)
    case e: HardwareUpdateMissing   => Json.obj("hardware_update_missing" -> e.asJson)
    case e: ScheduledUpdateExists   => Json.obj("scheduled_update_exists" -> e.asJson)
  }

  case class InvalidEcuStatus(reasons: NonEmptyList[InvalidReason]) extends StatusInfo

  implicit val invalidEcuStatusCodec: Codec[InvalidEcuStatus] =
    io.circe.generic.semiauto.deriveCodec[InvalidEcuStatus]

}
