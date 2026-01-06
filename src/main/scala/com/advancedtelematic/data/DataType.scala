package com.advancedtelematic.data

import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.DataType.ObjectStatus.ObjectStatus
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libats.messaging_datatype.DataType.{Commit, ValidCommit}
import enumeratum.{EnumEntry, *}
import enumeratum.EnumEntry.Snakecase
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.digests.SHA256
import io.circe.Json
import org.apache.commons.codec.binary.Hex

import java.nio.file.{Path, Paths}
import java.util.Base64
import scala.util.Try

object DataType {
  import com.advancedtelematic.libats.data.ValidationUtils.*

  case class Ref(namespace: Namespace, name: RefName, value: Commit, objectId: ObjectId)

  case class RefName(get: String) extends AnyVal

  case class CommitManifest(namespace: Namespace, commit: ObjectId, contents: Json)

  object StaticDeltaMeta {
    sealed trait Status extends EnumEntry with Snakecase

    object Status extends Enum[Status] {
      val values = findValues

      case object Uploading extends Status
      case object Available extends Status
      case object Deleted extends Status
    }
  }

  case class StaticDeltaMeta(namespace: Namespace, id: DeltaId, from: Commit, to: Commit, superblockHash: SuperBlockHash, size: Long, status: StaticDeltaMeta.Status)

  type SuperBlockHash = SHA256

  case class StaticDeltaIndex(deltas: Map[(Commit, Commit), SuperBlockHash])

  case class ValidDeltaIndexId()
  type DeltaIndexId = Refined[String, ValidDeltaIndexId]

  // `.get` is safe to call because `value` is refined and validated
  implicit class DeltaIndexIdOps(value: DeltaIndexId) {
    def commit: Commit = {
      val (commitIdBase64, _) = value.value.splitAt(value.value.indexOf('.'))
      val bytes = fromMBase64(commitIdBase64)
      Hex.encodeHexString(bytes).refineTry[ValidCommit].get
    }
  }

  implicit val validDeltaIndexId: Validate.Plain[String, ValidDeltaIndexId] = Validate.fromPredicate(
    indexId => {
      val (commitIdBase64, objectType) = indexId.splitAt(indexId.indexOf('.'))
      MBase64Regex.matches(commitIdBase64) && objectType == ".index"
    },
    indexId => s"$indexId must be <modified base64>.index",
    ValidDeltaIndexId()
  )

  case class ValidObjectId()

  implicit val validObjectId: Validate.Plain[String, ValidObjectId] =
    Validate.fromPredicate(
      objectId => {
        val (sha, objectType) = objectId.splitAt(objectId.indexOf('.'))
        validHex(64, sha) && objectType.nonEmpty
      },
      objectId => s"$objectId must be in format <sha256>.objectType",
      ValidObjectId()
    )

  type ObjectId = Refined[String, ValidObjectId]

  type MBase64 = MatchesRegex["""^[A-z0-9+_]+$"""]

  // Safe to use `get` because we know that replacing invalid chars makes it valid mbase64
  private def mbase64(data: Array[Byte]): Refined[String, MBase64] = refineV[MBase64](
    Base64.getEncoder.encodeToString(data).replace("/", "_").replace("=", "")
  ).toOption.get

  private def fromMBase64(data: String): Array[Byte] =
    Base64.getDecoder.decode(data.replace("_", "/"))

  implicit class CommitTupleOps(value: (Commit, Commit)) {
    def toDeltaId: DeltaId = {
      val (from, to) = value
      val fromHex = Hex.decodeHex(from.value)
      val fromMbase = mbase64(fromHex).value
      val toHex = Hex.decodeHex(to.value)
      val toMbase = mbase64(toHex).value
      s"$fromMbase-$toMbase".refineTry[ValidDeltaId].get
    }
  }

  implicit class ObjectIdOps(value: ObjectId) {
    def path(parent: Path): Path = {
      val (prefix, rest) = value.value.splitAt(2)
      Paths.get(parent.toString, prefix, rest)
    }

    def filename: Path = path(Paths.get("/")).getFileName
  }

  object ObjectId {
    def from(commit: Commit): ObjectId = ObjectId.parse(commit.value + ".commit").toOption.get

    def parse(string: String): Try[ObjectId] = string.refineTry[ValidObjectId]
  }

  object ObjectStatus extends Enumeration {
    type ObjectStatus = Value

    val UPLOADED, CLIENT_UPLOADING, SERVER_UPLOADING = Value
  }

  case class TObject(namespace: Namespace, id: ObjectId, byteSize: Long, status: ObjectStatus)

  private val MBase64Regex = "^[A-z0-9+_]+$".r

  case class ValidDeltaId()
  type DeltaId = Refined[String, ValidDeltaId]

  implicit val validDeltaId: Validate.Plain[String, ValidDeltaId] =
    Validate.fromPredicate(
      v => {
        val parts = v.split("-")
        parts.length == 2 && MBase64Regex.matches(parts.head) && MBase64Regex.matches(parts.last)
      },
      v => s"$v is not a valid DeltaId (mbase64(from)-mbase64(to))",
      ValidDeltaId()
    )

  implicit class RefinedStringPrefixedPathOps(value: Refined[String, ?]) {
    def asPrefixedPath: Path = {
      val (prefix, rest) = value.value.splitAt(2)
      Paths.get(prefix, rest)
    }
  }

  implicit class DeltaIdOps(deltaId: DeltaId) {
    // Safe to call `.last` and `.get` because `deltaId.value` was already validated
    def toCommit: Commit = {
      val toBytes = fromMBase64(deltaId.value.split("-").last)
      val toCommitStr = Hex.encodeHexString(toBytes)
      toCommitStr.refineTry[ValidCommit].get
    }

    // Safe to call `.head` and `.get` because `deltaId.value` was already validated
    def fromCommit: Commit = {
      val fromBytes = fromMBase64(deltaId.value.split("-").head)
      val toCommitStr = Hex.encodeHexString(fromBytes)
      toCommitStr.refineTry[ValidCommit].get
    }

    def toCommitObjectId: ObjectId = ObjectId.from(toCommit)
  }

  object Commit {
    def fromObjectContent(bytes: Array[Byte]): Either[String, Commit] =
      refineV[ValidCommit](DigestCalculator.byteDigest()(bytes))
  }
}
