package com.advancedtelematic.tuf.reposerver.http

import java.time.Instant
import org.scalatest.OptionValues.*

import java.time.temporal.ChronoUnit
import org.apache.pekko.http.scaladsl.model.{StatusCodes, Uri}
import cats.syntax.option.*
import cats.syntax.show.*
import com.advancedtelematic.libats.data
import com.advancedtelematic.libats.data.DataType.HashMethod
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import com.advancedtelematic.libats.data.ErrorCodes.InvalidEntity
import com.advancedtelematic.libats.data.{DataType, ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  DelegatedPathPattern,
  DelegatedRoleName,
  Delegation,
  DelegationClientTargetItem,
  DelegationFriendlyName,
  DelegationInfo,
  SnapshotRole,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.{ClientDataType, TufDataType}
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, RepoId, SignedPayload, TufKey}
import com.advancedtelematic.libtuf.data.ValidatedString.StringToValidatedStringOps
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.AddDelegationFromRemoteRequest
import com.advancedtelematic.tuf.reposerver.util.{
  RepoResourceDelegationsSpecUtil,
  RepoResourceSpecUtil,
  ResourceSpec,
  TufReposerverSpec
}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import eu.timepit.refined.api.Refined
import io.circe.syntax.*
import org.scalactic.source.Position
import io.circe.Json

import java.util.UUID

class RepoResourceDelegationsSpec
    extends TufReposerverSpec
    with ResourceSpec
    with RepoResourceSpecUtil
    with RepoResourceDelegationsSpecUtil {

  private def addNewTrustedDelegationsOk(delegations: Delegation*)(implicit repoId: RepoId): Unit =
    addNewTrustedDelegations(delegations: _*) ~> check {
      status shouldBe StatusCodes.NoContent
    }

  private def addNewRemoteDelegationOk(
    delegatedRoleName: DelegatedRoleName,
    req: AddDelegationFromRemoteRequest)(implicit repoId: RepoId, pos: Position): Unit =
    addNewRemoteDelegation(delegatedRoleName, req) ~> check {
      status shouldBe StatusCodes.Created
    }

  private def getDelegationOk(delegationName: DelegatedRoleName)(
    implicit repoId: RepoId): SignedPayload[TargetsRole] =
    Get(
      apiUri(s"repo/${repoId.show}/delegations/${delegationName.value}.json")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]]
    }

  private def addNewTrustedDelegationKeysOK(tufKeys: TufKey*)(implicit repoId: RepoId): Unit =
    Put(apiUri(s"repo/${repoId.show}/trusted-delegations/keys"), tufKeys.asJson) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

  private def getTrustedDelegationsOk()(implicit repoId: RepoId, pos: Position): List[Delegation] =
    Get(apiUri(s"repo/${repoId.show}/trusted-delegations")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[List[Delegation]]
    }

  private def getTrustedDelegationInfo()(implicit repoId: RepoId): RouteTestResult =
    Get(apiUri(s"repo/${repoId.show}/trusted-delegations/info")) ~> routes

  private def getTrustedDelegationKeys()(implicit repoId: RepoId): RouteTestResult =
    Get(apiUri(s"repo/${repoId.show}/trusted-delegations/keys")) ~> routes

  test("Rejects trusted delegations without reference keys") {
    implicit val repoId = addTargetToRepo()
    addNewTrustedDelegations(delegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidTrustedDelegations
    }
  }

  keyTypeTest("Accepts trusted delegation keys") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeysOK(newKeys.pubkey)
  }

  keyTypeTest("Accepts trusted delegation keys idempotent") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeysOK(newKeys.pubkey)

    addNewTrustedDelegationKeysOK(newKeys.pubkey)
  }

  keyTypeTest("Accepts trusted delegations using trusted keys") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeysOK(newKeys.pubkey)

    // add the trusted delegation referencing the newly trusted key
    addNewTrustedDelegationsOk(delegation.copy(keyids = List(newKeys.pubkey.id)))
  }

  keyTypeTest("Gets trusted delegations") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeysOK(newKeys.pubkey)

    // use the newly trusted keys
    val newTrustedDelegation = delegation.copy(keyids = List(newKeys.pubkey.id))
    addNewTrustedDelegationsOk(newTrustedDelegation)

    getTrustedDelegationsOk() should contain(newTrustedDelegation)
  }

  keyTypeTest("Gets trusted delegation keys") { keyType =>
    implicit val repoId = addTargetToRepo()
    val newKeys = keyType.crypto.generateKeyPair()
    addNewTrustedDelegationKeysOK(newKeys.pubkey)

    getTrustedDelegationKeys() ~> check {
      status shouldBe StatusCodes.OK
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

    addNewTrustedDelegations(delegation.copy(keyids = List(newKeys.pubkey.id))) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidTrustedDelegations
    }
  }

  test("Rejects trusted delegations using invalid delegation name") {
    implicit val repoId = addTargetToRepo()
    val newKeys = Ed25519KeyType.crypto.generateKeyPair()

    addNewTrustedDelegations(
      delegation.copy(name = "badDelegationName?".unsafeApply[DelegatedRoleName])
    ) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe InvalidEntity
      responseAs[ErrorRepresentation].description should include(
        "delegated role name cannot be empty, bigger than 50 characters, or contain any special characters other than"
      )
    }
  }

  test("Rejects targets.json containing delegations that reference unknown keys") {
    implicit val repoId = addTargetToRepo()
    val newKeys = Ed25519KeyType.crypto.generateKeyPair()
    val signedTargets = buildSignedTargetsRoleWithDelegations(
      delegations.copy(roles = List(delegation.copy(keyids = List(newKeys.pubkey.id))))
    )

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

    pushSignedDelegatedMetadataOk(signedDelegation)
  }

  test("accepts overwrite of existing delegated role metadata") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegation)

    pushSignedDelegatedMetadataOk(signedDelegation)
  }

  test("returns delegated role metadata") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Get(
      apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].asJson shouldBe signedDelegationRole.asJson
    }
  }

  test("returns all trusted delegation info") {
    implicit val repoId = addTargetToRepo()
    uploadOfflineSignedTargetsRole()
    val signedDelegationRole = buildSignedDelegatedTargets()
    pushSignedDelegatedMetadataOk(signedDelegationRole)
    getTrustedDelegationInfo() ~> check {
      status shouldBe StatusCodes.OK
      val someMap = responseAs[Map[String, DelegationInfo]]
//      someMap(delegation.name.value) shouldBe DelegationInfo(None, None, None, Some)
      someMap(delegation.name.value).friendlyName shouldBe empty
      someMap(delegation.name.value).lastFetched shouldBe empty
      someMap(delegation.name.value).remoteUri shouldBe empty
      someMap(delegation.name.value).expires should not be empty
    }
  }

  test("rejects delegated metadata when delegation name has invalid characters") {
    implicit val repoId = addTargetToRepo()
    val signedDelegation = buildSignedDelegatedTargets()
    pushSignedDelegatedMetadata(
      signedDelegation,
      "badDelegationName*".unsafeApply[DelegatedRoleName]
    ) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidDelegationName
      responseAs[ErrorRepresentation].cause.getOrElse("").toString should include(
        "delegated role name cannot be empty, bigger than 50 characters, or contain any special characters other than"
      )
    }
  }

  test("rejects delegated metadata when delegation name is too long") {
    implicit val repoId = addTargetToRepo()
    val signedDelegation = buildSignedDelegatedTargets()
    pushSignedDelegatedMetadata(
      signedDelegation,
      ("n" * 51).unsafeApply[DelegatedRoleName]
    ) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidDelegationName
      responseAs[ErrorRepresentation].cause.getOrElse("").toString should include(
        "delegated role name cannot be empty, bigger than 50 characters, or contain any special characters other than"
      )
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

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.PayloadSignatureInvalid
    }
  }

  test(
    "rejects delegated metadata when a target filename doesn't match path specified in delegation refs in targets.json"
  ) {
    implicit val repoId = addTargetToRepo()
    uploadOfflineSignedTargetsRole()
    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("bad-target-filename") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidDelegatedTarget
    }
  }

  test(
    "rejects delegated metadata when a target filename doesn't match nested path specified in delegation refs in targets.json"
  ) {
    implicit val repoId = addTargetToRepo()
    val customDelegationRef =
      delegation.copy(paths = List("*/wicked/*".unsafeApply[DelegatedPathPattern]))
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("someprefix/mypath/wicked-target") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidDelegatedTarget
    }
  }

  test("Accepts delegated metadata with nested delegation filename path glob in targets.json") {
    implicit val repoId = addTargetToRepo()
    val customDelegationRef =
      delegation.copy(paths = List("only-poppin/*".unsafeApply[DelegatedPathPattern]))
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("only-poppin/wicked/lit-targets") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("Accepts delegated metadata with curly brackets in delegation filename path glob") {
    implicit val repoId = addTargetToRepo()
    val customDelegationRef =
      delegation.copy(paths = List("here{we}go/*".unsafeApply[DelegatedPathPattern]))
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("here{we}go/target1.xfile") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("Accepts delegated metadata with question marks in delegation filename path glob") {
    implicit val repoId = addTargetToRepo()
    val customDelegationRef =
      delegation.copy(paths = List("here{we}g?/*".unsafeApply[DelegatedPathPattern]))
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("here{we}go/target1.xfile") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("Accepts delegated metadata with square brackets in delegation filename path glob") {
    implicit val repoId = addTargetToRepo()
    val customDelegationRef =
      delegation.copy(paths = List("here{we}g?/target[0-9].*".unsafeApply[DelegatedPathPattern]))
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("here{we}go/target1.xfile") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("Accepts delegated metadata with multiple delegated paths specified and only one matching") {
    implicit val repoId = addTargetToRepo()
    val customDelegationRef = delegation.copy(paths =
      List(
        "*/wicked/*".unsafeApply[DelegatedPathPattern],
        "*/alpine/*".unsafeApply[DelegatedPathPattern]
      )
    )
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("only-poppin/alpine/lit-targets") -> ClientDataType.ClientTargetItem(
        Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
        0,
        None
      )
    )
    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("does not allow repeated signatures to check threshold") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(delegation.copy(threshold = 2))))

    val default = buildSignedDelegatedTargets()
    val signedDelegation =
      SignedPayload(default.signatures.head +: default.signatures, default.signed, default.json)

    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.PayloadSignatureInvalid
    }
  }

  test("rejects delegated metadata when not properly signed") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val otherKey = Ed25519KeyType.crypto.generateKeyPair()
    val delegationTargets =
      TargetsRole(Instant.now().plus(30, ChronoUnit.DAYS), targets = Map.empty, version = 2)
    val signature =
      TufCrypto.signPayload(otherKey.privkey, delegationTargets.asJson).toClient(otherKey.pubkey.id)
    val signedDelegation =
      SignedPayload(List(signature), delegationTargets, delegationTargets.asJson)

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

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    val delegationRole =
      Get(
        apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json")
      ) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }

    val delegationLength = delegationRole.asJson.canonical.length

    Get(apiUri(s"repo/${repoId.show}/snapshot.json")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signed = responseAs[SignedPayload[SnapshotRole]].signed
      signed
        .meta(Refined.unsafeApply(s"${delegatedRoleName.value}.json"))
        .length shouldBe delegationLength
    }
  }

  test("automatically renewed snapshot still contains delegation") {
    val signedRoleGeneration = TufRepoSignedRoleGeneration(fakeKeyserverClient)

    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    val delegationRole =
      Get(
        apiUri(s"repo/${repoId.show}/delegations/${delegatedRoleName.value}.json")
      ) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }

    val delegationLength = delegationRole.asJson.canonical.length

    val oldSnapshots = signedRoleGeneration.findRole[SnapshotRole](repoId).futureValue

    signedRoleRepository
      .persist[SnapshotRole](
        repoId,
        oldSnapshots.copy(expiresAt = Instant.now().minusSeconds(60)),
        forceVersion = true
      )
      .futureValue

    val renewedSnapshots = signedRoleRepository.find[SnapshotRole](repoId).futureValue
    renewedSnapshots.role
      .meta(Refined.unsafeApply(s"${delegatedRoleName.value}.json"))
      .length shouldBe delegationLength
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

  test("can add delegation using remote url") {
    implicit val repoId = addTargetToRepo()
    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation = buildSignedDelegatedTargets()

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation.asJson)

    val req = AddDelegationFromRemoteRequest(uri)

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    addNewRemoteDelegationOk(delegation.name, req)

    val savedDelegation = getDelegationOk(delegation.name)
    savedDelegation.json shouldBe signedDelegation.json
  }

  test("can overwrite delegation using remote url") {
    implicit val repoId = addTargetToRepo()

    val uri1 = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")
    val uri2 = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation1 = buildSignedDelegatedTargets()
    val signedDelegation2 = buildSignedDelegatedTargets(version = 3)

    fakeRemoteDelegationClient.setRemote(uri1, signedDelegation1.asJson)
    fakeRemoteDelegationClient.setRemote(uri2, signedDelegation2.asJson)

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    val req1 = AddDelegationFromRemoteRequest(uri1)
    addNewRemoteDelegationOk(delegation.name, req1)

    val savedDelegation = getDelegationOk(delegation.name)
    savedDelegation.json shouldBe signedDelegation1.json

    val req2 = AddDelegationFromRemoteRequest(uri2)
    addNewRemoteDelegationOk(delegation.name, req2)
  }

  test("can update delegation using the configured URL") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation1 = buildSignedDelegatedTargets()
    val signedDelegation2 = buildSignedDelegatedTargets(version = 3)

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation1.asJson, Map("myheader" -> "myval2"))

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    val req1 = AddDelegationFromRemoteRequest(uri, Map("myheader" -> "myval2").some)
    addNewRemoteDelegationOk(delegation.name, req1)

    val savedDelegation1 = getDelegationOk(delegation.name)
    savedDelegation1.json shouldBe signedDelegation1.json

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation2.asJson, Map("myheader" -> "myval2"))

    Put(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegation.name.value}/remote/refresh")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val savedDelegation2 = getDelegationOk(delegation.name)
    savedDelegation2.json shouldBe signedDelegation2.json
  }

  test("retrieves when the delegation was last fetched (headers)") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation1 = buildSignedDelegatedTargets()

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation1.asJson)

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    val start = Instant.now

    val req1 = AddDelegationFromRemoteRequest(uri)
    addNewRemoteDelegationOk(delegation.name, req1)

    Get(
      apiUri(s"repo/${repoId.show}/delegations/${delegation.name.value}.json")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[TargetsRole]].json shouldBe signedDelegation1.json

      val tsStr = header("x-ats-delegation-last-fetched-at").value.value()
      val ts = Instant.parse(tsStr)

      ts.isAfter(start) shouldBe true
    }
  }

  test("can specify the friendlyName during remote delegation creation") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation1 = buildSignedDelegatedTargets()

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation1.asJson, Map("myheader" -> "myval2"))

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)
    val friendlyName = "my-friendly-delegation-name".unsafeApply[DelegationFriendlyName]

    val req =
      AddDelegationFromRemoteRequest(uri, Map("myheader" -> "myval2").some, Some(friendlyName))
    addNewRemoteDelegation(delegation.name, req) ~> check {
      status shouldBe StatusCodes.Created
    }

    getDelegationInfo(delegation.name) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[DelegationInfo].friendlyName.getOrElse("") shouldBe friendlyName
    }

    getTrustedDelegationInfo() ~> check {
      status shouldBe StatusCodes.OK
      val respMap = responseAs[Map[String, DelegationInfo]]
      respMap(delegation.name.value).friendlyName.getOrElse("") shouldBe friendlyName
    }
  }

  test("specifying invalid friendlyName during remote delegation creation returns error") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation1 = buildSignedDelegatedTargets()

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation1.asJson, Map("myheader" -> "myval2"))

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)
    val friendlyName = "m" * 105

    val req = AddDelegationFromRemoteRequest(
      uri,
      Map("myheader" -> "myval2").some,
      Option(friendlyName.unsafeApply[DelegationFriendlyName])
    )
    addNewRemoteDelegation(delegation.name, req) ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].description should include(
        "delegation friendly name name cannot be empty or longer than 80 characters"
      )
    }
  }

  test("can set the friendlyName for any existing delegation") {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegation)

    val friendlyName = "my-friendly-delegation-name".unsafeApply[DelegationFriendlyName]
    Patch(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegation.name.value}/info"),
      DelegationInfo(None, None, Some(friendlyName), None).asJson
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    getDelegationInfo(delegation.name) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[DelegationInfo].friendlyName.getOrElse("") shouldBe friendlyName
    }
  }

  test(
    "Attempting to set immutable members of delegationInfo fails gracefully with error response"
  ) {
    implicit val repoId = addTargetToRepo()

    uploadOfflineSignedTargetsRole()

    val signedDelegation = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegation)

    val friendlyName = "my-friendly-delegation-name".unsafeApply[DelegationFriendlyName]
    Patch(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegation.name.value}/info"),
      DelegationInfo(None, Some("http://some-remote-uri"), Some(friendlyName), None).asJson
    ) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.ImmutableFields
    }
  }

  test("can delete a trusted delegation") {
    implicit val repoId = addTargetToRepo()

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    Delete(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegation.name.value}")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val delegations = getTrustedDelegationsOk()
    delegations.map(_.name) shouldBe empty
  }

  test("refresh returns error when there is no remote uri for delegation") {
    implicit val repoId = addTargetToRepo()

    val signedDelegation = buildSignedDelegatedTargets()

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    pushSignedDelegatedMetadataOk(signedDelegation)

    Put(
      apiUri(s"repo/${repoId.show}/trusted-delegations/${delegation.name.value}/remote/refresh")
    ) ~> routes ~> check {
      status shouldBe StatusCodes.PreconditionFailed
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.MissingRemoteDelegationUri
    }
  }

  test("handles remote server 404 errors gracefully") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    val req = AddDelegationFromRemoteRequest(uri)

    addNewRemoteDelegation(delegation.name, req) ~> check {
      status shouldBe StatusCodes.BadGateway
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.DelegationRemoteFetchFailed
    }
  }

  test("handles remote server parsing errors gracefully") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    fakeRemoteDelegationClient.setRemote(uri, Json.obj())

    val req = AddDelegationFromRemoteRequest(uri)

    addNewRemoteDelegation(delegation.name, req) ~> check {
      status shouldBe StatusCodes.BadGateway
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.DelegationRemoteParseFailed
    }
  }

  test("uses supplied headers to fetch remote uri") {
    implicit val repoId = addTargetToRepo()

    val uri = Uri(s"https://test/mydelegation-${UUID.randomUUID()}")

    val signedDelegation = buildSignedDelegatedTargets()

    fakeRemoteDelegationClient.setRemote(uri, signedDelegation.asJson, Map("myheader" -> "myvalue"))

    addNewTrustedDelegationKeysOK(keyPair.pubkey)

    addNewTrustedDelegationsOk(delegation)

    val req = AddDelegationFromRemoteRequest(uri, Map("myheader" -> "myvalue").some)
    addNewRemoteDelegationOk(delegation.name, req)

    val savedDelegation = getDelegationOk(delegation.name)

    savedDelegation.json shouldBe signedDelegation.json
  }

  test("can fetch single delegations_item") {
    // Create package
    implicit val repoId = addTargetToRepo()
    val customDelegationRef = delegation.copy(paths =
      List(
        "*some*".unsafeApply[DelegatedPathPattern],
        "*/alpine/*".unsafeApply[DelegatedPathPattern]
      )
    )
    uploadOfflineSignedTargetsRole(delegations.copy(roles = List(customDelegationRef)))

    val testTargets: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] = Map(
      Refined.unsafeApply("some_hot_target-0.0.1") ->
        ClientDataType.ClientTargetItem(
          Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
          0,
          None
        )
    )

    val signedDelegation = buildSignedDelegatedTargets(targets = testTargets)
    pushSignedDelegatedMetadata(signedDelegation) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    // fetch it
    Get(apiUri(s"repo/${repoId.show}/delegations_items/some_hot_target-0.0.1")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val targetItem = responseAs[List[DelegationClientTargetItem]]
      targetItem.head.targetFilename shouldBe Refined.unsafeApply("some_hot_target-0.0.1")
      targetItem.head.delegatedRoleName shouldBe delegatedRoleName
    }
  }

  test("can fetch all delegations_items when pattern parameter is excluded") {
    implicit val repoId = addTargetToRepo()
    val delegatedRoleName1 = delegatedRoleName
    val delegatedRoleName2 = "bens-second-delegation".unsafeApply[DelegatedRoleName]
    val customDelegationRef1 = delegation.copy(
      name = delegatedRoleName1,
      paths = List("*".unsafeApply[DelegatedPathPattern])
    )
    val customDelegationRef2 = delegation.copy(
      name = delegatedRoleName2,
      paths = List("*".unsafeApply[DelegatedPathPattern])
    )
    uploadOfflineSignedTargetsRole(
      delegations.copy(roles = List(customDelegationRef1, customDelegationRef2))
    )

    val filename1 = "some_hot_target-0.0.1"
    val filename2 = "hot-dogs-Rus-0.0.2"
    val filename3 = "smol-dogs-innovations-90.3.4"
    val filename4 = "true-scoops-ice-cream-334.3.3"
    val testTargets1: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] =
      Map(
        Refined.unsafeApply(filename1) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          ),
        Refined.unsafeApply(filename2) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          )
      )
    val testTargets2: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] =
      Map(
        Refined.unsafeApply(filename3) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          ),
        Refined.unsafeApply(filename4) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          )
      )
    val signedDelegation1 = buildSignedDelegatedTargets(targets = testTargets1)
    val signedDelegation2 = buildSignedDelegatedTargets(targets = testTargets2)
    pushSignedDelegatedMetadata(signedDelegation1, delegatedRoleName1) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    pushSignedDelegatedMetadata(signedDelegation2, delegatedRoleName2) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    // fetch them
    Get(apiUri(s"repo/${repoId.show}/delegations_items")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val delegationClientTargetItems =
        responseAs[PaginationResult[DelegationClientTargetItem]].values
      val itemsTuples =
        delegationClientTargetItems.map(d => d.targetFilename -> d.delegatedRoleName)
      itemsTuples should contain(Refined.unsafeApply(filename1) -> delegatedRoleName1)
      itemsTuples should contain(Refined.unsafeApply(filename2) -> delegatedRoleName1)
      itemsTuples should contain(Refined.unsafeApply(filename3) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename4) -> delegatedRoleName2)
    }
  }

  test("can search delegations_items with pattern and get expected output") {
    implicit val repoId = addTargetToRepo()
    val delegatedRoleName1 = delegatedRoleName
    val delegatedRoleName2 = "bens-second-delegation".unsafeApply[DelegatedRoleName]
    val customDelegationRef1 = delegation.copy(
      name = delegatedRoleName1,
      paths = List("*".unsafeApply[DelegatedPathPattern])
    )
    val customDelegationRef2 = delegation.copy(
      name = delegatedRoleName2,
      paths = List("*".unsafeApply[DelegatedPathPattern])
    )
    uploadOfflineSignedTargetsRole(
      delegations.copy(roles = List(customDelegationRef1, customDelegationRef2))
    )

    val filename1 = "some_hot_target-0.0.1"
    val filename2 = "hot-dogs-Rus-0.0.2"
    val filename3 = "smol-dogs-innovations-90.3.4"
    val filename4 = "true-scoops-ice-cream-334.3.3"
    val testTargets1: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] =
      Map(
        Refined.unsafeApply(filename1) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          ),
        Refined.unsafeApply(filename2) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          )
      )
    val testTargets2: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] =
      Map(
        Refined.unsafeApply(filename3) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          ),
        Refined.unsafeApply(filename4) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          )
      )
    val signedDelegation1 = buildSignedDelegatedTargets(targets = testTargets1)
    val signedDelegation2 = buildSignedDelegatedTargets(targets = testTargets2)
    pushSignedDelegatedMetadata(signedDelegation1, delegatedRoleName1) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    pushSignedDelegatedMetadata(signedDelegation2, delegatedRoleName2) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    // fetch them
    Get(apiUri(s"repo/${repoId.show}/delegations_items?nameContains=dogs")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val delegationClientTargetItems =
        responseAs[PaginationResult[DelegationClientTargetItem]].values
      val itemsTuples =
        delegationClientTargetItems.map(d => d.targetFilename -> d.delegatedRoleName)
      itemsTuples should not contain (Refined.unsafeApply(filename1) -> delegatedRoleName1)
      itemsTuples should contain(Refined.unsafeApply(filename2) -> delegatedRoleName1)
      itemsTuples should contain(Refined.unsafeApply(filename3) -> delegatedRoleName2)
      itemsTuples should not contain (Refined.unsafeApply(filename4) -> delegatedRoleName2)
    }
  }

  // TODO: Re-Enable this when reposerver supports pagination with the delegation_items
  ignore("can restrict delegations_items with pagination parameters") {
    implicit val repoId = addTargetToRepo()
    val delegatedRoleName1 = delegatedRoleName
    val delegatedRoleName2 = "bens-second-delegation".unsafeApply[DelegatedRoleName]
    val customDelegationRef1 = delegation.copy(
      name = delegatedRoleName1,
      paths = List("*".unsafeApply[DelegatedPathPattern])
    )
    val customDelegationRef2 = delegation.copy(
      name = delegatedRoleName2,
      paths = List("*".unsafeApply[DelegatedPathPattern])
    )
    uploadOfflineSignedTargetsRole(
      delegations.copy(roles = List(customDelegationRef1, customDelegationRef2))
    )

    val filename1 = "some_hot_target-0.0.1"
    val filename2 = "hot-dogs-Rus-0.0.2"
    val filename3 = "smol-dogs-innovations-90.3.4"
    val filename4 = "true-scoops-ice-cream-334.3.3"
    val testTargets1: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] =
      Map(
        Refined.unsafeApply(filename1) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          ),
        Refined.unsafeApply(filename2) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          )
      )
    val testTargets2: Map[TufDataType.TargetFilename, ClientDataType.ClientTargetItem] =
      Map(
        Refined.unsafeApply(filename3) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          ),
        Refined.unsafeApply(filename4) ->
          ClientDataType.ClientTargetItem(
            Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
            0,
            None
          )
      )
    val signedDelegation1 = buildSignedDelegatedTargets(targets = testTargets1)
    val signedDelegation2 = buildSignedDelegatedTargets(targets = testTargets2)
    pushSignedDelegatedMetadata(signedDelegation1, delegatedRoleName1) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    pushSignedDelegatedMetadata(signedDelegation2, delegatedRoleName2) ~> check {
      status shouldBe StatusCodes.NoContent
    }
    // fetch them
    Get(apiUri(s"repo/${repoId.show}/delegations_items?offset=1")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val paged = responseAs[PaginationResult[DelegationClientTargetItem]]
      paged.total shouldBe 3
      paged.offset shouldBe 1
      paged.limit shouldBe 50
      val itemsTuples = paged.values.map(d => d.targetFilename -> d.delegatedRoleName)
      val idk = Refined.unsafeApply(filename1)
      itemsTuples should not contain (Refined.unsafeApply(filename3) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename4) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename1) -> delegatedRoleName1)
      itemsTuples should contain(Refined.unsafeApply(filename2) -> delegatedRoleName1)
    }
    Get(apiUri(s"repo/${repoId.show}/delegations_items?offset=2")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val paged = responseAs[PaginationResult[DelegationClientTargetItem]]
      paged.total shouldBe 2
      paged.offset shouldBe 2
      paged.limit shouldBe 50
      val delegationClientTargetItems = paged.values
      val itemsTuples =
        delegationClientTargetItems.map(d => d.targetFilename -> d.delegatedRoleName)
      itemsTuples should not contain (Refined.unsafeApply(filename3) -> delegatedRoleName2)
      itemsTuples should not contain (Refined.unsafeApply(filename4) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename1) -> delegatedRoleName1)
      itemsTuples should contain(Refined.unsafeApply(filename2) -> delegatedRoleName1)
    }
    Get(apiUri(s"repo/${repoId.show}/delegations_items?limit=3")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val paged = responseAs[PaginationResult[DelegationClientTargetItem]]
      paged.total shouldBe 3
      paged.offset shouldBe 0
      paged.limit shouldBe 3
      val delegationClientTargetItems = paged.values
      val itemsTuples =
        delegationClientTargetItems.map(d => d.targetFilename -> d.delegatedRoleName)
      itemsTuples should contain(Refined.unsafeApply(filename3) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename4) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename1) -> delegatedRoleName1)
      itemsTuples should not contain (Refined.unsafeApply(filename2) -> delegatedRoleName1)
    }
    Get(apiUri(s"repo/${repoId.show}/delegations_items?offset=2&limit=3")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val paged = responseAs[PaginationResult[DelegationClientTargetItem]]
      paged.total shouldBe 1
      paged.offset shouldBe 2
      paged.limit shouldBe 3
      val delegationClientTargetItems = paged.values
      val itemsTuples =
        delegationClientTargetItems.map(d => d.targetFilename -> d.delegatedRoleName)
      itemsTuples should not contain (Refined.unsafeApply(filename3) -> delegatedRoleName2)
      itemsTuples should not contain (Refined.unsafeApply(filename4) -> delegatedRoleName2)
      itemsTuples should contain(Refined.unsafeApply(filename1) -> delegatedRoleName1)
      itemsTuples should not contain (Refined.unsafeApply(filename2) -> delegatedRoleName1)
    }
  }

}
