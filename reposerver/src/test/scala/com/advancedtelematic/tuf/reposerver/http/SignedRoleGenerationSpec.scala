package com.advancedtelematic.tuf.reposerver.http

import java.time.Instant

import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{TimestampRole, _}
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, RepoId, RoleType}
import com.advancedtelematic.libtuf_server.repo.server.RepoRoleRefresh
import com.advancedtelematic.tuf.reposerver.db.SignedRoleRepositorySupport
import com.advancedtelematic.tuf.reposerver.util.{FakeKeyserverClient, TufReposerverSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits


// TODO: Should be moved to libtuf_server and tested without TufRepoProviders
class SignedRoleGenerationSpec extends TufReposerverSpec with DatabaseSpec with SignedRoleRepositorySupport with ScalaFutures {
  override implicit val ec: ExecutionContext = Implicits.global

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  val fakeKeyserverClient: FakeKeyserverClient = new FakeKeyserverClient

  val signedRoleGeneration = TufRepoSignedRoleGeneration(fakeKeyserverClient)
  implicit val roleRefresh = new RepoRoleRefresh(fakeKeyserverClient, new TufRepoSignedRoleProvider(), new TufRepoTargetItemsProvider())

  def setupRepo(): Future[RepoId] = for {
    repoId <- FastFuture.successful(RepoId.generate())
    _ <- fakeKeyserverClient.createRoot(repoId, Ed25519KeyType)
    _ <- signedRoleGeneration.regenerateAllSignedRoles(repoId)
  } yield repoId

  test("renewal of snapshots renews also timestamps") {
    val repoId = setupRepo().futureValue

    val oldTimestamps = signedRoleGeneration.findRole[TimestampRole](repoId).futureValue
    val oldSnapshots = signedRoleGeneration.findRole[SnapshotRole](repoId).futureValue

    signedRoleRepository.persist[SnapshotRole](repoId, oldSnapshots.copy(expiresAt = Instant.now().minusSeconds(60)), forceVersion = true).futureValue

    val renewedSnapshots = signedRoleGeneration.findRole[SnapshotRole](repoId).futureValue
    val renewedTimestampsDb = signedRoleRepository.find[TimestampRole](repoId).futureValue
    val (_, renewedSnapshotsHash) = renewedTimestampsDb.role.meta(RoleType.SNAPSHOT.metaPath).hashes.head

    renewedSnapshots.checksum shouldNot be(oldSnapshots.checksum)
    renewedSnapshots.version shouldBe oldSnapshots.version + 1

    renewedTimestampsDb.version shouldBe oldTimestamps.version + 1
    renewedSnapshotsHash shouldBe renewedSnapshots.checksum.hash
  }

  test("renewal of targets renews also snapshots and timestamps") {
    val repoId = setupRepo().futureValue

    val oldTargets = signedRoleGeneration.findRole[TargetsRole](repoId).futureValue
    val oldSnapshots = signedRoleGeneration.findRole[SnapshotRole](repoId).futureValue
    val oldTimestamps = signedRoleGeneration.findRole[TimestampRole](repoId).futureValue

    signedRoleRepository.persist[TargetsRole](repoId, oldTargets.copy(expiresAt = Instant.now().minusSeconds(60)), forceVersion = true).futureValue

    val renewedTargets = signedRoleGeneration.findRole[TargetsRole](repoId).futureValue
    val renewedSnapshots = signedRoleRepository.find[SnapshotRole](repoId).futureValue
    val renewedTimestamps = signedRoleGeneration.findRole[TimestampRole](repoId).futureValue
    val (_, renewedSnapshotsHash) = renewedTimestamps.role.meta(RoleType.SNAPSHOT.metaPath).hashes.head

    val renewedSnaphotsParsed = renewedSnapshots.role
    val (_, renewedTargetsHash) = renewedSnaphotsParsed.meta(RoleType.TARGETS.metaPath).hashes.head

    renewedTargetsHash shouldBe renewedTargets.checksum.hash
    renewedSnapshots.version shouldBe oldSnapshots.version + 1

    renewedTimestamps.version shouldBe oldTimestamps.version + 1
    renewedSnapshotsHash shouldBe renewedSnapshots.checksum.hash
  }
}
