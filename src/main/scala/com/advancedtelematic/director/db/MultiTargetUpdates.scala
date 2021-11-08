package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.AdminDataType.{MultiTargetUpdate, TargetUpdate, TargetUpdateRequest}
import com.advancedtelematic.director.data.DbDataType.{EcuTarget, EcuTargetId, HardwareUpdate}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateId
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class MultiTargetUpdates(implicit val db: Database, val ec: ExecutionContext)
  extends HardwareUpdateRepositorySupport with EcuTargetsRepositorySupport {

  def create(ns: Namespace, multiTargetUpdate: MultiTargetUpdate): Future[UpdateId] = {
    val updateId = UpdateId.generate

    val hardwareUpdates = multiTargetUpdate.targets.map { case (hwId, targetUpdateReq) =>
      val toId = EcuTargetId.generate()

      val to = EcuTarget(ns, toId, targetUpdateReq.to.target, targetUpdateReq.to.targetLength,
        targetUpdateReq.to.checksum, targetUpdateReq.to.checksum.hash, targetUpdateReq.to.uri)

      val from = targetUpdateReq.from.map { f =>
        val fromId = EcuTargetId.generate()
        EcuTarget(ns, fromId, f.target, f.targetLength, f.checksum, f.checksum.hash, f.uri)
      }

      for {
        _ <- from.map(ecuTargetsRepository.persistAction).getOrElse(DBIO.successful(()))
        _ <- ecuTargetsRepository.persistAction(to)
        _ <- hardwareUpdateRepository.persistAction(HardwareUpdate(ns, updateId, hwId, from.map(_.id), to.id))
      } yield ()
    }.toVector

    db.run(DBIO.sequence(hardwareUpdates).transactionally).map(_ => updateId)
  }

  def find(ns: Namespace, updateId: UpdateId): Future[MultiTargetUpdate] =
    hardwareUpdateRepository.findUpdateTargets(ns, updateId).map { hardwareUpdates =>
      hardwareUpdates.foldLeft(Map.empty[HardwareIdentifier, TargetUpdateRequest]) { case (acc, (hu, fromO, toU)) =>
        val from = fromO.map { f =>
          TargetUpdate(f.filename, f.checksum, f.length, f.uri)
        }

        val to = TargetUpdate(toU.filename, toU.checksum, toU.length, toU.uri)

        acc + (hu.hardwareId -> TargetUpdateRequest(from, to))
      }
    }.map { targets => MultiTargetUpdate(targets) }
}
