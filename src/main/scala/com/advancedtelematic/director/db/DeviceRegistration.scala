package com.advancedtelematic.director.db

import org.apache.pekko.http.scaladsl.util.FastFuture
import cats.implicits.toShow
import com.advancedtelematic.director.data.AdminDataType.{EcuInfoImage, EcuInfoResponse, RegisterEcu}
import com.advancedtelematic.director.data.UptaneDataType.Hashes
import com.advancedtelematic.director.db.ProvisionedDeviceRepository.DeviceCreateResult
import com.advancedtelematic.director.db.deviceregistry.{DeviceRepository, EcuReplacementRepository}
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.director.http.Errors.AssignmentExistsError
import com.advancedtelematic.director.http.deviceregistry.Errors.MissingDevice
import com.advancedtelematic.director.repo.DeviceRoleGeneration
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, EcuIdentifier}
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*

import scala.async.Async.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DeviceRegistration(keyserverClient: KeyserverClient)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends ProvisionedDeviceRepositorySupport
    with EcuRepositorySupport {
  val roleGeneration = new DeviceRoleGeneration(keyserverClient)

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def findDeviceEcuInfo(ns: Namespace, deviceId: DeviceId): Future[Vector[EcuInfoResponse]] =
    async {
      val primary = await(ecuRepository.findDevicePrimary(ns, deviceId)).ecuSerial
      val ecus = await(ecuRepository.findTargets(ns, deviceId))

      ecus.map { case (ecu, target) =>
        val img = EcuInfoImage(target.filename, target.length, Hashes(target.checksum))
        EcuInfoResponse(ecu.ecuSerial, ecu.hardwareId, ecu.ecuSerial == primary, img)
      }.toVector
    }

  private def registerAndRegenerateTargets(ns: Namespace,
                                           repoId: RepoId,
                                           deviceId: DeviceId,
                                           primaryEcuId: EcuIdentifier,
                                           ecus: Seq[RegisterEcu]): Future[DeviceCreateResult] =
    if (ecus.exists(_.ecu_serial == primaryEcuId)) {
      val _ecus = ecus.map(_.toEcu(ns, deviceId))

      for {
        result <- provisionedDeviceRepository.create(ecuRepository)(
          ns,
          deviceId,
          primaryEcuId,
          _ecus
        )
        _ <- roleGeneration.findFreshTargets(ns, repoId, deviceId)
      } yield result
    } else
      FastFuture.failed(Errors.PrimaryIsNotListedForDevice)

  private def insertEcuReplacementOrError(
    deviceCreateResult: Try[DeviceCreateResult]): Future[Unit] =
    deviceCreateResult match {
      case Success(event: ProvisionedDeviceRepository.Updated) =>
        val io = event.asEcuReplacedSeq.map { ecuReplaced =>
          EcuReplacementRepository.insert(ecuReplaced)
        }
        db.run(DBIO.sequence(io)).map(_ => ())
      case Failure(AssignmentExistsError(deviceId)) =>
        db.run(DeviceRepository.setDeviceStatus(deviceId, DeviceStatus.Error))
      case Failure(ex) =>
        FastFuture.failed(ex)
      case Success(_) =>
        FastFuture.successful(())
    }

  def register(ns: Namespace,
               repoId: RepoId,
               deviceId: DeviceId,
               primaryEcuId: EcuIdentifier,
               ecus: Seq[RegisterEcu]): Future[DeviceCreateResult] =
    registerAndRegenerateTargets(ns, repoId, deviceId, primaryEcuId, ecus).transformWith {
      createResult =>
        insertEcuReplacementOrError(createResult)
          .recover {
            case MissingDevice =>
              log.warn(
                s"Trying to replace ECUs on a non-existing or deleted device: ${deviceId.show}."
              )
            case ex =>
              log.warn("could not add journal events for ecu replacement", ex)
          }
          .transform(_ => createResult)
    }

}
