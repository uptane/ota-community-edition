package com.advancedtelematic.tuf.reposerver.db

import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.libtuf_server.data.TufSlickMappings._
import DBDataType._
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole

import scala.concurrent.Future

object SignedRoleDbTestUtil {
  implicit class SignedRoleRepositoryTestOps(value: SignedRoleRepository) {
    def update[T: TufRole](repoId: RepoId, signedRole: SignedRole[T]): Future[Int] =
      value.db.run {
        Schema.signedRoles
          .filter(_.repoId === repoId)
          .filter(_.roleType === signedRole.tufRole.roleType)
          .update(signedRole.asDbSignedRole(repoId))
      }
  }
}
