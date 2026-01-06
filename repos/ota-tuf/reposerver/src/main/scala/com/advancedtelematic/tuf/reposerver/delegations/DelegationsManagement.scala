package com.advancedtelematic.tuf.reposerver.delegations

import org.apache.pekko.http.scaladsl.model.Uri
import cats.implicits.*
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.advancedtelematic.libats.data.RefinedUtils.*
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  DelegatedRoleName,
  Delegation,
  DelegationClientTargetItem,
  DelegationFriendlyName,
  DelegationInfo,
  MetaItem,
  MetaPath,
  TargetCustom,
  TargetsRole,
  ValidMetaPath
}
import com.advancedtelematic.libtuf.data.TufDataType.{
  JsonSignedPayload,
  RepoId,
  SignedPayload,
  TargetFilename
}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration
import com.advancedtelematic.tuf.reposerver.db.{
  DelegationRepositorySupport,
  SignedRoleRepositorySupport
}
import com.advancedtelematic.tuf.reposerver.http.*
import slick.jdbc.MySQLProfile.api.*

import scala.async.Async.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import org.apache.pekko.http.scaladsl.unmarshalling.*
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.TufCodecs
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.{DelegatedTargetItem, TargetItem}

import java.nio.file.{FileSystems, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit

class SignedRoleDelegationsFind()(implicit val db: Database, val ec: ExecutionContext)
    extends DelegationRepositorySupport {

  import com.advancedtelematic.libtuf.crypt.CanonicalJson._
  import com.advancedtelematic.libtuf.data.TufCodecs._
  import io.circe.syntax._

  def findSignedTargetRoleDelegations(
    repoId: RepoId,
    targetRole: SignedRole[TargetsRole]): Future[Map[MetaPath, MetaItem]] = {
    val delegatedRoleNames =
      targetRole.role.delegations.map(_.roles.map(_.name)).getOrElse(List.empty)
    val delegationsF =
      delegatedRoleNames
        .map(name => delegationsRepo.find(repoId, name).map((name, _)))
        .sequence
        .recover { case Errors.DelegationNotFound => List.empty }

    for {
      delegations <- delegationsF
      delegationsAsMetaItems <- delegations.map { case (name, d) =>
        Future.fromTry {
          (name.value + ".json").refineTry[ValidMetaPath].product(asMetaItem(d.content))
        }
      }.sequence
    } yield delegationsAsMetaItems.toMap
  }

  private def asMetaItem(content: JsonSignedPayload): Try[MetaItem] = {
    val canonicalJson = TufCodecs.jsonSignedPayloadEncoder(content).canonical
    val checksum = Sha256Digest.digest(canonicalJson.getBytes)
    val hashes = Map(checksum.method -> checksum.hash)
    val versionT = content.signed.hcursor.downField("version").as[Int].toTry

    versionT.map(version => MetaItem(hashes, canonicalJson.getBytes.length, version))
  }

}

class DelegationsManagement()(implicit val db: Database, val ec: ExecutionContext)
    extends DelegationRepositorySupport
    with SignedRoleRepositorySupport {

  def create(repoId: RepoId,
             roleName: DelegatedRoleName,
             delegationMetadata: SignedPayload[TargetsRole],
             remoteUri: Option[Uri] = None,
             lastFetch: Option[Instant] = None,
             remoteHeaders: Map[String, String] = Map.empty,
             friendlyName: Option[DelegationFriendlyName] = None)(
    implicit signedRoleGeneration: SignedRoleGeneration): Future[Unit] = async {
    val targetsRole = await(signedRoleRepository.find[TargetsRole](repoId)).role
    val trustedDelegation = findTrustedDelegationByName(targetsRole, roleName)

    validateDelegationMetadataSignatures(targetsRole, trustedDelegation, delegationMetadata)
      .valueOr(err => throw Errors.PayloadSignatureInvalid(err))

    val validatedDelegationsRole =
      validateDelegationTargetPaths(targetsRole, roleName, delegationMetadata)
        .valueOr(err => throw Errors.InvalidDelegatedTarget(err))

    val items = validatedDelegationsRole.signed.targets.map { case (filename, clientTargetItem) =>
      val checksums = clientTargetItem.hashes.map { case (method, hash) =>
        Checksum(method, hash)
      }

      if (checksums.isEmpty)
        throw Errors.InvalidDelegatedTarget(NonEmptyList.of("targets checksum cannot be empty"))

      val targetCreatedAt = clientTargetItem.custom
        .flatMap { custom =>
          custom.hcursor.get[Instant]("createdAt").toOption
        }
        .orElse(Some(Instant.now()))

      DelegatedTargetItem(
        repoId,
        filename,
        roleName,
        checksums.head,
        clientTargetItem.length,
        targetCreatedAt,
        clientTargetItem.custom
      )
    }

    await(
      delegationsRepo.persistAll(
        repoId,
        roleName,
        delegationMetadata.asJsonSignedPayload,
        remoteUri,
        lastFetch,
        remoteHeaders,
        friendlyName,
        items.toSeq
      )
    )

    await(signedRoleGeneration.regenerateSnapshots(repoId))
  }

  def createFromRemote(repoId: RepoId,
                       uri: Uri,
                       delegationName: DelegatedRoleName,
                       remoteHeaders: Map[String, String],
                       friendlyName: Option[DelegationFriendlyName] = None)(
    implicit signedRoleGeneration: SignedRoleGeneration,
    client: RemoteDelegationClient): Future[Unit] = async {
    val signedRole = await(client.fetch[SignedPayload[TargetsRole]](uri, remoteHeaders))
    await(
      create(
        repoId,
        delegationName,
        signedRole,
        Option(uri),
        Option(Instant.now()),
        remoteHeaders,
        friendlyName
      )
    )
  }

  def updateFromRemote(repoId: RepoId, delegatedRoleName: DelegatedRoleName)(
    implicit signedRoleGeneration: SignedRoleGeneration,
    client: RemoteDelegationClient): Future[Unit] = async {
    val delegation = await(delegationsRepo.find(repoId, delegatedRoleName))

    if (delegation.remoteUri.isEmpty)
      throw Errors.MissingRemoteDelegationUri(repoId, delegatedRoleName)

    val signedRole = await(
      client.fetch[SignedPayload[TargetsRole]](delegation.remoteUri.get, delegation.remoteHeaders)
    )

    await(
      create(
        repoId,
        delegatedRoleName,
        signedRole,
        delegation.remoteUri,
        Option(Instant.now()),
        delegation.remoteHeaders
      )
    )
  }

  def find(repoId: RepoId,
           roleName: DelegatedRoleName): Future[(JsonSignedPayload, DelegationInfo)] = async {
    val delegation = await(delegationsRepo.find(repoId, roleName))
    (
      delegation.content,
      DelegationInfo(
        delegation.lastFetched,
        delegation.remoteUri,
        delegation.friendlyName,
        delegation.content.signed.as[TargetsRole].toOption.map(_.expires)
      )
    )
  }

  private def getAllDelegationTargets(
    repoId: RepoId): Future[Seq[(DelegatedRoleName, Map[TargetFilename, ClientTargetItem])]] =
    async {
      val expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
      val delegations = await(delegationsRepo.findAll(repoId)).toList

      val f = delegations.map { delegation =>
        SignedRole
          .withChecksum[TargetsRole](delegation.content, 1, expiresAt)
          .map(signedRole => (delegation.roleName, signedRole.role.targets))
      }.sequence
      await(f)
    }

  def findTargetByFilename(
    repoId: RepoId,
    targetFilename: TargetFilename): Future[Seq[DelegationClientTargetItem]] = {
    val delegationItems = getAllDelegationTargets(repoId).map { delegations =>
      delegations.flatMap { case (delegatedRoleName, targetsMap) =>
        targetsMap.filter(_._1 == targetFilename).map { case (filename, targetItem) =>
          DelegationClientTargetItem(filename, delegatedRoleName, targetItem)
        }
      }
    }
    // Having the same named target across delegations is possible, so we must return a list
    delegationItems.flatMap { items =>
      if (items.isEmpty)
        Future.failed(
          Errors
            .InvalidDelegatedTarget(NonEmptyList[String]("Target Not Found", List.empty[String]))
        )
      else Future.successful(items)
    }
  }

  // delegations dont have a database table for targetItems so we must use the json directly
  def findTargets(repoId: RepoId,
                  nameContains: Option[String] = None): Future[List[DelegationClientTargetItem]] = {
    val allDelegationTargets = getAllDelegationTargets(repoId)
    allDelegationTargets.map {
      _.flatMap { case (delegatedRoleName, targetsMap) =>
        val filteredMap = nameContains match {
          case Some(containsExpr) =>
            targetsMap.view.filterKeys(_.value.contains(containsExpr))
          case None =>
            targetsMap
        }
        filteredMap.map { case (filename, item) =>
          DelegationClientTargetItem(filename, delegatedRoleName, item)
        }
      }.toList
    }
  }

  def setDelegationInfo(repoId: RepoId,
                        roleName: DelegatedRoleName,
                        delegationInfo: DelegationInfo): Future[Unit] =
    delegationInfo match {
      case DelegationInfo(Some(_), _, _, _) =>
        throw Errors.RequestedImmutableFields(Seq("friendlyName"), Seq("lastFetched", "remoteUri"))
      case DelegationInfo(_, Some(_), _, _) =>
        throw Errors.RequestedImmutableFields(Seq("friendlyName"), Seq("lastFetched", "remoteUri"))
      case DelegationInfo(_, _, Some(friendlyName), _) =>
        delegationsRepo.setDelegationFriendlyName(repoId, roleName, friendlyName)
      case DelegationInfo(_, _, None, _) =>
        throw Errors.InvalidDelegationName(NonEmptyList.one("missing friendlyName field"))
    }

  private def findTrustedDelegationByName(targetsRole: TargetsRole,
                                          delegatedRoleName: DelegatedRoleName): Delegation =
    targetsRole.delegations
      .flatMap(_.roles.find(_.name == delegatedRoleName))
      .getOrElse(throw Errors.DelegationNotDefined)

  private def validateDelegationMetadataSignatures(targetsRole: TargetsRole,
                                                   delegation: Delegation,
                                                   delegationMetadata: SignedPayload[TargetsRole])
    : ValidatedNel[String, SignedPayload[TargetsRole]] = {
    val publicKeys = targetsRole.delegations
      .map(_.keys)
      .getOrElse(Map.empty)
      .view
      .filterKeys(delegation.keyids.contains)
      .toMap
    TufCrypto.payloadSignatureIsValid(publicKeys, delegation.threshold, delegationMetadata)
  }

  private def validateDelegationTargetPaths(targetsRole: TargetsRole,
                                            delegatedRoleName: DelegatedRoleName,
                                            delegationMetadata: SignedPayload[TargetsRole])
    : ValidatedNel[String, SignedPayload[TargetsRole]] = {
    val delegationRef = findTrustedDelegationByName(targetsRole, delegatedRoleName)

    // Find invalid targets by running all targetNames (map keys) through the pathPattern regexes in trusted delegations
    val invalidTargets: List[TargetFilename] = delegationMetadata.signed.targets.view
      .filterKeys { target =>
        val matchedPaths = delegationRef.paths.filter { pathPattern =>
          /*
         So Aktualizr uses glob patterns on a regular string (not a file-path) via this function without any flags: https://man7.org/linux/man-pages/man3/fnmatch.3.html
         Here we are using java's path matcher which is different because it assumes we are operating on a path and naturally implies directory boundaries!
         We escape curly brackets {} because aktualizr wont treat them as special characters and we replace * with ** to match 0 or more characters across directory
         boundaries which gets us closer to aktualizr-parity, However, java will treat all other special characters
         as if they were a path, not a string! So unexpected behavior could occur here when using ? and [] among directory boundaries.
         Something like this is really what we need (but it's old and abandoned): https://github.com/morgen-peschke/scala-glob
           */
          FileSystems
            .getDefault()
            .getPathMatcher(s"glob:${pathPattern.value
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("*", "**")}")
            .matches(Paths.get(target.value))
        }
        // if no delegation paths matched targetName, we return true, which designates this targetName as invalid
        matchedPaths.length < 1
      }
      .keys
      .toList

    Right(delegationMetadata)
      .ensure(
        s"Target(s): ${invalidTargets} does not match any registered path patterns of this delegation: ${delegationRef.paths
            .map(_.value)}"
      ) { _ =>
        invalidTargets.length < 1
      }
      .toValidatedNel
  }

}
