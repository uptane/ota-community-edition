package com.advancedtelematic.tuf.reposerver.http

import java.time.Instant
import java.time.temporal.ChronoUnit
import akka.http.scaladsl.model.StatusCodes
import cats.syntax.option._
import cats.syntax.show._
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedPathPattern._
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedPathPattern, DelegatedRoleName, Delegation, Delegations, SnapshotRole, TargetsRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, JsonSignedPayload, RepoId, RoleType, SignedPayload, TufKeyPair, TufKey}
import com.advancedtelematic.libtuf.data.ValidatedString._
import com.advancedtelematic.libtuf_server.repo.server.RepoRoleRefresh
import com.advancedtelematic.tuf.reposerver.util.{RepoResourceSpecUtil, ResourceSpec, TufReposerverSpec}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import eu.timepit.refined.api.Refined
import io.circe.syntax._
import org.scalactic.source.Position

import scala.concurrent.Future
import scala.util.{Success, Failure}

class RepoResourceDelegationsSpec extends TufReposerverSpec
  with ResourceSpec
  with RepoResourceSpecUtil {

  lazy val keyPair = Ed25519KeyType.crypto.generateKeyPair()

  val delegatedRoleName = "mydelegation".unsafeApply[DelegatedRoleName]

  val delegation = {
    val delegationPath = "mypath/*".unsafeApply[DelegatedPathPattern]
    Delegation(delegatedRoleName, List(keyPair.pubkey.id), List(delegationPath))
  }

  implicit val roleRefresh = new RepoRoleRefresh(fakeKeyserverClient, new TufRepoSignedRoleProvider(), new TufRepoTargetItemsProvider())

  val delegations = Delegations(Map(keyPair.pubkey.id -> keyPair.pubkey), List(delegation))

  private def uploadOfflineSignedTargetsRole(_delegations: Delegations = delegations)
                                  (implicit repoId: RepoId, pos: Position): Unit = {
    val signedTargets = buildSignedTargetsRoleWithDelegations(_delegations)(repoId, pos)
    Put(apiUri(s"repo/${repoId.show}/targets"), signedTargets).withValidTargetsCheckSum ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  private def buildDelegations(pubKey: TufKey, roleName: String = "mydelegation", pattern: String="mypath/*"): Delegations = {
    val delegation = {
      val delegationPath = pattern.unsafeApply[DelegatedPathPattern]
      val delegatedRoleName = roleName.unsafeApply[DelegatedRoleName]
      Delegation(delegatedRoleName, List(pubKey.id), List(delegationPath))
    }
    Delegations(Map(pubKey.id -> pubKey), List(delegation))
  }

  private def buildSignedTargetsRoleWithDelegations(_delegations: Delegations = delegations)
                                                   (implicit repoId: RepoId, pos: Position): Future[JsonSignedPayload] = {
    val oldTargets = buildSignedTargetsRole(repoId, Map.empty)
    val newTargets = oldTargets.signed.copy(delegations = _delegations.some)

    fakeKeyserverClient.sign(repoId, newTargets).map(_.asJsonSignedPayload)
  }

  private def buildSignedDelegatedTargets(delegatedKeyPair: TufKeyPair = keyPair)
                                         (implicit repoId: RepoId, pos: Position): SignedPayload[TargetsRole] = {
    val delegationTargets = TargetsRole(Instant.now().plus(30, ChronoUnit.DAYS), targets = Map.empty, version = 2)
    val signature = TufCrypto.signPayload(delegatedKeyPair.privkey, delegationTargets.asJson).toClient(delegatedKeyPair.pubkey.id)
    SignedPayload(List(signature), delegationTargets, delegationTargets.asJson)
  }

  private def pushSignedDelegatedMetadata(signedPayload: SignedPayload[TargetsRole])
                                         (implicit repoId: RepoId): RouteTestResult = {
    Put(apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json"), signedPayload) ~> routes
  }

  private def pushSignedTargetsMetadata(signedPayload: JsonSignedPayload)
                                        (implicit repoId: RepoId): RouteTestResult = {
    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload).withValidTargetsCheckSum ~> routes
  }

  private def addNewTrustedDelegations(delegations: List[Delegation])
                                      (implicit repoId: RepoId): RouteTestResult = {
    Put(apiUri(s"repo/${repoId.show}/trusted-delegations"), delegations.asJson) ~> routes
  }

  private def addNewTrustedDelegationKeys(tufKeys: List[TufKey])
                                         (implicit repoId: RepoId): RouteTestResult = {
    Put(apiUri(s"repo/${repoId.show}/trusted-delegations/keys"), tufKeys.asJson) ~> routes
  }

  private def getTrustedDelegations()(implicit repoId: RepoId): RouteTestResult = {
    Get(apiUri(s"repo/${repoId.show}/trusted-delegations")) ~> routes
  }

  private def getTrustedDelegationKeys()(implicit repoId: RepoId): RouteTestResult = {
    Get(apiUri(s"repo/${repoId.show}/trusted-delegations/keys")) ~> routes
  }

  test("Rejects trusted delegations without reference keys") {
    implicit val repoId = addTargetToRepo()
    addNewTrustedDelegations(List(delegation)) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidTrustedDelegations
    }
  }

  keyTypeTest("Accepts trusted delegation keys") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeys(List(newKeys.pubkey)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  keyTypeTest("Accepts trusted delegation keys idempotent") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeys(List(newKeys.pubkey)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    addNewTrustedDelegationKeys(List(newKeys.pubkey)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  keyTypeTest("Accepts trusted delegations using trusted keys") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeys(List(newKeys.pubkey)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    // add the trusted delegation referencing the newly trusted key
    addNewTrustedDelegations(List(delegation.copy(keyids = List(newKeys.pubkey.id)))) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  keyTypeTest("Gets trusted delegations") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeys(List(newKeys.pubkey)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    // use the newly trusted keys
    val newTrustedDelegation = delegation.copy(keyids = List(newKeys.pubkey.id))
    addNewTrustedDelegations(List(newTrustedDelegation)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    getTrustedDelegations() ~> check {
      status shouldBe StatusCodes.OK
      println(response)
      responseAs[List[Delegation]] should contain(newTrustedDelegation)
    }
  }

  keyTypeTest("Gets trusted delegation keys") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeys(List(newKeys.pubkey)) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    getTrustedDelegationKeys() ~> check {
      status shouldBe StatusCodes.OK
      println(response)
      responseAs[List[TufKey]] should contain(newKeys.pubkey)
    }
  }

  keyTypeTest("Replaces trusted delegation keys when offline targets uploaded") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    val newDelegationsBlock = buildDelegations(newKeys.pubkey)
    uploadOfflineSignedTargetsRole(newDelegationsBlock)
    getTrustedDelegationKeys() ~> check {
      status shouldBe StatusCodes.OK
      // only items in published delegations should be present
      responseAs[List[TufKey]] shouldBe newDelegationsBlock.keys.values.toList
    }
  }

  test("Rejects trusted delegations using unknown keys") {
    implicit val repoId = addTargetToRepo()
    val newKeys = Ed25519KeyType.crypto.generateKeyPair()

    addNewTrustedDelegations(List(delegation.copy(keyids=List(newKeys.pubkey.id)))) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidTrustedDelegations
    }
  }

  test("Rejects targets.json containing delegations that reference unknown keys") {
    implicit val repoId = addTargetToRepo()
    val newKeys = Ed25519KeyType.crypto.generateKeyPair()
    val signedTargets = buildSignedTargetsRoleWithDelegations(delegations.copy(roles = List(delegation.copy(keyids = List(newKeys.pubkey.id)))))

    val targetsRole = signedTargets.futureValue
    pushSignedTargetsMetadata(targetsRole) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidOfflineTargets
    }
  }

  test("accepts delegated targets") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    Get(apiUri(s"repo/${repoId.show}/targets.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].signed.delegations should contain(delegations)
    }
  }

  test("accepts delegated role metadata when signed with known keys") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("accepts overwrite of existing delegated role metadata") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("returns delegated role metadata") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata(signedDelegationRole) ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].asJson shouldBe signedDelegationRole.asJson
    }
  }

  test("rejects delegated metadata when not defined in targets.json") {
    implicit val repoId = addTargetToRepo()

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe Errors.DelegationNotDefined.code
    }
  }

  test("rejects delegated metadata when not signed according to threshold") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(delegation.copy(threshold = 2))))

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata( signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.PayloadSignatureInvalid
    }
  }

  test("does not allow repeated signatures to check threshold") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(delegation.copy(threshold = 2))))

    val default = buildSignedDelegatedTargets()
    val signedDelegation = SignedPayload(default.signatures.head +: default.signatures, default.signed, default.json)

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.PayloadSignatureInvalid
    }
  }

  test("rejects delegated metadata when not properly signed") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val otherKey = Ed25519KeyType.crypto.generateKeyPair()
    val delegationTargets = TargetsRole(Instant.now().plus(30, ChronoUnit.DAYS), targets = Map.empty, version = 2)
    val signature = TufCrypto.signPayload(otherKey.privkey, delegationTargets.asJson).toClient(otherKey.pubkey.id)
    val signedDelegation = SignedPayload(List(signature), delegationTargets, delegationTargets.asJson)

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.PayloadSignatureInvalid
    }
  }

  test("re-generates snapshot role after storing delegations") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    Get(apiUri(s"repo/${repoId.show}/snapshot.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[SnapshotRole]].signed.version shouldBe 2
    }
  }

  test("SnapshotRole includes signed delegation length") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata(signedDelegationRole) ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val delegationRole =
      Get(apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json")) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }

    val delegationLength = delegationRole.asJson.canonical.length

    Get(apiUri(s"repo/${repoId.show}/snapshot.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signed = responseAs[SignedPayload[SnapshotRole]].signed
      signed.meta(Refined.unsafeApply(s"${delegatedRoleName.value}.json")).length shouldBe delegationLength
    }
  }

  test("automatically renewed snapshot still contains delegation") {
    val signedRoleGeneration = TufRepoSignedRoleGeneration(fakeKeyserverClient)

    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadata(signedDelegationRole) ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val delegationRole =
      Get(apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json")) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }

    val delegationLength = delegationRole.asJson.canonical.length

    val oldSnapshots = signedRoleGeneration.findRole[SnapshotRole](repoId).futureValue

    signedRoleRepository.persist[SnapshotRole](repoId, oldSnapshots.copy(expiresAt = Instant.now().minusSeconds(60)), forceVersion = true).futureValue

    val renewedSnapshots = signedRoleRepository.find[SnapshotRole](repoId).futureValue
    renewedSnapshots.role.meta(Refined.unsafeApply(s"${delegatedRoleName.value}.json")).length shouldBe delegationLength
  }

  test("Adding a single target keeps delegations") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    Post(apiUri(s"repo/${repoId.show}/targets/myfile"), testFile) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/targets.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].signed.delegations should contain(delegations)
    }
  }
}
