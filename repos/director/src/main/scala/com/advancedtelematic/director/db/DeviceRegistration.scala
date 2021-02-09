package com.advancedtelematic.director.db

import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.data.AdminDataType.{EcuInfoImage, EcuInfoResponse, RegisterEcu}
import com.advancedtelematic.director.data.UptaneDataType.Hashes
import com.advancedtelematic.director.db.DeviceRepository.DeviceCreateResult
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.director.http.Errors.AssignmentExistsError
import com.advancedtelematic.director.repo.DeviceRoleGeneration
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.{EcuReplacement, EcuReplacementFailed}
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import slick.jdbc.MySQLProfile.api._

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DeviceRegistration(keyserverClient: KeyserverClient)(implicit val db: Database, val ec: ExecutionContext) extends DeviceRepositorySupport
  with EcuRepositorySupport  {
  val roleGeneration = new DeviceRoleGeneration(keyserverClient)

  def findDeviceEcuInfo(ns: Namespace, deviceId: DeviceId): Future[Vector[EcuInfoResponse]] = async {
    val primary = await(ecuRepository.findDevicePrimary(ns, deviceId)).ecuSerial
    val ecus = await(ecuRepository.findTargets(ns, deviceId))

    ecus.map { case (ecu, target) =>
      val img = EcuInfoImage(target.filename, target.length, Hashes(target.checksum))
      EcuInfoResponse(ecu.ecuSerial, ecu.hardwareId, ecu.ecuSerial == primary, img)
    }.toVector
  }

  private def register(ns: Namespace, repoId: RepoId, deviceId: DeviceId, primaryEcuId: EcuIdentifier, ecus: Seq[RegisterEcu]): Future[DeviceCreateResult] = {
    if (ecus.exists(_.ecu_serial == primaryEcuId)) {
      val _ecus = ecus.map(_.toEcu(ns, deviceId))

      for {
        result <- deviceRepository.create(ecuRepository)(ns, deviceId, primaryEcuId, _ecus)
        _ <- roleGeneration.findFreshTargets(ns, repoId, deviceId)
      } yield result
    } else
      FastFuture.failed(Errors.PrimaryIsNotListedForDevice)
  }

  def registerAndPublish(ns: Namespace, repoId: RepoId, deviceId: DeviceId, primaryEcuId: EcuIdentifier, ecus: Seq[RegisterEcu])
                        (implicit messageBusPublisher: MessageBusPublisher): Future[DeviceCreateResult] =
    register(ns, repoId, deviceId, primaryEcuId, ecus).andThen {
      case Success(event: DeviceRepository.Updated) =>
        event.asEcuReplacedSeq.foreach(messageBusPublisher.publishSafe(_))
      case Failure(AssignmentExistsError(deviceId)) =>
        val failedReplacement: EcuReplacement = EcuReplacementFailed(deviceId)
        messageBusPublisher.publishSafe(failedReplacement)
    }
}
