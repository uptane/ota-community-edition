package com.advancedtelematic.tuf.reposerver.util

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.apache.pekko.http.scaladsl.model.{StatusCodes, Uri}
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.libats.data.DataType.{HashMethod, ValidChecksum}
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.DelegatedPathPattern.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  DelegatedPathPattern,
  DelegatedRoleName,
  Delegation,
  Delegations,
  SnapshotRole,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.{ClientDataType, TufDataType}
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  JsonSignedPayload,
  RepoId,
  SignedPayload,
  TufKey,
  TufKeyPair
}
import com.advancedtelematic.libtuf.data.ValidatedString.*
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.repo.server.RepoRoleRefresh
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.AddDelegationFromRemoteRequest
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.syntax.*
import org.scalactic.source.Position

import scala.concurrent.Future
import com.advancedtelematic.tuf.reposerver.data.RepoCodecs.*
import com.advancedtelematic.tuf.reposerver.http.{
  TufRepoSignedRoleProvider,
  TufRepoTargetItemsProvider
}
import eu.timepit.refined.api.Refined
import com.advancedtelematic.libtuf_server.data.Marshalling.*
import io.circe.Json

trait RepoResourceDelegationsSpecUtil extends RepoResourceSpecUtil {
  lazy val keyPair = Ed25519KeyType.crypto.generateKeyPair()

  val delegatedRoleName = "my-delegation.test.ok_".unsafeApply[DelegatedRoleName]

  val delegation = {
    val delegationPath = "mypath/*".unsafeApply[DelegatedPathPattern]
    Delegation(delegatedRoleName, List(keyPair.pubkey.id), List(delegationPath))
  }

  implicit val roleRefresh: RepoRoleRefresh = new RepoRoleRefresh(
    fakeKeyserverClient,
    new TufRepoSignedRoleProvider(),
    new TufRepoTargetItemsProvider()
  )

  val delegations = Delegations(Map(keyPair.pubkey.id -> keyPair.pubkey), List(delegation))

  val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
    Refined.unsafeApply("mypath/mytargetName") -> ClientTargetItem(
      Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
      2,
      Json
        .obj(
          "name" -> "mytargetName".asJson,
          "version" -> "0.0.2".asJson,
          "hardwareIds" -> List("delegated-hardware-id-001").asJson
        )
        .some
    )
  )

  def uploadOfflineSignedTargetsRole(
    _delegations: Delegations = delegations)(implicit repoId: RepoId, pos: Position): Unit = {
    val signedTargets = buildSignedTargetsRoleWithDelegations(_delegations)(repoId, pos)
    Put(
      apiUri(s"repo/${repoId.show}/targets"),
      signedTargets
    ).withValidTargetsCheckSum ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  def buildDelegations(pubKey: TufKey,
                       roleName: String = "mydelegation",
                       pattern: String = "mypath/*"): Delegations = {
    val delegation = {
      val delegationPath = pattern.unsafeApply[DelegatedPathPattern]
      val delegatedRoleName = roleName.unsafeApply[DelegatedRoleName]
      Delegation(delegatedRoleName, List(pubKey.id), List(delegationPath))
    }
    Delegations(Map(pubKey.id -> pubKey), List(delegation))
  }

  def buildSignedTargetsRoleWithDelegations(_delegations: Delegations = delegations)(
    implicit repoId: RepoId,
    pos: Position): Future[JsonSignedPayload] = {
    val oldTargets = buildSignedTargetsRole(repoId, Map.empty)
    val newTargets = oldTargets.signed.copy(delegations = _delegations.some)

    fakeKeyserverClient.sign(repoId, newTargets).map(_.asJsonSignedPayload)
  }

  def buildSignedDelegatedTargets(
    delegatedKeyPair: TufKeyPair = keyPair,
    version: Int = 2,
    targets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = testTargets)(
    implicit repoId: RepoId,
    pos: Position): SignedPayload[TargetsRole] = {
    val delegationTargets =
      TargetsRole(Instant.now().plus(30, ChronoUnit.DAYS), targets = targets, version = version)
    val signature = TufCrypto
      .signPayload(delegatedKeyPair.privkey, delegationTargets.asJson)
      .toClient(delegatedKeyPair.pubkey.id)
    SignedPayload(List(signature), delegationTargets, delegationTargets.asJson)
  }

  def pushSignedDelegatedMetadata(signedPayload: SignedPayload[TargetsRole],
                                  roleName: DelegatedRoleName = delegatedRoleName)(
    implicit repoId: RepoId): RouteTestResult =
    Put(apiUri(s"repo/${repoId.show}/delegations/${roleName.value}.json"), signedPayload) ~> routes

  def pushSignedDelegatedMetadataOk(signedPayload: SignedPayload[TargetsRole])(
    implicit repoId: RepoId): Unit =
    pushSignedDelegatedMetadata(signedPayload) ~> check {
      status shouldBe StatusCodes.NoContent
    }

  def pushSignedTargetsMetadata(signedPayload: JsonSignedPayload)(
    implicit repoId: RepoId): RouteTestResult =
    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload).withValidTargetsCheckSum ~> routes

  def addNewTrustedDelegations(delegations: Delegation*)(implicit repoId: RepoId): RouteTestResult =
    Put(apiUri(s"repo/${repoId.show}/trusted-delegations"), delegations.asJson) ~> routes

  def addNewRemoteDelegation(
    delegatedRoleName: DelegatedRoleName,
    req: AddDelegationFromRemoteRequest)(implicit repoId: RepoId, pos: Position): RouteTestResult =
    Put(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegatedRoleName.value}/remote"),
      req.asJson
    ) ~> routes

  def getDelegationInfo(delegatedRoleName: DelegatedRoleName)(
    implicit repoId: RepoId): RouteTestResult =
    Get(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegatedRoleName.value}/info")
    ) ~> routes

}
