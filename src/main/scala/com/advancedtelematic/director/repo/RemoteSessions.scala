package com.advancedtelematic.director.repo

import org.apache.pekko.http.scaladsl.util.FastFuture
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.advancedtelematic.director.data.DataType.AdminRoleName
import com.advancedtelematic.director.data.DbDataType.SignedPayloadToDbRole
import com.advancedtelematic.director.db.AdminRolesRepositorySupport
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  RemoteCommandsPayload,
  RemoteSessionsPayload,
  RemoteSessionsRole,
  TufRole,
  TufRoleOps
}
import com.advancedtelematic.libtuf.data.RoleValidation
import com.advancedtelematic.libtuf.data.TufDataType.{
  JsonSignedPayload,
  RepoId,
  RoleType,
  SignedPayload
}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import io.circe.Codec
import slick.jdbc.MySQLProfile.api.*

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

class RemoteSessions(keyserverClient: KeyserverClient)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends AdminRolesRepositorySupport {
  import scala.async.Async.*

  private val defaultExpire = Duration.ofDays(365)
  // Roles are marked as expired `EXPIRE_AHEAD` before the actual expire date
  private val EXPIRE_AHEAD = defaultExpire.dividedBy(4)

  private def nextExpires = Instant.now().plus(defaultExpire)

  private val REMOTE_SESSIONS_ADMIN_NAME = AdminRoleName("remote-sessions")

  def find(repoId: RepoId): Future[JsonSignedPayload] = async {
    val existing = await(
      adminRolesRepository.findLatest(repoId, RoleType.REMOTE_SESSIONS, REMOTE_SESSIONS_ADMIN_NAME)
    )

    // Only refreshes expired snapshots, not updates
    if (existing.isExpired(EXPIRE_AHEAD)) {
      val versionedRole = existing.toSignedRole[RemoteSessionsRole]
      val newRole = versionedRole.tufRole.refreshRole(versionedRole.role, _ + 1, nextExpires)

      val signed = await(sign(repoId, newRole))
      await(
        adminRolesRepository.persistAll(signed.toDbAdminRole(repoId, REMOTE_SESSIONS_ADMIN_NAME))
      )
      signed.content
    } else
      existing.content
  }

  def setRemoteCommands(
    repoId: RepoId,
    remoteCommands: RemoteCommandsPayload): Future[SignedRole[RemoteSessionsRole]] =
    set(repoId) {
      case Some(rs) =>
        rs.copy(remote_commands = Some(remoteCommands))
      case None =>
        RemoteSessionsRole (
          remote_sessions = RemoteSessionsPayload.empty,
          remote_commands = Some(remoteCommands),
          version = 1,
          expires = nextExpires
        )
    }

  def setRemoteSessions(
    repoId: RepoId,
    remoteSessions: RemoteSessionsPayload): Future[SignedRole[RemoteSessionsRole]] =
    set(repoId) {
      case Some(rs) =>
        rs.copy(remote_sessions = remoteSessions)
      case None =>
        RemoteSessionsRole(
          remote_sessions = remoteSessions,
          None,
          version = 1,
          expires = nextExpires
        )
    }

  private def set(repoId: RepoId)(updateFn: Option[RemoteSessionsRole] => RemoteSessionsRole)
    : Future[SignedRole[RemoteSessionsRole]] =
    async {
      val existing = await(
        adminRolesRepository
          .findLatestOpt(repoId, RoleType.REMOTE_SESSIONS, REMOTE_SESSIONS_ADMIN_NAME)
      )

      val newRole = existing match {
        case Some(r) =>
          val role = r.value.toSignedRole[RemoteSessionsRole].role
          val newRole = updateFn(Some(role)).copy(
            expires = nextExpires,
            version = role.version + 1
          ) // persist will check this bump is valid and does not conflict
          newRole
        case None =>
          await(keyserverClient.addRemoteSessionsRole(repoId))
          updateFn(None)
      }

      val signedRole = await(sign(repoId, newRole))
      await(
        adminRolesRepository
          .persistAll(signedRole.toDbAdminRole(repoId, REMOTE_SESSIONS_ADMIN_NAME))
      )
      signedRole
    }

  private def sign[T: Codec: TufRole](repoId: RepoId, role: T): Future[SignedRole[T]] = async {
    val signedPayload = await(keyserverClient.sign(repoId, role))
    await(SignedRole.withChecksum[T](signedPayload.asJsonSignedPayload, role.version, role.expires))
  }

  private def validateSignedPayload(
    repoId: RepoId,
    payload: JsonSignedPayload): Future[ValidatedNel[String, SignedPayload[RemoteSessionsRole]]] =
    for {
      rootRole <- keyserverClient.fetchRootRole(repoId).map(_.signed)
      userSignedValid = RoleValidation.rawJsonIsValid[RemoteSessionsRole](payload).andThen {
        parsedRole =>
          RoleValidation.roleIsValid(parsedRole, rootRole)
      }
    } yield userSignedValid

  def updateFullRole(repoId: RepoId, payload: JsonSignedPayload): Future[Unit] =
    validateSignedPayload(repoId, payload).flatMap {
      case Valid(signedPayload) =>
        SignedRole
          .withChecksum[RemoteSessionsRole](
            signedPayload.asJsonSignedPayload,
            signedPayload.signed.version,
            signedPayload.signed.expires
          )
          .flatMap { signedRole =>
            adminRolesRepository.persistAll(
              signedRole.toDbAdminRole(repoId, REMOTE_SESSIONS_ADMIN_NAME)
            )
          }
      case Invalid(errors) =>
        FastFuture.failed(Errors.InvalidSignedPayload(repoId, errors))
    }

}
