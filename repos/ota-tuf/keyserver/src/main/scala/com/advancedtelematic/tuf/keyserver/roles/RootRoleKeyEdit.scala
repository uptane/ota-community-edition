package com.advancedtelematic.tuf.keyserver.roles

import org.apache.pekko.http.scaladsl.util.FastFuture
import cats.implicits._
import com.advancedtelematic.tuf.keyserver.db.KeyRepositorySupport
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.RootManipulationOps._
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, KeyType, RepoId, RoleType, TufKeyPair}
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.SignedPayloadDbOps
import com.advancedtelematic.tuf.keyserver.db.KeyRepository.KeyNotFound
import com.advancedtelematic.tuf.keyserver.db.{KeyRepositorySupport, SignedRootRoleSupport}
import com.advancedtelematic.tuf.keyserver.http.Errors
import slick.jdbc.MySQLProfile.api._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

class RootRoleKeyEdit()(implicit val db: Database, val ec: ExecutionContext)
    extends KeyRepositorySupport
    with SignedRootRoleSupport {
  val roleSigning = new RoleSigning()
  val signedRootRole = new SignedRootRoles()

  def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit] = for {
    _ <- ensureIsRepoKey(repoId, keyId)
    _ <- keyRepo.delete(keyId)
  } yield ()

  def rotate(repoId: RepoId): Future[Unit] = async {
    val unsigned = await(signedRootRole.findForSign(repoId))
    var newRoot = unsigned

    val newRootKeys =
      List.fill(newRoot.roles.get(RoleType.ROOT).map(_.keyids).toList.flatten.size) {
        TufCrypto.generateKeyPair(KeyType.default, KeyType.default.crypto.defaultKeySize)
      }

    val newTargetsKeys =
      List.fill(newRoot.roles.get(RoleType.TARGETS).map(_.keyids).toList.flatten.size) {
        TufCrypto.generateKeyPair(KeyType.default, KeyType.default.crypto.defaultKeySize)
      }

    if (newTargetsKeys.nonEmpty) {
      newRoot = newRoot.withRoleKeys(RoleType.TARGETS, newTargetsKeys.map(_.pubkey): _*)
    }

    if (newRootKeys.nonEmpty) {
      newRoot = newRoot.withRoleKeys(RoleType.ROOT, newRootKeys.map(_.pubkey): _*)
    }

    val oldPrivateKeys = await {
      keyRepo
        .findAll(unsigned.roleKeys(RoleType.ROOT).map(_.id))
        .recoverWith { case KeyNotFound =>
          FastFuture.failed(Errors.KeysOffline)
        }
    }

    val oldKeyPairs = oldPrivateKeys.map { k =>
      k.toTufKeyPair.toEither.valueOr(_ => throw Errors.KeysReadError)
    }

    val signedPayload = roleSigning.signWithPrivateKeys(newRoot, newRootKeys ++ oldKeyPairs)

    val newKeys = Map(RoleType.ROOT -> newRootKeys, RoleType.TARGETS -> newTargetsKeys)

    await(
      signedRootRoleRepo.persistWithKeys(keyRepo)(signedPayload.toDbSignedRole(repoId), newKeys)
    )
  }

  def findAllKeyPairs(repoId: RepoId, roleType: RoleType): Future[Seq[TufKeyPair]] =
    for {
      rootRole <- signedRootRole.findLatest(repoId)
      targetKeyIds = rootRole.signed.roleKeys(roleType).map(_.id)
      dbKeys <- keyRepo.findAll(targetKeyIds)
      keyPairsT = dbKeys.map(_.toTufKeyPair)
      keyPairs <- Future.fromTry(keyPairsT.toList.sequence)
    } yield keyPairs

  def findKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair] =
    for {
      _ <- ensureIsRepoKey(repoId, keyId)
      key <- keyRepo.find(keyId)
      keyPair <- Future.fromTry(key.toTufKeyPair)
    } yield keyPair

  private def ensureIsRepoKey(repoId: RepoId, keyId: KeyId): Future[KeyId] = async {
    val publicKey = await(keyRepo.repoKeys(repoId)).find(_.id == keyId)
    publicKey.map(_.id).getOrElse(throw KeyNotFound)
  }

}
