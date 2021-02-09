package com.advancedtelematic.tuf.reposerver.http

import java.time.Instant

import cats.syntax.option._
import cats.syntax.either._
import akka.http.scaladsl.model.Uri
import io.circe.syntax._
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, TargetCustom, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, RoleType, SignedPayload, TargetFilename, TargetFormat, ValidTargetFilename}
import com.advancedtelematic.tuf.reposerver.util._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.DataType.{Checksum, HashMethod, ValidChecksum}
import com.advancedtelematic.tuf.reposerver.db.TargetItemRepositorySupport
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.{StorageMethod, TargetItem}
import io.circe.Json

import scala.concurrent.Future
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}

class OfflineSignedRoleStorageSpec extends TufReposerverSpec with DatabaseSpec with PatienceConfiguration
  with TargetItemRepositorySupport {

  implicit val ec = scala.concurrent.ExecutionContext.global

  override implicit def patienceConfig: PatienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  val mockUri = Uri("https://example.com")

  val defaultCustom = Map(
    "uri" -> mockUri.toString().asJson,
    "name" -> "somename".asJson,
    "version" -> "someversion".asJson,
    "hardwareIds" -> Json.arr(),
    "createdAt" -> Instant.now().asJson,
    "updatedAt" -> Instant.now().asJson
  ).asJson

  val mockChecksum = Checksum(HashMethod.SHA256, "5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03".refineTry[ValidChecksum].get)

  val mockHashes = Map(HashMethod.SHA256 -> mockChecksum.hash)

  val mockFilename = "my/filename".refineTry[ValidTargetFilename].get

  val mockTargetItems: Map[TargetFilename, ClientTargetItem] = Map(
    mockFilename -> ClientTargetItem(mockHashes, 1, Some(defaultCustom))
  )

  val keyserver: KeyserverClient = new FakeKeyserverClient

  val subject = new OfflineSignedRoleStorage(keyserver)

  val signedRoleGeneration = TufRepoSignedRoleGeneration(keyserver)
  val targetRoleGeneration = new TargetRoleEdit(keyserver, signedRoleGeneration)

  def storeOffline(repoId: RepoId, targets: Map[TargetFilename, ClientTargetItem], version: Int): Future[ValidatedNel[String, (Seq[TargetItem], SignedRole[TargetsRole])]] = {
    val targetsRole = TargetsRole(Instant.now.plusSeconds(3600), targets, version)
    val payload = keyserver.sign(repoId, RoleType.TARGETS, targetsRole.asJson).futureValue
    subject.store(repoId, SignedPayload(payload.signatures, targetsRole, targetsRole.asJson))
  }

  keyTypeTest("allows previously online targets to contain empty custom metadata") { keyType =>
    val repoId = RepoId.generate()
    keyserver.createRoot(repoId).futureValue

    targetRoleGeneration.addTargetItem(TargetItem(repoId, mockFilename, mockUri.some, mockChecksum, 22, None, StorageMethod.Managed)).futureValue
    val existingItem = targetItemRepo.findByFilename(repoId, mockFilename).futureValue

    val newTargetItems = Map(mockFilename -> ClientTargetItem(mockHashes, existingItem.length, existingItem.custom.map(_.asJson)))

    storeOffline(repoId, newTargetItems, version = 2).futureValue shouldBe a[Valid[_]]
  }

  keyTypeTest("allows changing of custom data for existing items ") { keyType =>
    val repoId = RepoId.generate()

    keyserver.createRoot(repoId).futureValue

    val newCustom = defaultCustom.as[TargetCustom].valueOr(throw _).copy(targetFormat = TargetFormat.BINARY.some)

    val oldTargetItem = TargetItem(repoId, mockFilename, mockUri.some, mockChecksum, 22, newCustom.some, StorageMethod.Managed)
    targetRoleGeneration.addTargetItem(oldTargetItem).futureValue

    val newTargetItems = Map(mockFilename -> ClientTargetItem(mockHashes, oldTargetItem.length, newCustom.asJson.some))

    storeOffline(repoId, newTargetItems, version = 2).futureValue shouldBe a[Valid[_]]

    targetItemRepo.findByFilename(repoId, mockFilename).futureValue.custom.flatMap(_.targetFormat) should contain(TargetFormat.BINARY)
  }

  keyTypeTest("keeps some attributes of previously existing targets unchanged when storing new offline targets ") { keyType =>
    val repoId = RepoId.generate()

    keyserver.createRoot(repoId).futureValue

    val oldFilename = "my/oldfilename".refineTry[ValidTargetFilename].get

    val oldTargetItem = TargetItem(repoId, oldFilename, mockUri.some, mockChecksum, 22, defaultCustom.as[TargetCustom].toOption, StorageMethod.Managed)
    targetRoleGeneration.addTargetItem(oldTargetItem).futureValue

    val newChecksum = "33a1e103ecb162181620d521915879e68736ea20e4eabe22cc243115d4d43563".refineTry[ValidChecksum].get
    val newHashes = Map(HashMethod.SHA256 -> newChecksum)

    val newTargetItems = mockTargetItems + (oldFilename -> ClientTargetItem(newHashes, 44, oldTargetItem.custom.map(_.asJson)))

    storeOffline(repoId, newTargetItems, version = 2).futureValue shouldBe a[Valid[_]]

    val newTargetItem = targetItemRepo.findByFilename(repoId, oldFilename).futureValue

    newTargetItem.length shouldBe 44
    newTargetItem.checksum.hash shouldBe newChecksum
    newTargetItem.storageMethod shouldBe StorageMethod.Managed
  }

  test("deletes old target items when storing offline targets ") {
    val repoId = RepoId.generate()

    keyserver.createRoot(repoId).futureValue

    storeOffline(repoId, mockTargetItems, version = 1).futureValue shouldBe a[Valid[_]]
    targetItemRepo.findFor(repoId).futureValue shouldNot be(empty)

    storeOffline(repoId, Map.empty, version = 2).futureValue shouldBe a[Valid[_]]
    targetItemRepo.findFor(repoId).futureValue shouldBe empty
  }

}
