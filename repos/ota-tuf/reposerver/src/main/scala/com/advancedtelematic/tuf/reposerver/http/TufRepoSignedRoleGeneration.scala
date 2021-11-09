package com.advancedtelematic.tuf.reposerver.http

import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext


object TufRepoSignedRoleGeneration {
  def apply(keyserverClient: KeyserverClient)(implicit db: Database, ec: ExecutionContext) = {
    new SignedRoleGeneration(keyserverClient, new TufRepoTargetItemsProvider(), new TufRepoSignedRoleProvider())
  }
}
