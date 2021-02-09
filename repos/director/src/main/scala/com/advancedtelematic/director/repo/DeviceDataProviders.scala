package com.advancedtelematic.director.repo


import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.util.FastFuture
import cats.implicits._
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DataType.{DeviceTargetsCustom, TargetItemCustom, TargetItemCustomEcuData}
import com.advancedtelematic.director.data.DbDataType._
import com.advancedtelematic.director.db.{AssignmentsRepositorySupport, DbSignedRoleRepositorySupport, EcuRepositorySupport, EcuTargetsRepositorySupport}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientHashes, ClientTargetItem, MetaItem, MetaPath, TargetsRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.libtuf_server.repo.server.TargetsItemsProvider.TargetItems
import com.advancedtelematic.libtuf_server.repo.server.{SignedRoleProvider, TargetsItemsProvider}
import io.circe.syntax._
import slick.jdbc.MySQLProfile.api._

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}

protected [repo] class DeviceSignedRoleProvider(ns: Namespace, deviceId: DeviceId)(implicit val db: Database, val ec: ExecutionContext) extends SignedRoleProvider
  with DbSignedRoleRepositorySupport {

  override def find[T](repoId: RepoId)(implicit evidence$1: TufRole[T]): Future[SignedRole[T]] =
    dbSignedRoleRepository.findLatest(deviceId).map(_.toSignedRole)

  override def persistAll(repoId: RepoId, roles: List[SignedRole[_]]): Future[List[SignedRole[_]]] =
    dbSignedRoleRepository.persistAll(roles.map(_.toDbSignedRole(deviceId))).map(_ => roles)
}


protected [repo] class DeviceTargetProvider(ns: Namespace, deviceId: DeviceId)(implicit val db: Database, val ec: ExecutionContext)
  extends TargetsItemsProvider[DeviceTargetsCustom]
  with AssignmentsRepositorySupport
  with EcuTargetsRepositorySupport with EcuRepositorySupport {

  private case class TargetItem(hashes: ClientHashes,
                        length: Long,
                        uri: Option[Uri],
                        ecuIds: List[EcuIdentifier]) {
    def +(other: TargetItem) = {
      require(other.length == length, "Cannot sum TargetItem with different lengths")
      require(other.uri == uri, "Cannot sum TargetItem with different uris")
      TargetItem(hashes ++ other.hashes, length, uri, ecuIds ++ other.ecuIds)
    }
  }


  override def findSignedTargetRoleDelegations(repoId: RepoId, signedRole: SignedRole[TargetsRole]): Future[Map[MetaPath, MetaItem]] =
    FastFuture.successful(Map.empty)

  override def findTargets(repoId: RepoId): Future[TargetItems[DeviceTargetsCustom]] = async {
    val assignments = await(assignmentsRepository.findBy(deviceId))
    val ecus = await(ecuRepository.findBy(deviceId)).map(e => e.ecuSerial -> e.hardwareId).toMap
    val maybeCorrelationId = assignments.headOption.map(_.correlationId)

    val targetsByFilenameF = assignments.map { assignment =>
      ecuTargetsRepository.find(ns, assignment.ecuTargetId).map { ecuTarget =>
        val hashes = Map(ecuTarget.checksum.method -> ecuTarget.checksum.hash)
        ecuTarget.filename -> this.TargetItem(hashes, ecuTarget.length, ecuTarget.uri, List(assignment.ecuId))
      }
    }.toList.sequence

    val targetsByFilename = await(targetsByFilenameF)
      .groupBy { case (filename, _) => filename }
      .mapValues(_.map(_._2))

    val items = targetsByFilename.mapValues { filenameItems =>
      val targetItem = filenameItems.reduce { _ + _ }
      val hwIds = ecus.filterKeys(targetItem.ecuIds.contains).mapValues(h => TargetItemCustomEcuData(h))
      val custom = TargetItemCustom(targetItem.uri, hwIds)

      ClientTargetItem(targetItem.hashes, targetItem.length, Option(custom.asJson))
    }

    TargetItems(items, custom = maybeCorrelationId.map(c => DeviceTargetsCustom(Option(c))))
  }
}
