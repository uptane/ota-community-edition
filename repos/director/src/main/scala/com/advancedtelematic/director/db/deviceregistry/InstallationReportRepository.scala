package com.advancedtelematic.director.db.deviceregistry

import cats.implicits.toShow

import java.time.Instant
import com.advancedtelematic.libats.data.DataType.{CorrelationId, ResultCode}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier, EcuInstallationReport}
import com.advancedtelematic.director.deviceregistry.data.DataType.{DeviceInstallationResult, EcuInstallationResult, InstallationStat}
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import io.circe.Json
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
import com.advancedtelematic.libats.slick.db.SlickCirceMapper.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickUrnMapper
import com.advancedtelematic.libats.slick.db.SlickUrnMapper.correlationIdMapper
import slick.jdbc.GetResult
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
    def description = column[Option[String]]("description")

    def * =
      (correlationId, resultCode, deviceUuid, ecuId, success, description) <>
        ((EcuInstallationResult.apply _).tupled, EcuInstallationResult.unapply)

    def pk = primaryKey("pk_ecu_report", (correlationId, deviceUuid, ecuId))
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
        ecuReport.result.success,
        Option(ecuReport.result.description.value)
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

//  def fetchDeviceInstallationResult(
//    correlationId: CorrelationId): DBIO[Seq[DeviceInstallationResult]] =
//    deviceInstallationResults.filter(_.correlationId === correlationId).result

  import cats.syntax.either.*

  def fetchManyDevicesInstallationResults(ids: Set[(DeviceId, CorrelationId)]): DBIO[Vector[DeviceInstallationResult]] =  {
    val idsStr = ids.map { case (d, c) => s"(${d.show}, ${c.toString})"}.mkString(",")

    implicit val getDeviceInstallationResult: GetResult[DeviceInstallationResult] = GetResult { r =>
      DeviceInstallationResult(
        correlationId = CorrelationId.fromString(r.nextString()).valueOr(err => throw new IllegalArgumentException(err)),
        resultCode = ResultCode(r.nextString()),
        deviceId = DeviceId(java.util.UUID.fromString(r.nextString())),
        success = r.nextBoolean(),
        receivedAt = r.nextTimestamp().toInstant,
        installationReport = io.circe.parser.parse(r.nextString()).getOrElse(Json.Null)
      )
    }

    sql"""SELECT correlation_id, result_code, device_uuid, success, received_at FROM #${deviceInstallationResults.baseTableRow.tableName}
           WHERE (device_id, correlation_id) IN (#$idsStr)
      """.as[DeviceInstallationResult]
  }

  def fetchManyByDevice(deviceId: DeviceId, correlationIds: Set[CorrelationId]): DBIO[Seq[DeviceInstallationResult]] =
    deviceInstallationResults
      .filter(_.deviceUuid === deviceId)
      .filter(_.correlationId.inSet(correlationIds))
      .result

  def fetchDeviceInstallationResultByCorrelationId(deviceId: DeviceId,
                                                   correlationId: CorrelationId): DBIO[Option[DeviceInstallationResult]] =
    deviceInstallationResults
      .filter(_.deviceUuid === deviceId)
      .filter(_.correlationId === correlationId)
      .result
      .headOption

  def fetchEcuInstallationReport(deviceId: DeviceId, correlationId: CorrelationId)(
    implicit ec: ExecutionContext): DBIO[Map[EcuIdentifier, EcuInstallationResult]] =
    ecuInstallationResults
      .filter(_.deviceUuid === deviceId)
      .filter(_.correlationId === correlationId)
      .result
      .map { seq =>
        seq.map { res =>
          res.ecuId -> res
        }.toMap
      }

  private[db] def queryInstallationHistory(deviceId: DeviceId): Query[Rep[Json], Json, Seq] =
    deviceInstallationResults
      .filter(_.deviceUuid === deviceId)
      .sortBy(_.receivedAt.desc)
      .map(_.installationReport)
  // TODO: Returning or even storing an installationReport here doesn't make sense, since it just contains
  // the same as ecuInstallationResults. installationReport is just the serialized form of the DeviceUpdateEvent
  // that originated this deviceInstallationResult
  // We cannot change this now as this is used by the frontend currently

  def installationReports(deviceId: DeviceId, offset: Offset, limit: Limit)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[Json]] =
    queryInstallationHistory(deviceId).paginateResult(offset, limit)

}
