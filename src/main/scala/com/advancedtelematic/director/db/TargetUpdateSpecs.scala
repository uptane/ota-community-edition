package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.AdminDataType.{
  TargetUpdate,
  TargetUpdateRequest,
  TargetUpdateSpec
}
import com.advancedtelematic.director.data.DataType.TargetSpecId
import com.advancedtelematic.director.data.DbDataType.{EcuTarget, EcuTargetId, HardwareUpdate}
import com.advancedtelematic.director.http.Errors.InvalidMtu
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

class TargetUpdateSpecs(implicit val db: Database, val ec: ExecutionContext)
    extends HardwareUpdateRepositorySupport
    with EcuTargetsRepositorySupport {

  def create(ns: Namespace, targetUpdateSpec: TargetUpdateSpec): Future[TargetSpecId] =
    db.run(createAction(ns, targetUpdateSpec).transactionally)

  protected[db] def createAction(ns: Namespace,
                                 targetUpdateSpec: TargetUpdateSpec): DBIO[TargetSpecId] = {
    if (targetUpdateSpec.targets.isEmpty)
      throw InvalidMtu("multiTargetUpdate.targets cannot be empty")

    val targetSpecId = TargetSpecId.generate()

    val hardwareUpdates = targetUpdateSpec.targets.map { case (hwId, targetUpdateReq) =>
      val toId = EcuTargetId.generate()

      val t = targetUpdateReq.to
      val to = EcuTarget(
        ns,
        toId,
        t.target,
        t.targetLength,
        t.checksum,
        t.checksum.hash,
        t.uri,
        t.userDefinedCustom
      )

      val from = targetUpdateReq.from.map { f =>
        val fromId = EcuTargetId.generate()
        EcuTarget(
          ns,
          fromId,
          f.target,
          f.targetLength,
          f.checksum,
          f.checksum.hash,
          f.uri,
          f.userDefinedCustom
        )
      }

      for {
        _ <- from.map(ecuTargetsRepository.persistAction).getOrElse(DBIO.successful(()))
        _ <- ecuTargetsRepository.persistAction(to)
        _ <- hardwareUpdateRepository.persistAction(
          HardwareUpdate(ns, targetSpecId, hwId, from.map(_.id), to.id)
        )
      } yield ()
    }.toVector

    DBIO.sequence(hardwareUpdates).map(_ => targetSpecId)
  }

  def find(ns: Namespace, TargetSpecId: TargetSpecId): Future[TargetUpdateSpec] =
    hardwareUpdateRepository
      .findUpdateTargets(ns, TargetSpecId)
      .map { hardwareUpdates =>
        hardwareUpdates.foldLeft(Map.empty[HardwareIdentifier, TargetUpdateRequest]) {
          case (acc, (hu, fromO, toU)) =>
            val from = fromO.map { f =>
              TargetUpdate(f.filename, f.checksum, f.length, f.uri, toU.userDefinedCustom)
            }

            val to =
              TargetUpdate(toU.filename, toU.checksum, toU.length, toU.uri, toU.userDefinedCustom)

            acc + (hu.hardwareId -> TargetUpdateRequest(from, to))
        }
      }
      .map(targets => TargetUpdateSpec(targets))

}
