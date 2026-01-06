package com.advancedtelematic.libtuf_server.repo.server

import org.apache.pekko.http.scaladsl.util.FastFuture

import java.time.Instant
import java.time.temporal.ChronoUnit
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{
  MetaItem,
  MetaPath,
  SnapshotRole,
  TargetsRole,
  TimestampRole,
  TufRole,
  _
}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, RoleType}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import io.circe.{Codec, Decoder}
import slick.jdbc.MySQLProfile.api._

import scala.annotation.unused
import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class RepoRoleRefresh(keyserverClient: KeyserverClient,
                      signedRoleProvider: SignedRoleProvider,
                      targetItemProvider: TargetsItemsProvider[_])(
  implicit val db: Database,
  val ec: ExecutionContext) {

  private val roleRefresh: RepoId => RoleRefresh = repoId =>
    new RoleRefresh(new RepoRoleSigner(repoId, keyserverClient))

  private def findExisting[T](repoId: RepoId)(implicit tufRole: TufRole[T]): Future[SignedRole[T]] =
    signedRoleProvider.find[T](repoId)

  private def commitRefresh[T](repoId: RepoId,
                               refreshedRole: SignedRole[T],
                               dependencies: List[SignedRole[_]])(
    implicit @unused _tf: TufRole[T]): Future[SignedRole[T]] = async {
    await(signedRoleProvider.persistAll(repoId, refreshedRole :: dependencies))
    refreshedRole
  }

  private def nextExpires(repoId: RepoId): Future[Instant] = async {
    val default = Instant.now().plus(30, ChronoUnit.DAYS)
    val notBefore = await(signedRoleProvider.expireNotBefore(repoId)).getOrElse(default)
    List(default, notBefore).max
  }

  def refreshRole[T](repoId: RepoId)(implicit tufRole: TufRole[T]): Future[SignedRole[T]] = {
    def ensureSafeDowncast[U](role: SignedRole[U]): Future[SignedRole[T]] =
      if (tufRole != role.tufRole)
        FastFuture.failed(
          new IllegalArgumentException(
            s"Error refreshing role, Cannot cast ${role.tufRole} to ${tufRole}"
          )
        )
      else
        FastFuture.successful(role.asInstanceOf[SignedRole[T]])

    tufRole.roleType match {
      case RoleType.TARGETS   => refreshTargets(repoId).flatMap(r => ensureSafeDowncast(r))
      case RoleType.SNAPSHOT  => refreshSnapshots(repoId).flatMap(r => ensureSafeDowncast(r))
      case RoleType.TIMESTAMP => refreshTimestamp(repoId).flatMap(r => ensureSafeDowncast(r))
      case t                  =>
        FastFuture.failed(new IllegalArgumentException("Cannot refresh repo role of type " + t))
    }
  }

  private def refreshTargets(repoId: RepoId): Future[SignedRole[TargetsRole]] = async {
    val existingTargets = await(findExisting[TargetsRole](repoId))
    val existingSnapshots = await(findExisting[SnapshotRole](repoId))
    val existingTimestamps = await(findExisting[TimestampRole](repoId))
    val expiresAt = await(nextExpires(repoId))
    val existingDelegations =
      await(targetItemProvider.findSignedTargetRoleDelegations(repoId, existingTargets))
    val (newTargets, dependencies) = await(
      roleRefresh(repoId).refreshTargets(
        existingTargets,
        existingTimestamps,
        existingSnapshots,
        existingDelegations,
        expiresAt
      )
    )
    await(commitRefresh(repoId, newTargets, dependencies))
  }

  private def refreshSnapshots(repoId: RepoId): Future[SignedRole[SnapshotRole]] = async {
    val existingTargets = await(findExisting[TargetsRole](repoId))
    val existingSnapshots = await(findExisting[SnapshotRole](repoId))
    val existingTimestamps = await(findExisting[TimestampRole](repoId))
    val delegations =
      await(targetItemProvider.findSignedTargetRoleDelegations(repoId, existingTargets))
    val expiresAt = await(nextExpires(repoId))
    val (newSnapshots, dependencies) = await(
      roleRefresh(repoId).refreshSnapshots(
        existingSnapshots,
        existingTimestamps,
        existingTargets,
        delegations,
        expiresAt
      )
    )
    await(commitRefresh(repoId, newSnapshots, dependencies))
  }

  private def refreshTimestamp(repoId: RepoId): Future[SignedRole[TimestampRole]] = async {
    val existingTimestamp = await(findExisting[TimestampRole](repoId))
    val existingSnapshots = await(findExisting[SnapshotRole](repoId))
    val newTimestamp =
      await(roleRefresh(repoId).refreshTimestamps(existingTimestamp, existingSnapshots))

    await(commitRefresh(repoId, newTimestamp, List.empty))
  }

}

protected class RoleRefresh(signFn: RepoRoleSigner)(implicit ec: ExecutionContext) {

  def refreshTargets(existingTargets: SignedRole[TargetsRole],
                     existingTimestamps: SignedRole[TimestampRole],
                     existingSnapshots: SignedRole[SnapshotRole],
                     existingDelegations: Map[MetaPath, MetaItem],
                     expiresAt: Instant): Future[(SignedRole[TargetsRole], List[SignedRole[_]])] =
    async {
      val newTargetsRole = refreshRole[TargetsRole](existingTargets, expiresAt)
      val signedTargets = await(signFn(newTargetsRole))
      val (newSnapshots, dependencies) = await(
        refreshSnapshots(
          existingSnapshots,
          existingTimestamps,
          signedTargets,
          existingDelegations,
          expiresAt
        )
      )

      (signedTargets, newSnapshots :: dependencies)
    }

  def refreshSnapshots(
    existingSnapshots: SignedRole[SnapshotRole],
    existingTimestamps: SignedRole[TimestampRole],
    newTargets: SignedRole[TargetsRole],
    delegations: Map[MetaPath, MetaItem],
    expiresAt: Instant): Future[(SignedRole[SnapshotRole], List[SignedRole[_]])] = async {
    val refreshed = refreshRole[SnapshotRole](existingSnapshots, expiresAt)

    val newMeta = existingSnapshots.role.meta + newTargets.asMetaRole ++ delegations
    val newSnapshot = SnapshotRole(newMeta, expiresAt, refreshed.version)

    val signedSnapshot = await(signFn(newSnapshot))
    val timestampRole = await(refreshTimestamps(existingTimestamps, signedSnapshot))

    (signedSnapshot, List(timestampRole))
  }

  def refreshTimestamps(existingTimestamps: SignedRole[TimestampRole],
                        newSnapshots: SignedRole[SnapshotRole]): Future[SignedRole[TimestampRole]] =
    async {
      val expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
      val refreshed = refreshRole[TimestampRole](existingTimestamps, expiresAt)
      val newTimestamp =
        TimestampRole(Map(newSnapshots.asMetaRole), refreshed.expires, refreshed.version)
      val signedTimestamps = await(signFn(newTimestamp))
      signedTimestamps
    }

  private def refreshRole[T: Decoder](existing: SignedRole[T], expiresAt: Instant)(
    implicit tufRole: TufRole[T]): T =
    tufRole.refreshRole(existing.role, _ + 1, expiresAt)

}

protected class RepoRoleSigner(repoId: RepoId, keyserverClient: KeyserverClient)(
  implicit ec: ExecutionContext) {

  def apply[T: Codec](role: T)(implicit tufRole: TufRole[T]): Future[SignedRole[T]] =
    keyserverClient.sign[T](repoId, role).flatMap { signedRole =>
      SignedRole.withChecksum[T](signedRole.asJsonSignedPayload, role.version, role.expires)
    }

}
