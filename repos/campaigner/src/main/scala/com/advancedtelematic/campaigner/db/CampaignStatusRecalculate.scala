package com.advancedtelematic.campaigner.db

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.advancedtelematic.campaigner.data.DataType.{CampaignId, CampaignStatus}
import com.advancedtelematic.libats.slick.db.SlickUUIDKey
import org.slf4j.LoggerFactory
import slick.jdbc.GetResult
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class CampaignStatusRecalculate()(implicit db: Database, ec: ExecutionContext, mat: Materializer) extends
  CampaignSupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  val statusTransition = new CampaignStatusTransition()

  implicit val getRowResult: GetResult[CampaignId] = slick.jdbc.GetResult { r =>
    SlickUUIDKey.dbMapping[CampaignId].getValue(r.rs, 1)
  }

  val campaignsSql = sql"""select uuid from campaigns where status is null""".as[CampaignId]

  def run: Future[Done] = {
    val source = db.stream(campaignsSql)

    Source.fromPublisher(source).mapAsyncUnordered(3) { campaignId =>
      db.run {
        statusTransition.isFinished(campaignId).map {
          case true => CampaignStatus.finished
          case false => CampaignStatus.launched
        }.flatMap { newStatus =>
          campaignRepo.setStatusAction(campaignId, newStatus).map(_ => newStatus)
        }
      }.map { status =>
        _log.info(s"updated $campaignId to status $status")
      }
    }.map(_ => 1).runWith(Sink.fold(0)(_ + _)).map { count =>
      _log.info(s"Finished updating $count campaigns")
      Done
    }
  }
}
