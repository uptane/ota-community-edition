package com.advancedtelematic.treehub.http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.*
import com.advancedtelematic.data.ClientDataType.CommitInfoRequest
import com.advancedtelematic.data.DataType.{CommitTupleOps, DeltaId, SuperBlockHash}
import com.advancedtelematic.data.GVariantEncoder.*
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.RefinedMarshallingSupport.*
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import com.advancedtelematic.treehub.delta_store.StaticDeltas
import com.advancedtelematic.treehub.http.PathMatchers.*
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter
import com.advancedtelematic.treehub.repo_metrics.UsageMetricsRouter.UpdateBandwidth
import eu.timepit.refined.api.RefType
import io.circe.KeyEncoder
import io.circe.syntax.EncoderOps
import org.slf4j.LoggerFactory

import scala.util.Success

class DeltaResource(namespace: Directive1[Namespace],
                    staticDeltas: StaticDeltas,
                    usageHandler: UsageMetricsRouter.HandlerRef) {

  import akka.http.scaladsl.server.Directives.*

  val _log = LoggerFactory.getLogger(this.getClass)

  private def publishBandwidthUsage(namespace: Namespace, usageBytes: Long, deltaId: DeltaId): Unit = {
    usageHandler ! UpdateBandwidth(namespace, usageBytes, deltaId.toCommitObjectId)
  }

  val superblockHashHeader: Directive1[SuperBlockHash] = optionalHeaderValueByName("x-trx-superblock-hash").flatMap {
    case Some(hashStr) => {
      RefType.applyRef[SuperBlockHash](hashStr).map(provide).getOrElse(failWith(Errors.MissingSuperblockHash))
    }
    case None => failWith(Errors.MissingSuperblockHash)
  }

  val route =
    extractExecutionContext { implicit ec =>
      namespace { ns =>
        (get & path("delta-indexes" / PrefixedIndexId)) { id =>
          val f = staticDeltas.index(ns, id).map(_.encodeGVariant)
          complete(f)
        } ~
        pathPrefix("deltas") {
          path(PrefixedDeltaIdPath / Segment) { (deltaId, path) =>
            (post & superblockHashHeader) { superblockHash =>
              (extractRequestEntity) { entity =>
                entity.contentLengthOption match {
                  case Some(size) if size > 0 =>
                    val f = staticDeltas.store(ns, deltaId, path, entity.dataBytes, size, superblockHash)
                    complete(f.map(_ => StatusCodes.OK))
                  case _ => reject(MissingHeaderRejection("Content-Length"))
                }
              }
            } ~
            get {
              val f = staticDeltas.retrieve(ns, deltaId, path)
                .andThen {
                  case Success((size, result)) =>
                    // TODO: On redirects, returns entire size of delta
                    publishBandwidthUsage(ns, result.entity.contentLengthOption.getOrElse(size), deltaId)
                }.map(_._2)

              complete(f)
            }
          } ~
            post {
              import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
              implicit val commitKeyEncoder: KeyEncoder[Commit] = KeyEncoder.encodeKeyString.contramap(_.value)

              entity(as[CommitInfoRequest]) { request =>
                val f = staticDeltas.getCommitInfos(ns, request.commits)
                complete(f)
              }
            } ~
            (delete & path(PrefixedDeltaIdPath)) { id =>
              val f = staticDeltas.markDeleted(ns, id)
              complete(f.map(_ => StatusCodes.Accepted))
            }
            ~
            (pathEnd & parameters(Symbol("from").as[Commit], Symbol("to").as[Commit])) { (from, to) =>
              val deltaId = (from, to).toDeltaId
              val uri = Uri(s"/deltas/${deltaId.asPrefixedPath}")
              complete(HttpResponse(StatusCodes.Found, headers = List(Location(uri))))
            } ~
            (pathEnd & parameters(Symbol("offset").as(nonNegativeLong).?, Symbol("limit").as(nonNegativeLong).?)) { (offset, limit) =>
              val f = staticDeltas.getAll(ns, offset, limit).map(_.asJson.toString())
              complete(f)
            }
        }
      }
    }
}
