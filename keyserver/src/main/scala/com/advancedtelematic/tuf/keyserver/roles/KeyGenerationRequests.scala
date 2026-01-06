package com.advancedtelematic.tuf.keyserver.roles

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import org.apache.pekko.http.scaladsl.util.FastFuture
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{RoleKeys, RootRole}
import com.advancedtelematic.libtuf.data.RootManipulationOps.*
import com.advancedtelematic.libtuf.data.{RoleValidation, RootRoleValidation, TufCodecs}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.*
import com.advancedtelematic.tuf.keyserver.daemon.DefaultKeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus.KeyGenRequestStatus
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.*
import com.advancedtelematic.tuf.keyserver.db.*
import com.advancedtelematic.tuf.keyserver.http.*
import io.circe.Codec
import io.circe.syntax.*
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*

import scala.async.Async.*
import scala.concurrent.{ExecutionContext, Future}

class SignedRootRoles(defaultRoleExpire: Duration = Duration.ofDays(365))(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends KeyRepositorySupport
    with SignedRootRoleSupport {

  private val keyGenerationRequests = new KeyGenerationRequests()
  private val roleSigning = new RoleSigning()

  private val _log = LoggerFactory.getLogger(this.getClass)

  def findByVersion(repoId: RepoId, version: Int): Future[SignedPayload[RootRole]] =
    signedRootRoleRepo.findByVersion(repoId, version).map(_.content)

  def findForSign(repoId: RepoId): Future[RootRole] =
    find(repoId).map(prepareForSign)

  def findLatest(repoId: RepoId): Future[SignedPayload[RootRole]] =
    find(repoId).map(_.content)

  def findFreshAndPersist(
    repoId: RepoId,
    expireNotBefore: Option[Instant] = None): Future[SignedPayload[RootRole]] = async {
    val signedRole = await(findAndPersist(repoId))

    if (
      signedRole.expiresAt.isBefore(
        Instant.now.plus(1, ChronoUnit.HOURS)
      ) || // existing role expires within the hour
      expireNotBefore.exists(signedRole.expiresAt.isBefore)
    ) { // existing role expiration is earlier than expireNotBefore
      val versionedRole = signedRole.content.signed
      val nextVersion = versionedRole.version + 1
      val nextExpires =
        List(
          Option(Instant.now.plus(defaultRoleExpire)),
          expireNotBefore.map(_.plus(defaultRoleExpire))
        ).flatten.max
      val newRole = versionedRole.copy(expires = nextExpires, version = nextVersion)

      val f = signRootRole(newRole)
        .flatMap(persistSignedPayload(repoId))
        .map(_.content)
        .recover { case Errors.PrivateKeysNotFound =>
          _log.info(
            "Could not find private keys to refresh a role, keys are offline, returning an expired role"
          )
          signedRole.content
        }

      await(f)
    } else {
      signedRole.content
    }
  }

  def addRolesIfNotPresent(repoId: RepoId, roles: RoleType*): Future[SignedPayload[RootRole]] =
    async {
      val rootRole = await(findForSign(repoId))

      val missingRoles = roles.toSet -- rootRole.roles.keys.toSet

      if (missingRoles.isEmpty)
        await(find(repoId)).content
      else {
        val rootKeyType = for {
          k <- rootRole.roles.get(RoleType.ROOT)
          kid <- k.keyids.headOption
          key <- rootRole.keys.get(kid)
        } yield key.keytype

        val keyType = rootKeyType.getOrElse(KeyType.default)

        // Same key is used for all roles
        // ERROR is used so the daemon doesn't pick up this request
        val keyGenRequest = await(
          keyGenerationRequests.createRoleGenRequest(
            repoId,
            threshold = 1,
            keyType,
            missingRoles.head,
            KeyGenRequestStatus.ERROR
          )
        )
        val keys = await(DefaultKeyGenerationOp().apply(keyGenRequest))
        val roleKeys = RoleKeys(keys.map(_.id), threshold = 1)
        val newKeys = rootRole.keys ++ keys.map(k => k.id -> k.publicKey).toMap
        val newRoles = rootRole.roles ++ missingRoles.map(_ -> roleKeys).toMap
        val newRoot = rootRole.copy(roles = newRoles, keys = newKeys)

        val signedPayload = await(signRootRole(newRoot))
        await(persistSignedPayload(repoId)(signedPayload))
        signedPayload
      }
    }

  private def persistSignedPayload(repoId: RepoId)(
    signedPayload: SignedPayload[RootRole]): Future[SignedRootRole] = {
    val signedRootRole = signedPayload.toDbSignedRole(repoId)
    signedRootRoleRepo.persist(signedRootRole).map(_ => signedRootRole)
  }

  protected def find(repoId: RepoId): Future[SignedRootRole] =
    signedRootRoleRepo.findLatest(repoId)

  private def findAndPersist(repoId: RepoId): Future[SignedRootRole] =
    find(repoId).recoverWith { case SignedRootRoleRepository.MissingSignedRole =>
      signDefault(repoId).flatMap(persistSignedPayload(repoId))
    }

  def persistUserSigned(
    repoId: RepoId,
    offlinePayload: JsonSignedPayload): Future[ValidatedNel[String, SignedRootRole]] = for {
    oldSignedRoot <- signedRootRoleRepo.findLatest(repoId).map(_.content)
    offlineSignedParsedV = userSignedJsonIsValid(offlinePayload, oldSignedRoot)
    userSignedIsValid <- offlineSignedParsedV match {
      case Valid(offlineSignedParsed) =>
        val newOnlineKeys = offlineSignedParsed.signed.keys.values.map(_.id).toSet
        val signedRootRole = offlineSignedParsed.toDbSignedRole(repoId)
        signedRootRoleRepo
          .persistAndKeepRepoKeys(keyRepo)(signedRootRole, newOnlineKeys)
          .map(_ => Valid(signedRootRole))

      case r @ Invalid(_) =>
        FastFuture.successful(r)
    }
  } yield userSignedIsValid

  private def userSignedJsonIsValid(
    offlinePayload: JsonSignedPayload,
    existingSignedRoot: SignedPayload[RootRole]): ValidatedNel[String, SignedPayload[RootRole]] =
    RoleValidation.rawJsonIsValid[RootRole](offlinePayload).andThen { offlineSignedParsed =>
      RootRoleValidation.newRootIsValid(offlineSignedParsed, existingSignedRoot)
    }

  private def signDefault(repoId: RepoId): Future[SignedPayload[RootRole]] =
    createDefault(repoId).flatMap(signRootRole)

  private def signRootRole(role: RootRole): Future[SignedPayload[RootRole]] = async {
    val keys = role.roleKeys(RoleType.ROOT)
    val payloadJson = role.asJson
    val signatures = await(roleSigning.signWithKeys(payloadJson, keys))
    SignedPayload(signatures, role, payloadJson)
  }

  private def prepareForSign(signedRootRole: SignedRootRole): RootRole =
    signedRootRole.content.signed
      .copy(expires = Instant.now.plus(defaultRoleExpire), version = signedRootRole.version + 1)

  private def ensureReadyForGenerate(repoId: RepoId): Future[Unit] =
    keyRepo.repoKeys(repoId).flatMap {
      case keys if keys.exists(_.roleType == RoleType.ROOT) => FastFuture.successful(())
      case _ => FastFuture.failed(Errors.RepoRootKeysNotFound)
    }

  private def createDefault(repoId: RepoId): Future[RootRole] = async {
    val keyGenRequests = await(keyGenerationRequests.readyKeyGenRequests(repoId))
    await(ensureReadyForGenerate(repoId))

    val repoKeys = await(keyRepo.repoKeys(repoId)).toSet

    val clientKeys = repoKeys.map(key => key.id -> key.publicKey).toMap

    val roleTypeToKeyIds = repoKeys.groupBy(_.roleType).view.mapValues(_.map(_.id).toSeq)

    val roles = keyGenRequests.map { genRequest =>
      genRequest.roleType -> RoleKeys(roleTypeToKeyIds(genRequest.roleType), genRequest.threshold)
    }.toMap

    assert(clientKeys.nonEmpty, "no keys for new default root")
    assert(roles.nonEmpty, "no roles for new default root")

    RootRole(clientKeys, roles, expires = Instant.now.plus(defaultRoleExpire), version = 1)
  }

}

// TODO: `Key` and `KeyGenRequest` should not have RoleType or threshold
// Because one key can be used for multiple roles and is independent of threshold
// Remove those attributes, drop the db columns, and move that logic to root generation
class KeyGenerationRequests()(implicit val db: Database, val ec: ExecutionContext)
    extends KeyGenRequestSupport
    with KeyRepositorySupport {

  private val DEFAULT_ROLES = RoleType.TUF_ALL

  def createDefaultGenRequest(repoId: RepoId,
                              threshold: Int,
                              keyType: KeyType,
                              initStatus: KeyGenRequestStatus): Future[Seq[KeyGenRequest]] = {
    val reqs = DEFAULT_ROLES.map { roleType =>
      KeyGenRequest(
        KeyGenId.generate(),
        repoId,
        initStatus,
        roleType,
        keyType.crypto.defaultKeySize,
        keyType,
        threshold
      )
    }

    keyGenRepo.persistAll(reqs)
  }

  def createRoleGenRequest(repoId: RepoId,
                           threshold: Int,
                           keyType: KeyType,
                           roleType: RoleType,
                           initStatus: KeyGenRequestStatus): Future[KeyGenRequest] = {
    val kgr = KeyGenRequest(
      KeyGenId.generate(),
      repoId,
      initStatus,
      roleType,
      keyType.crypto.defaultKeySize,
      keyType,
      threshold
    )
    keyGenRepo.persist(kgr)
  }

  def forceRetry(repoId: RepoId): Future[Seq[KeyGenId]] =
    keyGenRepo
      .findBy(repoId)
      .map { genRequests =>
        genRequests.filter(_.status == KeyGenRequestStatus.ERROR).map(_.id)
      }
      .flatMap { genIds =>
        keyGenRepo.setStatusAll(genIds, KeyGenRequestStatus.REQUESTED)
      }

  def readyKeyGenRequests(repoId: RepoId): Future[Seq[KeyGenRequest]] =
    keyGenRepo.findBy(repoId).flatMap { keyGenReqs =>
      if (keyGenReqs.isEmpty)
        FastFuture.failed(KeyRepository.KeyNotFound)
      else if (keyGenReqs.exists(_.status == KeyGenRequestStatus.ERROR)) {
        val errors = keyGenReqs.foldLeft(Map.empty[KeyGenId, String]) { (errors, req) =>
          if (req.status == KeyGenRequestStatus.ERROR)
            errors + (req.id -> req.description)
          else
            errors
        }
        FastFuture.failed(Errors.KeyGenerationFailed(repoId, errors))
      } else if (keyGenReqs.exists(_.status != KeyGenRequestStatus.GENERATED))
        FastFuture.failed(Errors.KeysNotReady)
      else
        FastFuture.successful(keyGenReqs)
    }

}
