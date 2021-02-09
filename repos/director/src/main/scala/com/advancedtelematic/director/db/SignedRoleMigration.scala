package com.advancedtelematic.director.db

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.advancedtelematic.libats.codecs.CirceCodecs.checkSumCodec
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.data.TufDataType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import io.circe.syntax._
import org.slf4j.LoggerFactory
import slick.jdbc.GetResult._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SetParameter}

import scala.concurrent.{ExecutionContext, Future}

// migrate director.file_cache to director2.signed_roles
// (director2.checksum can only be created in Scala.)
class SignedRoleMigration(old_director_schema: String = "director")
                         (implicit val db: Database, val mat: Materializer, val ec: ExecutionContext) {

  private case class Row(role: RoleType, version: Int, deviceId: DeviceId, content: String, createdAt: Instant, updatedAt: Instant, expiresAt: Instant)

  private val _log = LoggerFactory.getLogger(this.getClass)

  implicit val getRoleTypeResult = GetResult(r => RoleType.withName(r.nextString()))
  implicit val setRoleTypeParameter: SetParameter[RoleType] =
    (roleType: RoleType, pp: PositionedParameters) => pp.setString(roleType.toString)

  implicit val getDeviceIdResult = GetResult(r => DeviceId(UUID.fromString(r.nextString)))
  implicit val setDeviceIdParameter: SetParameter[DeviceId] =
    (deviceId: DeviceId, pp: PositionedParameters) => pp.setString(deviceId.uuid.toString)

  implicit val getInstantResult = GetResult(r => r.nextTimestamp().toInstant)
  implicit val setInstantParameter: SetParameter[Instant] =
    (instant: Instant, pp: PositionedParameters) => pp.setTimestamp(Timestamp.from(instant))

  implicit val setChecksumParameter: SetParameter[Checksum] =
    (checksum: Checksum, pp: PositionedParameters) => pp.setString(checksum.asJson.noSpaces)

  private def exists(schema: String, table: String): Future[Boolean] = {
    val sql = sql"SELECT EXISTS(SELECT * FROM information_schema.tables WHERE table_schema = $schema AND table_name = $table)".as[Boolean]
    db.run(sql).map(_.head)
  }

  private def fileCache: Source[Row, NotUsed] = {
    val sql =
      sql""" select role,version,device,file_entity,created_at,updated_at,expires from #$old_director_schema.file_cache""".as[(RoleType,Int,DeviceId,String,Instant,Instant,Instant)]

    val flow = Flow[(RoleType, Int, DeviceId, String, Instant, Instant, Instant)].map(Row.tupled)

    Source.fromPublisher(db.stream(sql)).via(flow)
  }

  private def calculateCheckSum(row: Row): Future[(Row, Checksum, Long)] = {
    Future {
      scala.concurrent.blocking {
        val canonicalJson = row.content.asJson.canonical
        val checksum = Sha256Digest.digest(canonicalJson.getBytes)
        val length = canonicalJson.length

        (row, checksum, length.toLong)
      }
    }
  }

  private def writeSignedRole(rows: Seq[(Row, Checksum, Long)]): Future[Done] = {
    val sql = DBIO.sequence(rows.map { case (row, checksum, length) =>
      sqlu"""insert into signed_roles value (${row.role},${row.version},${row.deviceId},$checksum,$length,${row.content},${row.createdAt},${row.updatedAt},${row.expiresAt})
        on duplicate key update checksum=$checksum,length=$length,content=${row.content},created_at=${row.createdAt},updated_at=${row.updatedAt},expires_at=${row.expiresAt}
        """
    })

    db.run(sql).map { _ =>
      _log.debug(s"Wrote ${rows.size} rows (first: ${rows.headOption})")
      Done
    }
  }

  def run: Future[Done] = {
    exists(old_director_schema, "file_cache").flatMap { flag =>
      if (flag)
        fileCache
          .mapAsyncUnordered(10)(calculateCheckSum)
          .grouped(200)
          .mapAsync(200)(writeSignedRole)
          .runWith(Sink.ignore)
      else {
        _log.info("file_cache doesn't exist, skipping")
        FastFuture.successful(Done)
      }
    }
  }
}
