package com.advancedtelematic.director.repo

import com.advancedtelematic.director.Settings
import com.advancedtelematic.director.data.DataType.AdminRoleName
import com.advancedtelematic.director.data.DbDataType.SignedPayloadToDbRole
import com.advancedtelematic.director.db.AdminRolesRepository.NotDeleted
import com.advancedtelematic.director.db.AdminRolesRepositorySupport
import com.advancedtelematic.director.http.Errors.TooManyOfflineRoles
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  OfflineSnapshotRole,
  OfflineUpdatesRole,
  TufRole,
  TufRoleOps
}
import com.advancedtelematic.libtuf.data.TufDataType.{
  JsonSignedPayload,
  RepoId,
  RoleType,
  TargetFilename
}
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole.*
import io.circe.Codec

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.async.Async.*

class OfflineUpdates(keyserverClient: KeyserverClient)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends AdminRolesRepositorySupport
    with Settings {

  private val defaultExpire = Duration.ofDays(365)
  // Roles are marked as expired `EXPIRE_AHEAD` before the actual expire date
  private val EXPIRE_AHEAD = defaultExpire.dividedBy(4)

  private val DEFAULT_SNAPSHOTS_NAME = AdminRoleName("offline-snapshots")

  def findUpdates(repoId: RepoId, name: AdminRoleName, version: Int): Future[JsonSignedPayload] =
    findRole[OfflineUpdatesRole](repoId, name, version)

  def findSnapshot(repoId: RepoId, version: Int): Future[JsonSignedPayload] =
    findRole[OfflineSnapshotRole](repoId, DEFAULT_SNAPSHOTS_NAME, version)

  def findLatestUpdates(repoId: RepoId, name: AdminRoleName): Future[JsonSignedPayload] =
    findLatest[OfflineUpdatesRole](repoId, name)

  def findLatestSnapshot(repoId: RepoId): Future[JsonSignedPayload] =
    findLatest[OfflineSnapshotRole](repoId, DEFAULT_SNAPSHOTS_NAME)

  private def findLatest[T: Codec](repoId: RepoId, name: AdminRoleName)(
    implicit tufRole: TufRole[T]): Future[JsonSignedPayload] = async {
    val existing = await(adminRolesRepository.findLatest(repoId, tufRole.roleType, name))

    // Only refreshes expired snapshots, not updates
    if (existing.isExpired(EXPIRE_AHEAD) && tufRole.roleType == RoleType.OFFLINE_SNAPSHOT) {
      val versionedRole = existing.toSignedRole[T]
      val newRole = versionedRole.tufRole.refreshRole(versionedRole.role, _ + 1, nextExpires)

      val signed = await(sign(repoId, newRole))
      await(adminRolesRepository.persistAll(signed.toDbAdminRole(repoId, name)))
      signed.content

    } else
      existing.content
  }

  def delete(repoId: RepoId, offlineUpdatesName: AdminRoleName): Future[Unit] = async {
    val oldSnapshots = await(
      adminRolesRepository.findLatest(repoId, RoleType.OFFLINE_SNAPSHOT, DEFAULT_SNAPSHOTS_NAME)
    )

    val oldSnapshotsRoles = oldSnapshots
      .toSignedRole[OfflineSnapshotRole]
      .role
      .meta

    val newRolesMeta = oldSnapshotsRoles - offlineUpdatesName.asMetaPath

    val newSnapshots =
      OfflineSnapshotRole(newRolesMeta, oldSnapshots.expires, version = oldSnapshots.version + 1)

    val signedSnapshots = await(sign(repoId, newSnapshots))

    await(
      adminRolesRepository.setDeleted(
        repoId,
        RoleType.OFFLINE_UPDATES,
        offlineUpdatesName,
        signedSnapshots.toDbAdminRole(repoId, DEFAULT_SNAPSHOTS_NAME)
      )
    )
  }

  def set(repoId: RepoId,
          offlineUpdatesName: AdminRoleName,
          values: Map[TargetFilename, ClientTargetItem],
          expireAt: Option[Instant]): Future[SignedRole[OfflineUpdatesRole]] = async {
    val sizeOpt = await(
      adminRolesRepository.findLatestOpt(repoId, RoleType.OFFLINE_SNAPSHOT, DEFAULT_SNAPSHOTS_NAME)
    ).flatMap {
      case NotDeleted(role) => Some(role)
      case _                => None
    }.map(_.toSignedRole[OfflineSnapshotRole].role.meta.size)

    if (sizeOpt.getOrElse(0) >= maxOfflineTargets)
      throw TooManyOfflineRoles(maxOfflineTargets)

    val existing = await(
      adminRolesRepository.findLatestOpt(repoId, RoleType.OFFLINE_UPDATES, offlineUpdatesName)
    )

    val expires = expireAt.getOrElse(nextExpires)

    val newRole = existing match {
      case Some(r) =>
        val role = r.value.toSignedRole[OfflineUpdatesRole].role
        role.copy(targets = values, expires = expires, version = role.version + 1)
      case None =>
        await(
          keyserverClient.addOfflineUpdatesRole(repoId)
        ) // If there is no previous updates, create the role first
        OfflineUpdatesRole(values, expires = expires, version = 1)
    }

    await(signAndPersistWithSnapshot(repoId, offlineUpdatesName, newRole, expires))._1
  }

  private def nextExpires = Instant.now().plus(defaultExpire)

  private def findRole[T](repoId: RepoId, name: AdminRoleName, version: Int)(
    implicit tufRole: TufRole[T]): Future[JsonSignedPayload] =
    adminRolesRepository.findByVersion(repoId, tufRole.roleType, name, version).map(_.content)

  private def signAndPersistWithSnapshot(repoId: RepoId,
                                         name: AdminRoleName,
                                         updates: OfflineUpdatesRole,
                                         newExpires: Instant)
    : Future[(SignedRole[OfflineUpdatesRole], SignedRole[OfflineSnapshotRole])] = async {
    val oldSnapshotsO = await(
      adminRolesRepository.findLatestOpt(repoId, RoleType.OFFLINE_SNAPSHOT, DEFAULT_SNAPSHOTS_NAME)
    )
    val nextVersion = oldSnapshotsO.map(_.value.version).getOrElse(0) + 1

    val allAdminRoles = await(adminRolesRepository.findAll(repoId, RoleType.OFFLINE_UPDATES))

    val savedRolesMeta = allAdminRoles.map { adminRole =>
      val (_, metaItem) = adminRole.toSignedRole[OfflineUpdatesRole].asMetaRole
      adminRole.name.asMetaPath -> metaItem
    }.toMap

    val signedUpdates = await(sign(repoId, updates))

    val (_, metaItem) = signedUpdates.asMetaRole
    val newRolesMeta = savedRolesMeta + (name.asMetaPath -> metaItem)

    val expires = (allAdminRoles.map(_.expires) :+ newExpires).max

    val newSnapshots = OfflineSnapshotRole(newRolesMeta, expires, version = nextVersion)
    val signedSnapshots = await(sign(repoId, newSnapshots))

    await(
      adminRolesRepository.persistAll(
        signedUpdates.toDbAdminRole(repoId, name),
        signedSnapshots.toDbAdminRole(repoId, DEFAULT_SNAPSHOTS_NAME)
      )
    )

    (signedUpdates, signedSnapshots)
  }

  private def sign[T: Codec: TufRole](repoId: RepoId, role: T): Future[SignedRole[T]] = async {
    val signedPayload = await(keyserverClient.sign(repoId, role))
    await(SignedRole.withChecksum[T](signedPayload.asJsonSignedPayload, role.version, role.expires))
  }

}
