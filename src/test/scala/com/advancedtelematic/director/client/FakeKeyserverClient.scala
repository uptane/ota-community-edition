package com.advancedtelematic.director.client

import io.circe.Codec

import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{RoleKeys, RootRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{
  KeyId,
  KeyType,
  RepoId,
  RoleType,
  SignedPayload,
  TufKeyPair
}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient.{
  KeyPairNotFound,
  RoleKeyNotFound
}
import io.circe.Json

import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters._
import scala.concurrent.Future
import scala.util.Try
import scala.async.Async.*

class FakeKeyserverClient extends KeyserverClient {

  import io.circe.syntax._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val keys = new ConcurrentHashMap[RepoId, Map[RoleType, TufKeyPair]]()

  private val rootRoles = new ConcurrentHashMap[RepoId, RootRole]()

  def publicKey(repoId: RepoId, roleType: RoleType): PublicKey =
    keys.get(repoId)(roleType).pubkey.keyval

  private def addKey(repoId: RepoId, role: RoleType, keyPair: TufKeyPair): Unit =
    keys.compute(
      repoId,
      (_: RepoId, u: Map[RoleType, TufKeyPair]) =>
        if (u == null)
          Map(role -> keyPair)
        else
          u + (role -> keyPair)
    )

  private def generateRoot(repoId: RepoId, keyType: KeyType): RootRole = {
    RoleType.TUF_ALL.foreach { role =>
      val keyPair = keyType.crypto.generateKeyPair()
      addKey(repoId, role, keyPair)
    }

    val roles = keys.get(repoId).map { case (role, keyPair) =>
      role -> RoleKeys(List(keyPair.pubkey.id), threshold = 1)
    }

    val clientKeys = keys.get(repoId).map { case (_, keyPair) =>
      keyPair.pubkey.id -> keyPair.pubkey
    }

    RootRole(clientKeys, roles, expires = Instant.now.plusSeconds(3600), version = 1)
  }

  override def createRoot(repoId: RepoId, keyType: KeyType, forceSync: Boolean): Future[Json] =
    if (keys.contains(repoId)) {
      FastFuture.failed(KeyserverClient.RootRoleConflict)
    } else {
      val rootRole = generateRoot(repoId, keyType)
      rootRoles.put(repoId, rootRole)
      FastFuture.successful(rootRole.asJson)
    }

  def deleteRepo(repoId: RepoId): Option[RootRole] =
    Option(keys.remove(repoId)).flatMap(_ => Option(rootRoles.remove(repoId)))

  override def sign[T: Codec](repoId: RepoId, payload: T)(
    implicit tufRole: TufRole[T]): Future[SignedPayload[T]] = {
    val key = Option(keys.get(repoId))
      .flatMap(_.get(tufRole.roleType))
      .getOrElse(throw KeyserverClient.RoleKeyNotFound)
    val signature = TufCrypto.signPayload(key.privkey, payload.asJson).toClient(key.pubkey.id)
    FastFuture.successful(SignedPayload(List(signature), payload, payload.asJson))
  }

  override def fetchRootRole(repoId: RepoId,
                             _expireNotBefore: Option[Instant]): Future[SignedPayload[RootRole]] =
    FastFuture {
      Try {
        rootRoles.asScala(repoId)
      }.recover { case _: NoSuchElementException =>
        throw KeyserverClient.RootRoleNotFound
      }
    }.flatMap { role =>
      sign(repoId, role).map { jsonSigned =>
        SignedPayload(jsonSigned.signatures, role, jsonSigned.json)
      }
    }

  override def fetchUnsignedRoot(repoId: RepoId): Future[RootRole] =
    fetchRootRole(repoId).map(_.signed)

  override def updateRoot(repoId: RepoId, signedPayload: SignedPayload[RootRole]): Future[Unit] =
    FastFuture.successful {
      rootRoles.computeIfPresent(
        repoId,
        (_: RepoId, u: RootRole) => {
          assert(u != null, "fake keyserver, Role does not exist")
          signedPayload.signed
        }
      )
    }

  override def deletePrivateKey(repoId: RepoId, keyId: KeyId): Future[Unit] =
    FastFuture.successful {
      keys.computeIfPresent(
        repoId,
        (_: RepoId, existingKeys: Map[RoleType, TufKeyPair]) =>
          existingKeys.filter(_._2.pubkey.id != keyId)
      )
    }

  override def fetchTargetKeyPairs(repoId: RepoId): Future[Seq[TufKeyPair]] =
    FastFuture.successful {
      val keyPair = keys.asScala
        .getOrElse(repoId, throw RoleKeyNotFound)
        .getOrElse(RoleType.TARGETS, throw RoleKeyNotFound)
      Seq(keyPair)
    }

  override def fetchRootRole(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]] =
    fetchRootRole(repoId).filter(_.signed.version == version)

  override def fetchKeyPair(repoId: RepoId, keyId: KeyId): Future[TufKeyPair] = FastFuture {
    Try {
      keys.asScala
        .getOrElse(repoId, throw KeyPairNotFound)
        .values
        .find(_.pubkey.id == keyId)
        .getOrElse(throw KeyPairNotFound)
    }
  }

  def fetchKeypairByKeyId(keyId: KeyId): Option[TufKeyPair] =
    keys.asScala.values.flatMap(_.values).find(_.pubkey.id == keyId)

  override def addOfflineUpdatesRole(repoId: RepoId): Future[Unit] =
    addRoles(repoId, RoleType.OFFLINE_UPDATES, RoleType.OFFLINE_SNAPSHOT)

  private def addRoles(repoId: RepoId, roles: RoleType*): Future[Unit] = async {
    val rootRole = await(fetchUnsignedRoot(repoId))

    val rootKeyType = for {
      roleKeys <- rootRole.roles.get(RoleType.ROOT)
      keyId <- roleKeys.keyids.headOption
      tufKey <- rootRole.keys.get(keyId)
    } yield tufKey.keytype

    val keyType = rootKeyType.getOrElse(KeyType.default)

    val keyPair = keyType.crypto.generateKeyPair()

    val keys = roles.map(role => role -> keyPair)

    keys.foreach { case (role, key) =>
      addKey(repoId, role, key)
    }

    val roleKeys = keys.map { case (role, key) =>
      role -> RoleKeys(Seq(key.pubkey.id), 1)
    }.toMap

    val newRoles = rootRole.roles ++ roleKeys

    val newKeys = rootRole.keys + (keyPair.pubkey.id -> keyPair.pubkey)

    val newRootRole = RootRole(
      roles = newRoles,
      keys = newKeys,
      version = rootRole.version + 1,
      expires = rootRole.expires.plus(1, ChronoUnit.DAYS)
    )
    rootRoles.put(repoId, newRootRole)
  }

  override def addRemoteSessionsRole(repoId: RepoId): Future[Unit] =
    addRoles(repoId, RoleType.REMOTE_SESSIONS)

  override def rotateRoot(repoId: RepoId): Future[Unit] =
    FastFuture.failed(new IllegalArgumentException("[test] not implemented"))

}
