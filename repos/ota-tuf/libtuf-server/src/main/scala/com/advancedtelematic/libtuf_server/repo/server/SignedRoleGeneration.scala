package com.advancedtelematic.libtuf_server.repo.server

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.http.Errors.MissingEntityId
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType._
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId, RoleType}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import io.circe.Codec
import org.slf4j.LoggerFactory

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}

class SignedRoleGeneration(keyserverClient: KeyserverClient,
                           targetsProvider: TargetsItemsProvider[_],
                           signedRoleProvider: SignedRoleProvider)(implicit ec: ExecutionContext) {

  private val log = LoggerFactory.getLogger(this.getClass)

  def signAllRolesFor(repoId: RepoId, targetsRole: TargetsRole): Future[JsonSignedPayload] = async {
    val signedTarget = await(signRole(repoId, targetsRole))

    val (newSnapshots, newTimestamps) =
      await(freshSignedDependent(repoId, signedTarget, targetsRole.expires))

    await(signedRoleProvider.persistAll(repoId, List(signedTarget, newSnapshots, newTimestamps)))

    signedTarget.content
  }

  def regenerateAllSignedRoles(repoId: RepoId): Future[JsonSignedPayload] = async {
    await(fetchRootRole(repoId))

    val expireAt = await(nextExpire(repoId))

    val targetVersion = await(nextVersion[TargetsRole](repoId))

    val targetDelegations = await(extractDelegationsFromTargetsRole(repoId))

    val targetRole =
      await(genTargetsFromExistingItems(repoId, targetDelegations, expireAt, targetVersion))

    await(signAllRolesFor(repoId, targetRole))
  }

  def regenerateSnapshots(
    repoId: RepoId): Future[(SignedRole[SnapshotRole], SignedRole[TimestampRole])] = async {
    val existingTargets = await(signedRoleProvider.find[TargetsRole](repoId))
    val expireAt = await(nextExpire(repoId))
    val (snapshots, timestamps) = await(freshSignedDependent(repoId, existingTargets, expireAt))
    await(signedRoleProvider.persistAll(repoId, List(snapshots, timestamps)))
    (snapshots, timestamps)
  }

  def freshSignedDependent(
    repoId: RepoId,
    targetRole: SignedRole[TargetsRole],
    expireAt: Instant): Future[(SignedRole[SnapshotRole], SignedRole[TimestampRole])] = async {
    val signedRoot = await(fetchRootRole(repoId))

    val snapshotVersion = await(nextVersion[SnapshotRole](repoId))
    val delegations = await(targetsProvider.findSignedTargetRoleDelegations(repoId, targetRole))
    val snapshotRole =
      genSnapshotRole(signedRoot, targetRole, delegations, expireAt, snapshotVersion)
    val signedSnapshot = await(signRole(repoId, snapshotRole))

    val timestampVersion = await(nextVersion[TimestampRole](repoId))
    val timestampRole = genTimestampRole(signedSnapshot, expireAt, timestampVersion)
    val signedTimestamp = await(signRole(repoId, timestampRole))

    (signedSnapshot, signedTimestamp)
  }

  private def signRole[T: Codec](repoId: RepoId, role: T)(
    implicit tufRole: TufRole[T]): Future[SignedRole[T]] =
    keyserverClient.sign[T](repoId, role).flatMap { signedRole =>
      SignedRole.withChecksum[T](signedRole.asJsonSignedPayload, role.version, role.expires)
    }

  private def fetchRootRole(repoId: RepoId): Future[SignedRole[RootRole]] =
    keyserverClient.fetchRootRole(repoId).flatMap { rootRole =>
      SignedRole.withChecksum[RootRole](
        rootRole.asJsonSignedPayload,
        rootRole.signed.version,
        rootRole.signed.expires
      )
    }

  private def findFreshRole[T](repoId: RepoId, repoRoleRefresh: RepoRoleRefresh)(
    implicit tufRole: TufRole[T]): Future[SignedRole[T]] = {
    val f = for {
      notBefore <- signedRoleProvider.expireNotBefore(repoId)
      existingRole <- signedRoleProvider.find[T](repoId)
      signedRole <-
        if (
          existingRole.expiresAt.isBefore(Instant.now.plus(1, ChronoUnit.HOURS)) ||
          existingRole.expiresAt.isBefore(notBefore.getOrElse(existingRole.expiresAt))
        ) {
          repoRoleRefresh.refreshRole(repoId).recoverWith { case KeyserverClient.RoleKeyNotFound =>
            log.info(
              s"Could not update ${tufRole.roleType} (for $repoId) because the keys are missing, returning expired version"
            )
            FastFuture.successful(existingRole)
          }
        } else
          FastFuture.successful(existingRole)
    } yield signedRole

    f.recoverWith { case _: MissingEntityId[_] =>
      generateAndCacheRole[T](repoId)
    }
  }

  private def generateAndCacheRole[T](repoId: RepoId)(
    implicit tufRole: TufRole[T]): Future[SignedRole[T]] =
    regenerateAllSignedRoles(repoId)
      .recoverWith { case err =>
        log.warn("Could not generate signed roles", err);
        FastFuture.failed(Errors.SignedRoleNotFound(repoId, tufRole.roleType))
      }
      .flatMap(_ => signedRoleProvider.find[T](repoId))

  def findRole[T](repoId: RepoId)(
    implicit tufRole: TufRole[T],
    refresher: RepoRoleRefresh): Future[SignedRole[T]] =
    findRole(repoId, tufRole.roleType, refresher).asInstanceOf[Future[SignedRole[T]]]

  def findRole(repoId: RepoId,
               roleType: RoleType,
               refresher: RepoRoleRefresh): Future[SignedRole[_]] =
    roleType match {
      case RoleType.ROOT =>
        fetchRootRole(repoId)
      case RoleType.TARGETS =>
        findFreshRole[TargetsRole](repoId, refresher)
      case RoleType.SNAPSHOT =>
        findRoleAfterFreshTargets[SnapshotRole](repoId, refresher)
      case RoleType.TIMESTAMP =>
        findRoleAfterFreshTargets[TimestampRole](repoId, refresher)
      case RoleType.REMOTE_SESSIONS =>
        findRoleAfterFreshTargets[RemoteSessionsRole](repoId, refresher)
      case r =>
        throw new IllegalArgumentException(s"Unknown role type $r")
    }

  private def nextVersion[T: TufRole](repoId: RepoId): Future[Int] =
    signedRoleProvider
      .find[T](repoId)
      .map(_.version + 1)
      .recover { case _: MissingEntityId[_] =>
        1
      }

  private def findRoleAfterFreshTargets[T: TufRole](
    repoId: RepoId,
    repoRoleRefresh: RepoRoleRefresh): Future[SignedRole[_]] =
    for {
      _ <- findFreshRole[TargetsRole](
        repoId,
        repoRoleRefresh
      ) // Getting a fresh targets before will ensure the returned snapshots will include the latest
      role <- findFreshRole[T](repoId, repoRoleRefresh)
    } yield role

  private def genSnapshotRole(root: SignedRole[RootRole],
                              target: SignedRole[TargetsRole],
                              delegations: Map[MetaPath, MetaItem],
                              expireAt: Instant,
                              version: Int): SnapshotRole = {
    val meta = List(root.asMetaRole, target.asMetaRole).toMap ++ delegations
    SnapshotRole(meta, expireAt, version)
  }

  private def genTimestampRole(snapshotRole: SignedRole[SnapshotRole],
                               expireAt: Instant,
                               version: Int): TimestampRole = {
    val meta = Map(snapshotRole.asMetaRole)
    TimestampRole(meta, expireAt, version)
  }

  private def nextExpire(repoId: RepoId): Future[Instant] = async {
    val default = Instant.now().plus(31, ChronoUnit.DAYS)
    val notBefore = await(signedRoleProvider.expireNotBefore(repoId)).getOrElse(default)
    List(default, notBefore).max
  }

  private def extractDelegationsFromTargetsRole(repoId: RepoId): Future[Option[Delegations]] =
    signedRoleProvider
      .find[TargetsRole](repoId)
      .map { signedTargetRole =>
        signedTargetRole.role.delegations
      }
      .recover { case _: MissingEntityId[_] =>
        None
      }

  def genTargetsFromExistingItems(repoId: RepoId,
                                  delegations: Option[Delegations] = None): Future[TargetsRole] =
    for {
      nextVersion <- nextVersion[TargetsRole](repoId)
      targetItems <- targetsProvider.findTargets(repoId)
      expireAt <- nextExpire(repoId)
    } yield TargetsRole(
      expireAt,
      targetItems.items,
      nextVersion,
      delegations,
      targetItems.customJson
    )

  def genTargetsFromExistingItems(repoId: RepoId,
                                  delegations: Option[Delegations],
                                  expireAt: Instant,
                                  version: Int): Future[TargetsRole] = for {
    items <- targetsProvider.findTargets(repoId)
  } yield TargetsRole(expireAt, items.items, version, delegations, items.customJson)

  def ensureTargetsCanBeSigned(repoId: RepoId): Future[SignedRole[TargetsRole]] = async {
    val targetsRole = await(signedRoleProvider.find[TargetsRole](repoId)).role
    await(signRole(repoId, targetsRole))
  }

}
