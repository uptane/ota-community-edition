package com.advancedtelematic.tuf.keyserver.roles

import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, _}
import com.advancedtelematic.tuf.keyserver.db._
import com.advancedtelematic.tuf.keyserver.http.Errors
import io.circe.Json
import slick.jdbc.MySQLProfile.api._
import io.circe.syntax._

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}

class RoleSigning()(implicit val db: Database, val ec: ExecutionContext)
  extends SignedRootRoleSupport with KeyRepositorySupport {

  import com.advancedtelematic.libtuf.data.RootManipulationOps._

  def signWithRole(repoId: RepoId, roleType: RoleType, payload: Json): Future[JsonSignedPayload] = async {
    val root = await(signedRootRoleRepo.findLatest(repoId)).content.signed
    val keys = root.roleKeys(roleType)

    if(keys.isEmpty)
      throw Errors.RoleKeysNotFound
    else {
      val signatures = await(signWithKeys(payload, keys))
      JsonSignedPayload(signatures, payload)
    }

  }.recoverWith {
    case KeyRepository.KeyNotFound => Future.failed(Errors.PrivateKeysNotFound)
  }

  protected [roles] def signWithKeys(payload: Json, keys: Seq[TufKey]): Future[Seq[ClientSignature]] = {
    Future.sequence {
      keys.map(signForClient(payload))
    }
  }

  protected [roles] def signForClient(payload: Json)(key: TufKey): Future[ClientSignature] = {
    fetchPrivateKey(key).map { privateKey =>
      val signature = TufCrypto.signPayload(privateKey, payload)
      ClientSignature(key.id, signature.method, signature.sig)
    }
  }

  private def fetchPrivateKey(key: TufKey): Future[TufPrivateKey] =
    keyRepo.find(key.id).recoverWith {
      case KeyRepository.KeyNotFound =>
        FastFuture.failed(Errors.PrivateKeysNotFound)
    }.map(_.privateKey)
}
