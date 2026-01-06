package com.advancedtelematic.tuf.reposerver.http

import com.advancedtelematic.libtuf.data.ClientDataType
import com.advancedtelematic.libtuf.data.TufDataType.{
  validHardwareIdentifier,
  HardwareIdentifier,
  JsonSignedPayload,
  RepoId,
  TargetFilename,
  ValidTargetFilename
}
import com.advancedtelematic.libtuf_server.repo.client.ReposerverClient.EditTargetItem
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.TargetItem
import com.advancedtelematic.tuf.reposerver.db.{
  FilenameCommentRepository,
  TargetItemRepositorySupport
}
import io.circe.Json
import slick.jdbc.MySQLProfile.api._

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

class TargetRoleEdit(signedRoleGeneration: SignedRoleGeneration)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends TargetItemRepositorySupport
    with FilenameCommentRepository.Support {

  def addTargetItem(targetItem: TargetItem): Future[JsonSignedPayload] = for {
    _ <- targetItemRepo.persist(targetItem)
    json <- signedRoleGeneration.regenerateAllSignedRoles(targetItem.repoId)
  } yield json

  private def mergeCustomJson(existing: Json, provided: Json): Json =
    if (provided == Json.obj())
      provided
    else
      (existing.asObject, provided.asObject) match {
        case (Some(lhs), Some(rhs)) =>
          Json.fromJsonObject(rhs.toIterable.foldLeft(lhs) { case (acc, (k, v)) =>
            acc.add(k, v)
          })
        case _ => provided
      }

  def updateTargetProprietaryCustom(repoId: RepoId,
                                    filename: TargetFilename,
                                    proprietaryJson: Json): Future[Unit] = for {
    existing <- targetItemRepo.findByFilename(repoId, filename)
    newCustomJson = existing.custom.map { custom =>
      custom.copy(proprietary = mergeCustomJson(custom.proprietary, proprietaryJson))
    }
    _ <- targetItemRepo.setCustom(repoId, filename, newCustomJson)
    _ <- signedRoleGeneration.regenerateAllSignedRoles(repoId)
  } yield ()

  def deleteTargetItem(repoId: RepoId, filename: TargetFilename): Future[Unit] = for {
    _ <- signedRoleGeneration.ensureTargetsCanBeSigned(repoId)
    _ <- targetItemRepo.deleteItemAndComments(filenameCommentRepo)(repoId, filename)
    _ <- signedRoleGeneration.regenerateAllSignedRoles(repoId)
  } yield ()

  def editTargetItemCustom(repoId: RepoId,
                           filename: TargetFilename,
                           targetEdit: EditTargetItem): Future[Unit] =
    for {
      _ <- signedRoleGeneration.ensureTargetsCanBeSigned(repoId)
      existingTarget <- targetItemRepo.findByFilename(repoId, filename)
      newCustomJson = existingTarget.custom.map { existingCustom =>
        existingCustom.copy(
          uri = if (targetEdit.uri.isDefined) targetEdit.uri else existingCustom.uri,
          hardwareIds =
            if (targetEdit.hardwareIds.nonEmpty) targetEdit.hardwareIds
            else existingCustom.hardwareIds,
          proprietary = targetEdit.proprietaryCustom.getOrElse(existingCustom.proprietary)
        )
      }
      _ <- targetItemRepo.setCustom(repoId, filename, newCustomJson)
      _ <- signedRoleGeneration.regenerateAllSignedRoles(repoId)
    } yield ()

}
