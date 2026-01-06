package com.advancedtelematic.director.db.deviceregistry

import java.time.Instant

import com.advancedtelematic.libats.data.DataType.{CorrelationId, ResultCode}
import com.advancedtelematic.libats.data.{EcuIdentifier, PaginationResult}
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuInstallationReport}
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceInstallationResult,
  EcuInstallationResult,
  InstallationStat
}
import io.circe.Json
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickCirceMapper._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.libats.slick.db.SlickUrnMapper.correlationIdMapper
import slick.lifted.AbstractTable

import scala.concurrent.ExecutionContext

object InstallationReportRepository {

  trait InstallationResultTable {
    def correlationId: Rep[CorrelationId]
    def resultCode: Rep[ResultCode]
    def success: Rep[Boolean]
  }

  class DeviceInstallationResultTable(tag: Tag)
      extends Table[DeviceInstallationResult](tag, "DeviceInstallationResult")
      with InstallationResultTable {

    def correlationId = column[CorrelationId]("correlation_id")
    def resultCode = column[ResultCode]("result_code")
    def deviceUuid = column[DeviceId]("device_uuid")
    def success = column[Boolean]("success")
    def receivedAt = column[Instant]("received_at")(javaInstantMapping)
    def installationReport = column[Json]("installation_report")

    def * =
      (correlationId, resultCode, deviceUuid, success, receivedAt, installationReport) <>
        ((DeviceInstallationResult.apply _).tupled, DeviceInstallationResult.unapply)

    def pk = primaryKey("pk_device_report", (correlationId, deviceUuid))
  }

  val deviceInstallationResults = TableQuery[DeviceInstallationResultTable]

  class EcuInstallationResultTable(tag: Tag)
      extends Table[EcuInstallationResult](tag, "EcuInstallationResult")
      with InstallationResultTable {

    def correlationId = column[CorrelationId]("correlation_id")
    def resultCode = column[ResultCode]("result_code")
    def deviceUuid = column[DeviceId]("device_uuid")
    def ecuId = column[EcuIdentifier]("ecu_id")
    def success = column[Boolean]("success")

    def * =
      (correlationId, resultCode, deviceUuid, ecuId, success) <>
        ((EcuInstallationResult.apply _).tupled, EcuInstallationResult.unapply)

    def pk = primaryKey("pk_ecu_report", (deviceUuid, ecuId))
  }

  private val ecuInstallationResults = TableQuery[EcuInstallationResultTable]

  def saveInstallationResults(correlationId: CorrelationId,
                              deviceUuid: DeviceId,
                              deviceResultCode: ResultCode,
                              success: Boolean,
                              ecuReports: Map[EcuIdentifier, EcuInstallationReport],
                              receivedAt: Instant,
                              installationReport: Json)(
    implicit ec: ExecutionContext): DBIO[Unit] = {

    val deviceResult = DeviceInstallationResult(
      correlationId,
      deviceResultCode,
      deviceUuid,
      success,
      receivedAt,
      installationReport
    )
    val ecuResults = ecuReports.map { case (ecuId, ecuReport) =>
      EcuInstallationResult(
        correlationId,
        ecuReport.result.code,
        deviceUuid,
        ecuId,
        ecuReport.result.success
      )
    }
    val q =
      for {
        _ <- deviceInstallationResults.insertOrUpdate(deviceResult)
        _ <- DBIO.sequence(ecuResults.map(ecuInstallationResults.insertOrUpdate))
      } yield ()
    q.transactionally
  }

  private def statsQuery[T <: AbstractTable[_]](tableQuery: TableQuery[T],
                                                correlationId: CorrelationId)(
    implicit ec: ExecutionContext,
    ev: T <:< InstallationResultTable): DBIO[Seq[InstallationStat]] =
    tableQuery
      .map(r => (r.correlationId, r.resultCode, r.success))
      .filter(_._1 === correlationId)
      .groupBy(r => (r._2, r._3))
      .map(r => (r._1._1, r._2.length, r._1._2))
      .result
      .map(_.map(stat => InstallationStat(stat._1, stat._2, stat._3)))

  def installationStatsPerDevice(correlationId: CorrelationId)(
    implicit ec: ExecutionContext): DBIO[Seq[InstallationStat]] =
    statsQuery(deviceInstallationResults, correlationId)

  def installationStatsPerEcu(correlationId: CorrelationId)(
    implicit ec: ExecutionContext): DBIO[Seq[InstallationStat]] =
    statsQuery(ecuInstallationResults, correlationId)

  def fetchDeviceInstallationResult(
    correlationId: CorrelationId): DBIO[Seq[DeviceInstallationResult]] =
    deviceInstallationResults.filter(_.correlationId === correlationId).result

  def fetchDeviceInstallationResultFor(
    deviceId: DeviceId,
    correlationId: CorrelationId): DBIO[Seq[DeviceInstallationResult]] =
    deviceInstallationResults
      .filter(_.deviceUuid === deviceId)
      .filter(_.correlationId === correlationId)
      .result

  def fetchEcuInstallationReport(correlationId: CorrelationId): DBIO[Seq[EcuInstallationResult]] =
    ecuInstallationResults.filter(_.correlationId === correlationId).result

  private[db] def queryInstallationHistory(deviceId: DeviceId): Query[Rep[Json], Json, Seq] =
    deviceInstallationResults
      .filter(_.deviceUuid === deviceId)
      .sortBy(_.receivedAt.desc)
      .map(_.installationReport)

  def installationReports(deviceId: DeviceId, offset: Long, limit: Long)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[Json]] =
    queryInstallationHistory(deviceId).paginateResult(offset, limit)

}
