package com.advancedtelematic.treehub.delta_store

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.{CommitTupleOps, SuperBlockHash}
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libats.messaging_datatype.DataType.{Commit, ValidCommit}
import com.advancedtelematic.treehub.db.StaticDeltaMetaRepositorySupport
import com.advancedtelematic.treehub.object_store
import com.advancedtelematic.treehub.object_store.S3BlobStore
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import eu.timepit.refined.api.RefType
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time.{Seconds, Span}

import java.nio.file.Paths
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.Random

class S3DeltaStorageIntegrationSpec extends TreeHubSpec with ResourceSpec with BeforeAndAfterAll with StaticDeltaMetaRepositorySupport {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val s3Client = object_store.S3Client(s3Credentials)

  val s3DeltaStore = new S3BlobStore(s3Credentials, s3Client, allowRedirects = false, root = Some(Paths.get("deltas")))

  override val deltas = new StaticDeltas(s3DeltaStore)

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  test("returns static delta part") {
    val superblockHash = RefType.applyRef[SuperBlockHash](randomHash()).toOption.get
    val deltaId = (randomCommit(), randomCommit()).toDeltaId

    val bytes = ByteString("some static delta data")
    deltas.store(defaultNs, deltaId, "0", Source.single(bytes), bytes.size, superblockHash).futureValue

    deltas.store(defaultNs, deltaId, "superblock", Source.single(bytes), bytes.size, superblockHash).futureValue

    val (size, result) = deltas.retrieve(defaultNs, deltaId, "0").futureValue

    size shouldBe bytes.size * 2
    val savedBytes = result.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).futureValue

    savedBytes.utf8String shouldBe "some static delta data"
  }

  def randomCommit(): Commit =
    randomHash().refineTry[ValidCommit].get

  def randomHash() =
    DigestCalculator.digest()(new Random().nextString(10))
}
