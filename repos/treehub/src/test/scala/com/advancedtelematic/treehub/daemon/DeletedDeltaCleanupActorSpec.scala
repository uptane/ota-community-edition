package com.advancedtelematic.treehub.daemon

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.stream.scaladsl.Source
import akka.testkit.{ImplicitSender, TestException, TestKitBase}
import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{CommitTupleOps, StaticDeltaMeta, SuperBlockHash}
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libats.messaging_datatype.DataType.{Commit, ValidCommit}
import com.advancedtelematic.treehub.daemon.DeletedDeltaCleanupActor.{Done, Tick}
import com.advancedtelematic.treehub.db.StaticDeltaMetaRepositorySupport
import com.advancedtelematic.treehub.delta_store.StaticDeltas
import com.advancedtelematic.treehub.http.Errors
import com.advancedtelematic.treehub.object_store.{BlobStore, LocalFsBlobStore}
import com.advancedtelematic.util.{DatabaseSpec, LongTest, TreeHubSpec}
import eu.timepit.refined.api.RefType
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import slick.jdbc.MySQLProfile.api.*

import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Random

protected abstract class DeletedDeltaCleanupActorSpecUtil extends TreeHubSpec with TestKitBase with ImplicitSender with DatabaseSpec
  with StaticDeltaMetaRepositorySupport with Eventually with LongTest with BeforeAndAfterEach {

  override implicit lazy val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  val subject: ActorRef

  override def beforeEach() = {
    db.run(sqlu"delete from `static_deltas`").futureValue
  }

  override def afterAll(): Unit = {
    subject ! PoisonPill
  }
}

class DeletedDeltaCleanupActorSpec extends DeletedDeltaCleanupActorSpecUtil {

  import system.dispatcher

  lazy val storage = new LocalFsBlobStore(Files.createTempDirectory("deleted-deltas-store"))
  lazy val deltas = new StaticDeltas(storage)

  lazy val subject = system.actorOf(DeletedDeltaCleanupActor.withBackOff(storage, tickInterval=5.seconds, autoStart = false))

  test("deletes deleted deltas") {
    val superblockHash = RefType.applyRef[SuperBlockHash](randomHash()).toOption.get
    val deltaId = (randomCommit(), randomCommit()).toDeltaId

    val bytes = ByteString("some static delta data")

    // Store delta parts
    deltas.store(defaultNs, deltaId, "0", Source.single(bytes), bytes.size, superblockHash).futureValue
    deltas.store(defaultNs, deltaId, "superblock", Source.single(bytes), bytes.size, superblockHash).futureValue

    // Validate delta is stored to repository (DB)
    val result = staticDeltaMetaRepository.find(defaultNs, deltaId).futureValue
    result.id shouldBe deltaId
    result.status shouldBe StaticDeltaMeta.Status.Available

    // Validate delta parts exist in storage
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("0")).futureValue shouldBe true
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("superblock")).futureValue shouldBe true

    // Mark delta for deletion
    deltas.markDeleted(defaultNs, deltaId)

    // Validate delta is not available anymore
    staticDeltaMetaRepository.find(defaultNs, deltaId).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist

    // Validate delta is marked for deletion
    val deleted = staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted).futureValue
    deleted.length shouldBe 1
    deleted.head.id shouldBe deltaId

    // Trigger actor action
    subject ! Tick
    val done = expectMsgType[Done]
    done.count shouldBe deleted.length

    // Validate delta parts were deleted from storage
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("0")).futureValue shouldBe false
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("superblock")).futureValue shouldBe false

    // Validate delta was removed from repository (DB)
    staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted).futureValue.length shouldBe 0

    staticDeltaMetaRepository.find(defaultNs, deltaId).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist
  }

  test("schedules new run when receiving Done") {
    val superblockHash = RefType.applyRef[SuperBlockHash](randomHash()).toOption.get
    val deltaId = (randomCommit(), randomCommit()).toDeltaId

    val bytes = ByteString("some static delta data")

    // Store delta parts
    deltas.store(defaultNs, deltaId, "0", Source.single(bytes), bytes.size, superblockHash).futureValue
    deltas.store(defaultNs, deltaId, "superblock", Source.single(bytes), bytes.size, superblockHash).futureValue

    subject ! Done(0, 0)

    deltas.markDeleted(defaultNs, deltaId)

    eventually({
      // Validate delta parts were deleted from storage
      storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("0")).futureValue shouldBe false
      storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("superblock")).futureValue shouldBe false

      // Validate delta was removed from repository (DB)
      staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted).futureValue.length shouldBe 0

      staticDeltaMetaRepository.find(defaultNs, deltaId).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist
    })(patienceConfig.copy(timeout = Span(6, Seconds)), implicitly)
  }

  test("does nothing to deltas that are not deleted") {
    val superblockHash = RefType.applyRef[SuperBlockHash](randomHash()).toOption.get
    val deltaId = (randomCommit(), randomCommit()).toDeltaId

    val bytes = ByteString("some static delta data")

    // Store delta parts
    deltas.store(defaultNs, deltaId, "0", Source.single(bytes), bytes.size, superblockHash).futureValue
    deltas.store(defaultNs, deltaId, "superblock", Source.single(bytes), bytes.size, superblockHash).futureValue

    // Validate delta is stored to repository (DB)
    val result = staticDeltaMetaRepository.find(defaultNs, deltaId).futureValue
    result.id shouldBe deltaId
    result.status shouldBe StaticDeltaMeta.Status.Available

    // Validate delta parts exist in storage
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("0")).futureValue shouldBe true
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("superblock")).futureValue shouldBe true

    subject ! Tick
    val done = expectMsgType[Done]
    done.count shouldBe 0

    val result1 = staticDeltaMetaRepository.find(defaultNs, deltaId).futureValue
    result1.id shouldBe deltaId
    result1.status shouldBe StaticDeltaMeta.Status.Available

    // Validate delta parts exist in storage
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("0")).futureValue shouldBe true
    storage.exists(defaultNs, deltaId.asPrefixedPath.resolve("superblock")).futureValue shouldBe true
  }

  def randomCommit(): Commit =
    randomHash().refineTry[ValidCommit].get

  def randomHash() =
    DigestCalculator.digest()(new Random().nextString(10))
}

class DeletedDeltaCleanupActorMockStorageSpec extends DeletedDeltaCleanupActorSpecUtil {

  import system.dispatcher

  lazy val mockStorage = new MockBlobStore()
  lazy val deltas = new StaticDeltas(mockStorage)

  lazy val subject = system.actorOf(DeletedDeltaCleanupActor.withBackOff(mockStorage, tickInterval = 5.seconds, autoStart = false))

  def randomCommit(): Commit =
    randomHash().refineTry[ValidCommit].get

  def randomHash() =
    DigestCalculator.digest()(new Random().nextString(10))

  test("fails to delete deleted deltas when exception from storage") {
    val superblockHash1 = RefType.applyRef[SuperBlockHash](randomHash()).toOption.get
    val deltaId1 = (randomCommit(), randomCommit()).toDeltaId

    val superblockHash2 = RefType.applyRef[SuperBlockHash](randomHash()).toOption.get
    val deltaId2 = (randomCommit(), randomCommit()).toDeltaId

    val bytes = ByteString("some static delta data")

    // Store delta parts
    deltas.store(defaultNs, deltaId1, "0", Source.single(bytes), bytes.size, superblockHash1).futureValue
    deltas.store(defaultNs, deltaId1, "superblock", Source.single(bytes), bytes.size, superblockHash1).futureValue

    deltas.store(defaultNs, deltaId2, "1", Source.single(bytes), bytes.size, superblockHash2).futureValue
    deltas.store(defaultNs, deltaId2, "superblock", Source.single(bytes), bytes.size, superblockHash2).futureValue

    // Validate deltas are stored to repository (DB)
    val result1 = staticDeltaMetaRepository.find(defaultNs, deltaId1).futureValue
    result1.id shouldBe deltaId1
    result1.status shouldBe StaticDeltaMeta.Status.Available

    val result2 = staticDeltaMetaRepository.find(defaultNs, deltaId2).futureValue
    result2.id shouldBe deltaId2
    result2.status shouldBe StaticDeltaMeta.Status.Available

    // Mark deltas for deletion
    deltas.markDeleted(defaultNs, deltaId1)
    deltas.markDeleted(defaultNs, deltaId2)

    // Validate deltas are not available anymore
    staticDeltaMetaRepository.find(defaultNs, deltaId1).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist
    staticDeltaMetaRepository.find(defaultNs, deltaId2).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist

    // Validate deltas are marked for deletion
    staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted).futureValue.length shouldBe 2

    mockStorage.addPathToFailingPaths(deltaId1.asPrefixedPath)

    // Trigger actor action
    subject ! Tick
    val done = expectMsgType[Done]
    done.count shouldBe 2
    done.failedCount shouldBe 1

    // Validate failed delta was not removed from repository (DB)
    val deleted = staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted).futureValue
    deleted.length shouldBe 1
    deleted.head.id shouldBe deltaId1

    staticDeltaMetaRepository.find(defaultNs, deltaId1).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist
    staticDeltaMetaRepository.find(defaultNs, deltaId2).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist

    mockStorage.resetFailingPaths()

    subject ! Tick
    val done2 = expectMsgType[Done]
    done2.count shouldBe 1
    done2.failedCount shouldBe 0

    staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted).futureValue.length shouldBe 0
    staticDeltaMetaRepository.find(defaultNs, deltaId1).failed.futureValue shouldBe Errors.StaticDeltaDoesNotExist
  }
}
