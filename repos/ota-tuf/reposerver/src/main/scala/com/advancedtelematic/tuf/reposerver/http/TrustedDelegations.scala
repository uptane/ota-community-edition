package com.advancedtelematic.tuf.reposerver.http

import cats.implicits._
import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import com.advancedtelematic.libats.http.Errors.MissingEntityId
import com.advancedtelematic.tuf.reposerver.http.Errors._
import com.advancedtelematic.libtuf.data.ClientDataType.{Delegation, Delegations, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.TufKey
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration
import com.advancedtelematic.tuf.reposerver.db.SignedRoleRepositorySupport
import akka.http.scaladsl.util.FastFuture

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api._

class TrustedDelegations(implicit val db: Database, val ec: ExecutionContext) extends SignedRoleRepositorySupport {

  def validate(newDelegations : List[Delegation], existingTargets: TargetsRole): ValidatedNel[String, Delegations] = {
    existingTargets.delegations match {
      case Some(delegations) =>
        validate(delegations.copy(roles = newDelegations))
      case None => "Invalid or non-existent reference keys used by trusted delegations".invalidNel[Delegations]
    }
  }

  def validate(repoId: RepoId, delegations: Option[Delegations]): ValidatedNel[String, Delegations] = {
    delegations match {
      case Some(s) => validate(s)
      // an empty delegations block is valid
      case _ => Delegations(Map(), List()).validNel[String]
    }
  }

  def validate(delegations: Delegations): ValidatedNel[String, Delegations] = {
    val keyErrors = for {
      delegation <- delegations.roles
      keyid <- delegation.keyids if !delegations.keys.contains(keyid)
    } yield "Invalid delegation key referenced by: " + delegation.name
    if (keyErrors.nonEmpty) {
      NonEmptyList.fromListUnsafe(keyErrors).invalid[Delegations]
    } else
      delegations.validNel[String]
  }

  def getTrustedDelegationsBlock(repoId: RepoId): Future[Option[Delegations]] =
    signedRoleRepository.find[TargetsRole](repoId).map { signedTargetRole =>
      signedTargetRole.role.delegations
    }.recover {
      case _: MissingEntityId[_] => None
    }

  def get(repoId: RepoId): Future[List[Delegation]] = getTrustedDelegationsBlock(repoId).map {
    case Some(delegations) => delegations.roles
    case None => List.empty
  }

  def getKeys(repoId: RepoId): Future[List[TufKey]] = getTrustedDelegationsBlock(repoId).map {
    case Some(delegations) => delegations.keys.values.toList
    case None => List.empty
  }

  def add(repoId: RepoId, delegations: List[Delegation])(signedRoleGeneration: SignedRoleGeneration): Future[Any] = for {
    existingTargetsRole <- signedRoleRepository.find[TargetsRole](repoId)
    delegationsBlock <- validate(delegations, existingTargetsRole.role).fold (
      errors => FastFuture.failed(InvalidTrustedDelegations(errors)),
      FastFuture.successful)
    newTargetsRole <- signedRoleGeneration.genTargetsFromExistingItems(repoId, Some(delegationsBlock))
    json <- signedRoleGeneration.signAllRolesFor(repoId, newTargetsRole)
  } yield json

  def addKeys(repoId: RepoId, inKeys: List[TufKey])(signedRoleGeneration: SignedRoleGeneration): Future[Any] = for {
    delegationsBlock <- getTrustedDelegationsBlock(repoId).map {
      case Some(delegations) => delegations.copy(keys = inKeys.map(k => (k.id, k)).toMap)
      case None => Delegations(inKeys.map(k => (k.id, k)).toMap, List())
    }
    newTargetsRole <- signedRoleGeneration.genTargetsFromExistingItems(repoId,Some(delegationsBlock))
    json <- signedRoleGeneration.signAllRolesFor(repoId, newTargetsRole)
  } yield json
}