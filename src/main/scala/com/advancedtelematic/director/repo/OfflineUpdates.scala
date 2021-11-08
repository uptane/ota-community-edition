package com.advancedtelematic.director.repo

import com.advancedtelematic.director.data.DataType.AdminRoleName
import com.advancedtelematic.director.data.DbDataType.SignedPayloadToDbRole
import com.advancedtelematic.director.db.DbOfflineUpdatesRepositorySupportSupport
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, OfflineSnapshotRole, OfflineUpdatesRole, TufRole, TufRoleOps}
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId, RoleType, TargetFilename, TargetName}
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import io.circe.syntax._
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole._
import io.circe.Codec

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.async.Async._

class OfflineUpdates(keyserverClient: KeyserverClient)(implicit val db: Database, val ec: ExecutionContext) extends DbOfflineUpdatesRepositorySupportSupport {

  private val defaultExpire = Duration.ofDays(365)
  // Roles are marked as expired `EXPIRE_AHEAD` before the actual expire date
  private val EXPIRE_AHEAD =  defaultExpire.dividedBy(4)

  private val DEFAULT_SNAPSHOTS_NAME = AdminRoleName("offline-snapshots")

  def findUpdates(repoId: RepoId, name: AdminRoleName, version: Int): Future[JsonSignedPayload] =
    findRole[OfflineUpdatesRole](repoId, name, version)

  def findSnapshot(repoId: RepoId, version: Int): Future[JsonSignedPayload] =
    findRole[OfflineSnapshotRole](repoId, DEFAULT_SNAPSHOTS_NAME, version)

  def findLatestUpdates(repoId: RepoId, name: AdminRoleName): Future[JsonSignedPayload] =
    findLatest[OfflineUpdatesRole](repoId, name)

  def findLatestSnapshot(repoId: RepoId): Future[JsonSignedPayload] =
    findLatest[OfflineSnapshotRole](repoId, DEFAULT_SNAPSHOTS_NAME)

  private def findLatest[T : Codec](repoId: RepoId, name: AdminRoleName)(implicit tufRole: TufRole[T]): Future[JsonSignedPayload] = async {
    val existing = await(dbAdminRolesRepository.findLatest(repoId, tufRole.roleType, name))

    // Only refreshes expired snapshots, not updates
    if (existing.isExpired(EXPIRE_AHEAD) && tufRole.roleType == RoleType.OFFLINE_SNAPSHOT) {
      val versionedRole = existing.toSignedRole[T]
      val newRole = versionedRole.tufRole.refreshRole(versionedRole.role, _ + 1, nextExpires)

      val signed = await(sign(repoId, newRole))
      await(dbAdminRolesRepository.persistAll(signed.toDbAdminRole(repoId, name)))
      signed.content

    } else
      existing.content
  }

  def set(repoId: RepoId, offlineUpdatesName: AdminRoleName, values: Map[TargetFilename, ClientTargetItem]): Future[Unit] = async {
    val existing = await(dbAdminRolesRepository.findLatestOpt(repoId, RoleType.OFFLINE_UPDATES, offlineUpdatesName))

    val newRole = if(existing.isEmpty) {
      await(keyserverClient.addOfflineUpdatesRole(repoId)) // If there is no previous updates, create the role first
      OfflineUpdatesRole(values, expires = nextExpires, version = 1)
    } else {
      val role = existing.get.toSignedRole[OfflineUpdatesRole].role
      val newRole = role.copy(targets = values, expires = nextExpires, version = role.version + 1)
      newRole
    }

    await(signAndPersistWithSnapshot(repoId, offlineUpdatesName, newRole))
  }

  private def nextExpires = Instant.now().plus(defaultExpire)

  private def findRole[T : Codec](repoId: RepoId, name: AdminRoleName, version: Int)(implicit tufRole: TufRole[T]): Future[JsonSignedPayload] =
    dbAdminRolesRepository.findByVersion(repoId, tufRole.roleType, name, version).map(_.content)

  private def signAndPersistWithSnapshot(repoId: RepoId, name: AdminRoleName, updates: OfflineUpdatesRole): Future[(SignedRole[OfflineUpdatesRole], SignedRole[OfflineSnapshotRole])] = async {
    val oldSnapshotsO = await(dbAdminRolesRepository.findLatestOpt(repoId, RoleType.OFFLINE_SNAPSHOT, DEFAULT_SNAPSHOTS_NAME))
    val nextVersion = oldSnapshotsO.map(_.version).getOrElse(0) + 1

    val savedRolesMeta = await(dbAdminRolesRepository.findAll(repoId, RoleType.OFFLINE_UPDATES)).map { adminRole =>
      val (_, metaItem) = adminRole.toSignedRole[OfflineUpdatesRole].asMetaRole
      adminRole.name.asMetaPath -> metaItem
    }.toMap

    val signedUpdates = await(sign(repoId, updates))

    val (_, metaItem) = signedUpdates.asMetaRole
    val newRolesMeta = savedRolesMeta + (name.asMetaPath -> metaItem)

    val newSnapshots = OfflineSnapshotRole(newRolesMeta, nextExpires, version = nextVersion)
    val signedSnapshots = await(sign(repoId, newSnapshots))

    await(dbAdminRolesRepository.persistAll(signedUpdates.toDbAdminRole(repoId, name), signedSnapshots.toDbAdminRole(repoId, DEFAULT_SNAPSHOTS_NAME)))

    (signedUpdates, signedSnapshots)
  }

  private def sign[T : Codec : TufRole](repoId: RepoId, role: T): Future[SignedRole[T]] = async {
    val signedPayload = await(keyserverClient.sign(repoId, role))
    await(SignedRole.withChecksum[T](signedPayload.asJsonSignedPayload, role.version, role.expires))
  }
}
