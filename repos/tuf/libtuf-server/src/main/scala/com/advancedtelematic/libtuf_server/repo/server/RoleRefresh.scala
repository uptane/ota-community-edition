package com.advancedtelematic.libtuf_server.repo.server

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{MetaItem, MetaPath, SnapshotRole, TargetsRole, TimestampRole, TufRole, _}
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import slick.jdbc.MySQLProfile.api._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}


class RepoRoleRefresh(keyserverClient: KeyserverClient,
                      signedRoleProvider: SignedRoleProvider,
                      targetItemProvider: TargetsItemsProvider[_])(implicit val db: Database, val ec: ExecutionContext) {
  val roleRefresh: RepoId => RoleRefresh = repoId => new RoleRefresh(new RepoRoleSigner(repoId, keyserverClient))

  private def findExisting[T](repoId: RepoId)(implicit tufRole: TufRole[T]): Future[SignedRole[T]] = {
    signedRoleProvider.find[T](repoId)
  }

  private def commitRefresh[T : TufRole](repoId: RepoId, refreshedRole: SignedRole[T], dependencies: List[SignedRole[_]]): Future[SignedRole[T]] = async {
    await(signedRoleProvider.persistAll(repoId, refreshedRole :: dependencies))
    refreshedRole
  }

  def refreshTargets(repoId: RepoId): Future[SignedRole[TargetsRole]] = async {
    val existingTargets = await(findExisting[TargetsRole](repoId))
    val existingSnapshots = await(findExisting[SnapshotRole](repoId))
    val existingTimestamps = await(findExisting[TimestampRole](repoId))
    val existingDelegations = await(targetItemProvider.findSignedTargetRoleDelegations(repoId, existingTargets))
    val (newTargets, dependencies) = await(roleRefresh(repoId).refreshTargets(existingTargets, existingTimestamps, existingSnapshots, existingDelegations))
    await(commitRefresh(repoId, newTargets, dependencies))
  }

  def refreshSnapshots(repoId: RepoId): Future[SignedRole[SnapshotRole]] = async {
    val existingTargets = await(findExisting[TargetsRole](repoId))
    val existingSnapshots = await(findExisting[SnapshotRole](repoId))
    val existingTimestamps = await(findExisting[TimestampRole](repoId))
    val delegations = await(targetItemProvider.findSignedTargetRoleDelegations(repoId, existingTargets))

    val (newSnapshots, dependencies) = await(roleRefresh(repoId).refreshSnapshots(existingSnapshots, existingTimestamps, existingTargets, delegations))
    await(commitRefresh(repoId, newSnapshots, dependencies))
  }

  def refreshTimestamp(repoId: RepoId): Future[SignedRole[TimestampRole]] = async {
    val existingTimestamp = await(findExisting[TimestampRole](repoId))
    val existingSnapshots = await(findExisting[SnapshotRole](repoId))
    val newTimestamp = await(roleRefresh(repoId).refreshTimestamps(existingTimestamp, existingSnapshots))

    await(commitRefresh(repoId, newTimestamp, List.empty))
  }
}

protected class RoleRefresh(signFn: RepoRoleSigner)(implicit ec: ExecutionContext) {

  def refreshTargets(existingTargets: SignedRole[TargetsRole],
                     existingTimestamps: SignedRole[TimestampRole],
                     existingSnapshots: SignedRole[SnapshotRole],
                     existingDelegations: Map[MetaPath, MetaItem]): Future[(SignedRole[TargetsRole], List[SignedRole[_]])] = async {
    val newTargetsRole = refreshRole[TargetsRole](existingTargets)
    val signedTargets = await(signFn(newTargetsRole))
    val (newSnapshots, dependencies) = await(refreshSnapshots(existingSnapshots, existingTimestamps, signedTargets, existingDelegations))

    (signedTargets, newSnapshots :: dependencies)
  }

  def refreshSnapshots(existingSnapshots: SignedRole[SnapshotRole],
                       existingTimestamps: SignedRole[TimestampRole],
                       newTargets: SignedRole[TargetsRole],
                       delegations: Map[MetaPath, MetaItem]): Future[(SignedRole[SnapshotRole], List[SignedRole[_]])] = async {
    val refreshed = refreshRole[SnapshotRole](existingSnapshots)

    val newMeta = existingSnapshots.role.meta + newTargets.asMetaRole ++ delegations
    val newSnapshot = SnapshotRole(newMeta, refreshed.expires, refreshed.version)

    val signedSnapshot = await(signFn(newSnapshot))
    val timestampRole = await(refreshTimestamps(existingTimestamps, signedSnapshot))

    (signedSnapshot, List(timestampRole))
  }

  def refreshTimestamps(existingTimestamps: SignedRole[TimestampRole],
                        newSnapshots: SignedRole[SnapshotRole]): Future[SignedRole[TimestampRole]] = async {
    val refreshed = refreshRole[TimestampRole](existingTimestamps)
    val newTimestamp = TimestampRole(Map(newSnapshots.asMetaRole), refreshed.expires, refreshed.version)
    val signedTimestamps = await(signFn(newTimestamp))
    signedTimestamps
  }

  private def refreshRole[T : Decoder](existing: SignedRole[T])(implicit tufRole: TufRole[T]): T = {
    val nextExpires = Instant.now.plus(1, ChronoUnit.DAYS)
    tufRole.refreshRole(existing.role, _ + 1, nextExpires)
  }
}

protected class RepoRoleSigner(repoId: RepoId, keyserverClient: KeyserverClient)(implicit ec: ExecutionContext) {
  def apply[T : Encoder](role: T)(implicit tufRole: TufRole[T]): Future[SignedRole[T]] = {
    keyserverClient.sign(repoId, tufRole.roleType, role.asJson).map { signedRole =>
      SignedRole.withChecksum[T](repoId, signedRole, role.version, role.expires)
    }
  }
}