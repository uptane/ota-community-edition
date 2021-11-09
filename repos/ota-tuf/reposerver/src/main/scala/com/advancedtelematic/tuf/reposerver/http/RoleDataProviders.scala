package com.advancedtelematic.tuf.reposerver.http


import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, MetaItem, MetaPath, TargetsRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, TargetFilename}
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.libtuf_server.repo.server.TargetsItemsProvider.TargetItems
import com.advancedtelematic.libtuf_server.repo.server.{SignedRoleProvider, TargetsItemsProvider}
import com.advancedtelematic.tuf.reposerver.db.{SignedRoleRepositorySupport, TargetItemRepositorySupport}
import com.advancedtelematic.tuf.reposerver.delegations.SignedRoleDelegationsFind
import io.circe.Json
import io.circe.syntax._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class TufRepoTargetItemsProvider()(implicit val db: Database, val ec: ExecutionContext) extends TargetsItemsProvider[Json]
  with TargetItemRepositorySupport {

  val delegationsFind = new SignedRoleDelegationsFind()

  override def findSignedTargetRoleDelegations(repoId: RepoId, signedRole: SignedRole[TargetsRole]): Future[Map[MetaPath, MetaItem]] =
    delegationsFind.findSignedTargetRoleDelegations(repoId, signedRole)

  override def findTargets(repoId: RepoId): Future[TargetItems[Json]] = {
    val items = targetItemRepo.findFor(repoId).map { items =>
      items.map { item =>
        val hashes = Map(item.checksum.method -> item.checksum.hash)
        item.filename -> ClientTargetItem(hashes, item.length, item.custom.map(_.asJson))
      }.toMap
    }

    items.map(TargetItems.apply(_))
  }
}

class TufRepoSignedRoleProvider(implicit val db: Database, val ec: ExecutionContext) extends SignedRoleProvider with SignedRoleRepositorySupport {
  override def find[T: TufRole](repoId: RepoId): Future[SignedRole[T]] =
    signedRoleRepository.find(repoId)

  override def persistAll(repoId: RepoId, roles: List[SignedRole[_]]): Future[List[SignedRole[_]]] =
    signedRoleRepository.persistAll(repoId, roles).map(_ => roles)
}
