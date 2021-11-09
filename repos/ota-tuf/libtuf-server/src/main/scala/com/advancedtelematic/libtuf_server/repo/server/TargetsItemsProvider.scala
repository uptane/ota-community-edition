package com.advancedtelematic.libtuf_server.repo.server

import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, MetaItem, MetaPath, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, TargetFilename}
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.libtuf_server.repo.server.TargetsItemsProvider.TargetItems
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import scala.concurrent.Future

object TargetsItemsProvider {
  case class TargetItems[T : Encoder : Decoder](items: Map[TargetFilename, ClientTargetItem], custom: Option[T] = None) {
    def customJson: Option[Json] = custom.map(_.asJson)
  }
}

abstract class TargetsItemsProvider[T : Encoder : Decoder] {
  def findSignedTargetRoleDelegations(repoId: RepoId, signedRole: SignedRole[TargetsRole]): Future[Map[MetaPath, MetaItem]]

  def findTargets(repoId: RepoId): Future[TargetItems[T]]
}
