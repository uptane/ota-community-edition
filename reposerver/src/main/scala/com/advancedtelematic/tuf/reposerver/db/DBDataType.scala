package com.advancedtelematic.tuf.reposerver.db

import org.apache.pekko.http.scaladsl.model.Uri

import java.time.Instant
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.ClientDataType.{
  DelegatedRoleName,
  DelegationFriendlyName,
  TufRole
}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId}
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole

protected[db] object DBDataType {

  protected[db] case class DbSignedRole(repoId: RepoId,
                                        roleType: RoleType,
                                        content: JsonSignedPayload,
                                        checksum: Checksum,
                                        length: Long,
                                        version: Int,
                                        expireAt: Instant)

  protected[db] case class DbDelegation(repoId: RepoId,
                                        roleName: DelegatedRoleName,
                                        content: JsonSignedPayload,
                                        remoteUri: Option[Uri],
                                        lastFetched: Option[Instant],
                                        remoteHeaders: Map[String, String],
                                        friendlyName: Option[DelegationFriendlyName])

  protected[db] implicit class DbSignedRoleOps(value: DbSignedRole) {

    def asSignedRole[T: TufRole]: SignedRole[T] =
      SignedRole[T](value.content, value.checksum, value.length, value.version, value.expireAt)

  }

  protected[db] implicit class SignedRoleAsDbSignedRoleOps(value: SignedRole[_]) {

    def asDbSignedRole(repoId: RepoId): DbSignedRole =
      DbSignedRole(
        repoId,
        value.tufRole.roleType,
        value.content,
        value.checksum,
        value.length,
        value.version,
        value.expiresAt
      )

  }

}
