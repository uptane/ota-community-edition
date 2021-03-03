package com.advancedtelematic.tuf.reposerver.http

import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId, TargetFilename}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.TargetItem
import com.advancedtelematic.tuf.reposerver.db.{FilenameCommentRepository, TargetItemRepositorySupport}

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api._

class TargetRoleEdit(roleSigningClient: KeyserverClient, signedRoleGeneration: SignedRoleGeneration)
                    (implicit val db: Database, val ec: ExecutionContext)
  extends TargetItemRepositorySupport with FilenameCommentRepository.Support {

  def addTargetItem(targetItem: TargetItem): Future[JsonSignedPayload] = for {
    _ <- targetItemRepo.persist(targetItem)
    json <- signedRoleGeneration.regenerateAllSignedRoles(targetItem.repoId)
  } yield json

  def deleteTargetItem(repoId: RepoId, filename: TargetFilename): Future[Unit] = for {
    _ <- signedRoleGeneration.ensureTargetsCanBeSigned(repoId)
    _ <- targetItemRepo.deleteItemAndComments(filenameCommentRepo)(repoId, filename)
    _ <- signedRoleGeneration.regenerateAllSignedRoles(repoId)
  } yield ()
}
