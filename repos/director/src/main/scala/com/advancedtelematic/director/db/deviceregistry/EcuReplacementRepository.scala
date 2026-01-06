package com.advancedtelematic.director.db.deviceregistry

import java.time.Instant

import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.option._
import com.advancedtelematic.director.http.deviceregistry.Errors
import com.advancedtelematic.libats.data.{EcuIdentifier, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.MessageCodecs.ecuReplacementCodec
import com.advancedtelematic.libats.messaging_datatype.Messages.{
  EcuAndHardwareId,
  EcuReplaced,
  EcuReplacement,
  EcuReplacementFailed
}
import com.advancedtelematic.libats.slick.codecs.SlickRefined.refinedMappedType
import com.advancedtelematic.libats.slick.db.SlickExtensions.javaInstantMapping
import com.advancedtelematic.libats.slick.db.SlickResultExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.dbMapping
import com.advancedtelematic.libats.slick.db.SlickValidatedGeneric.validatedStringMapper
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, ValidHardwareIdentifier}
import eu.timepit.refined.refineV
import io.circe.Json
import io.circe.syntax._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

object EcuReplacementRepository {

  private type Record = (
    DeviceId,
    Option[EcuIdentifier],
    Option[HardwareIdentifier],
    Option[EcuIdentifier],
    Option[HardwareIdentifier],
    Instant,
    Boolean
  )

  class EcuReplacementTable(tag: Tag) extends Table[EcuReplacement](tag, "EcuReplacement") {
    def deviceId = column[DeviceId]("device_uuid")
    def formerEcuId = column[Option[EcuIdentifier]]("former_ecu_id")
    def formerHardwareId = column[Option[HardwareIdentifier]]("former_hardware_id")
    def currentEcuId = column[Option[EcuIdentifier]]("current_ecu_id")
    def currentHardwareId = column[Option[HardwareIdentifier]]("current_hardware_id")
    def replacedAt = column[Instant]("replaced_at")(javaInstantMapping)
    def success = column[Boolean]("success")

    private def tupleToClass(record: Record): EcuReplacement =
      record match {
        case (did, Some(fe), Some(fh), Some(ce), Some(ch), at, s) if s =>
          EcuReplaced(did, EcuAndHardwareId(fe, fh.value), EcuAndHardwareId(ce, ch.value), at)
        case (did, _, _, _, _, at, s) if !s =>
          EcuReplacementFailed(did, at)
        case _ =>
          throw Errors.CannotSerializeEcuReplacement
      }

    private def classToTuple(ecuReplacement: EcuReplacement): Option[Record] =
      ecuReplacement match {
        case EcuReplacementFailed(did, at) =>
          Some(did, None, None, None, None, at, false)
        case EcuReplaced(did, f, c, at) =>
          (
            refineV[ValidHardwareIdentifier](f.hardwareId).toOption,
            refineV[ValidHardwareIdentifier](c.hardwareId).toOption
          ).mapN { case (fhid, chid) =>
            (did, f.ecuId.some, fhid.some, c.ecuId.some, chid.some, at, true)
          }
      }

    override def * =
      (
        deviceId,
        formerEcuId,
        formerHardwareId,
        currentEcuId,
        currentHardwareId,
        replacedAt,
        success
      ).shaped <> (tupleToClass, classToTuple)

  }

  private val ecuReplacements = TableQuery[EcuReplacementTable]

  def insert(er: EcuReplacement)(implicit ec: ExecutionContext): DBIO[Unit] =
    (ecuReplacements += er)
      .handleForeignKeyError(Errors.MissingDevice)
      .map(_ => ())

  def fetchForDevice(deviceId: DeviceId): DBIO[Seq[EcuReplacement]] =
    ecuReplacements
      .filter(_.deviceId === deviceId)
      .result

  def deviceHistory(deviceId: DeviceId, offset: Long, limit: Long)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[Json]] =
    for {
      installations <- InstallationReportRepository.queryInstallationHistory(deviceId).result
      replacements <- fetchForDevice(deviceId).map(_.map(_.asJson))
      history = (installations ++ replacements).sortBy(
        _.hcursor.get[Instant]("eventTime").toOption
      )(Ordering[Option[Instant]].reverse)
      values = history.drop(offset.toInt).take(limit.toInt)
    } yield PaginationResult(values, history.length, offset, limit)

}
