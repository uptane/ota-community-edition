package com.advancedtelematic.tuf.reposerver.http

import cats.implicits._
import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import com.advancedtelematic.libats.http.Errors.MissingEntityId
import com.advancedtelematic.tuf.reposerver.http.Errors._
import com.advancedtelematic.libtuf.data.ClientDataType.{
  DelegatedRoleName,
  Delegation,
  Delegations,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufDataType.TufKey
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration
import com.advancedtelematic.tuf.reposerver.db.SignedRoleRepositorySupport
import org.apache.pekko.http.scaladsl.util.FastFuture

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api._

class TrustedDelegations(implicit val db: Database, val ec: ExecutionContext)
    extends SignedRoleRepositorySupport {

  def validate(newDelegations: List[Delegation],
               existingTargets: TargetsRole): ValidatedNel[String, Delegations] =
    existingTargets.delegations match {
      case Some(delegations) =>
        validate(delegations.copy(roles = newDelegations))
      case None =>
        "Invalid or non-existent reference keys used by trusted delegations".invalidNel[Delegations]
    }

  def validate(delegations: Option[Delegations]): ValidatedNel[String, Delegations] =
    delegations match {
      case Some(s) => validate(s)
      // an empty delegations block is valid
      case _ => Delegations(Map(), List()).validNel[String]
    }

  def validate(delegations: Delegations): ValidatedNel[String, Delegations] = {
    val nameErrors = for {
      delegation <- delegations.roles
      if DelegatedRoleName.delegatedRoleNameValidation(delegation.name.value).isInvalid
    } yield DelegatedRoleName.delegatedRoleNameValidation(delegation.name.value).toString()

    val keyErrors = for {
      delegation <- delegations.roles
      keyid <- delegation.keyids if !delegations.keys.contains(keyid)
    } yield "Invalid delegation key referenced by: " + delegation.name

    val errorsList = nameErrors.++(keyErrors)
    if (errorsList.nonEmpty) {
      NonEmptyList.fromListUnsafe(errorsList).invalid[Delegations]
    } else
      delegations.validNel[String]
  }

  private def getTrustedDelegationsBlock(repoId: RepoId): Future[Option[Delegations]] =
    signedRoleRepository
      .find[TargetsRole](repoId)
      .map { signedTargetRole =>
        signedTargetRole.role.delegations
      }
      .recover { case _: MissingEntityId[_] =>
        None
      }

  def get(repoId: RepoId): Future[List[Delegation]] = getTrustedDelegationsBlock(repoId).map {
    case Some(delegations) => delegations.roles
    case None              => List.empty
  }

  def getKeys(repoId: RepoId): Future[List[TufKey]] = getTrustedDelegationsBlock(repoId).map {
    case Some(delegations) => delegations.keys.values.toList
    case None              => List.empty
  }

  def add(repoId: RepoId, delegations: List[Delegation])(
    signedRoleGeneration: SignedRoleGeneration): Future[Any] = for {
    existingTargetsRole <- signedRoleRepository.find[TargetsRole](repoId)
    delegationsBlock <- validate(delegations, existingTargetsRole.role)
      .fold(errors => FastFuture.failed(InvalidTrustedDelegations(errors)), FastFuture.successful)
    newTargetsRole <- signedRoleGeneration.genTargetsFromExistingItems(
      repoId,
      Some(delegationsBlock)
    )
    json <- signedRoleGeneration.signAllRolesFor(repoId, newTargetsRole)
  } yield json

  import scala.async.Async._

  def remove(repoId: RepoId, delegatedRoleName: DelegatedRoleName)(
    signedRoleGeneration: SignedRoleGeneration): Future[Unit] = async {
    val delegations = await(getTrustedDelegationsBlock(repoId))

    val newDelegations = delegations.map { d =>
      val newRoles = d.roles.filter(_.name != delegatedRoleName)
      d.copy(roles = newRoles)
    }

    val newTargets = await(signedRoleGeneration.genTargetsFromExistingItems(repoId, newDelegations))

    await(signedRoleGeneration.signAllRolesFor(repoId, newTargets))
  }

  def setKeys(repoId: RepoId, inKeys: List[TufKey])(
    signedRoleGeneration: SignedRoleGeneration): Future[Any] = for {
    delegationsBlock <- getTrustedDelegationsBlock(repoId).map {
      case Some(delegations) => delegations.copy(keys = inKeys.map(k => (k.id, k)).toMap)
      case None              => Delegations(inKeys.map(k => (k.id, k)).toMap, List())
    }
    newTargetsRole <- signedRoleGeneration.genTargetsFromExistingItems(
      repoId,
      Some(delegationsBlock)
    )
    json <- signedRoleGeneration.signAllRolesFor(repoId, newTargetsRole)
  } yield json

}
