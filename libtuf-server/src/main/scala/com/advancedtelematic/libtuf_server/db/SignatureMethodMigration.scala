package com.advancedtelematic.libtuf_server.db

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.Materializer
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf_server.db.SignatureMethodMigration.Row
import io.circe.Json
import org.slf4j.LoggerFactory
import slick.dbio.StreamingDBIO
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Future

object SignatureMethodMigration {
  case class Row(repoId: RepoId, roleType: RoleType, payload: Json)
}

class SignatureMethodMigration(dBIO: StreamingDBIO[Vector[Row], Row],
                               replaceFn: Row => Future[Unit])(
  implicit val db: Database,
  val mat: Materializer,
  val system: ActorSystem) {

  implicit val ec: scala.concurrent.ExecutionContextExecutor = system.dispatcher

  private val _log = LoggerFactory.getLogger(this.getClass)

  def run: Future[Done] = {

    val source = Source.fromPublisher(db.stream(dBIO))

    val convertFlow = Flow[Row].mapAsync(3) { row =>
      val oldMethodE =
        row.payload.hcursor.downField("signatures").downArray.downField("method").as[String]

      oldMethodE match {
        case Left(err) =>
          _log.warn(s"Could not decode signed root for ${row.repoId}, $err")
          Future.successful(row)

        case Right(method) if method == "rsassa-pss" =>
          _log.info(s"root role for ${row.repoId} needs migration")

          replaceFn(row).map(_ => row)

        case Right(_) =>
          _log.info(s"root role for ${row.repoId} is up to date")
          Future.successful(row)
      }
    }

    val sink = Sink.foreach[Row] { row =>
      _log.info(s"Finished processing ${row.repoId}")
    }

    source.via(convertFlow).runWith(sink)
  }

}
