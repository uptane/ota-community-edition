package com.advancedtelematic.campaigner.actor

import akka.Done
import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.advancedtelematic.campaigner.client.DirectorClient
import com.advancedtelematic.campaigner.data.DataType.DeviceStatus.DeviceStatus
import com.advancedtelematic.campaigner.data.DataType.{Campaign, CampaignId, CancelTaskStatus, DeviceStatus}
import com.advancedtelematic.campaigner.db.{CampaignSupport, Campaigns, CancelTaskSupport, DeviceUpdateSupport}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import slick.jdbc.MySQLProfile.api.Database

import scala.concurrent.Future

object CampaignCanceler {
  private object Start

  def props(director: DirectorClient,
            campaign: CampaignId,
            ns: Namespace,
            batchSize: Int)
           (implicit db: Database, mat: Materializer): Props =
    Props(new CampaignCanceler(director, campaign, ns, batchSize))
}

class CampaignCanceler(director: DirectorClient,
                       campaignId: CampaignId,
                       ns: Namespace,
                       batchSize: Int)
                      (implicit db: Database, mat: Materializer)
    extends Actor
    with ActorLogging
    with CampaignSupport
    with DeviceUpdateSupport
    with CancelTaskSupport {

  import CampaignCanceler._
  import akka.pattern.pipe
  import context._

  private val campaigns = Campaigns()

  override def preStart(): Unit =
    self ! Start

  private def cancel(devs: Seq[(DeviceId, DeviceStatus)], campaign: Campaign): Future[Done] = {
    val (requested, others) = devs.toStream.partition(_._2 == DeviceStatus.requested)

    log.info(s"Canceling campaign requested: ${requested.size}, others: ${others.size}")

    val directorF =
      if (others.nonEmpty)
        director.cancelUpdate(ns, others.map(_._1))
      else
        FastFuture.successful(Seq.empty)

    val cancelNotApprovedUpdates = (affected: Seq[DeviceId]) =>
      if (campaign.autoAccept) {
        Future.unit
      } else {
        val scheduled = others.filter(_._2 == DeviceStatus.scheduled).map(_._1)
        val scheduledNotAffected = scheduled.diff(affected)
        log.info(s"cancelling ${scheduledNotAffected.size} scheduled devices")
        campaigns.cancelDevices(campaignId, scheduledNotAffected)
      }

    for {
      affected <- directorF
      _ = log.info(s"cancelling ${affected.size} affected devices")
      _ <- campaigns.cancelDevices(campaignId, affected)
      _ = log.info(s"cancelling ${requested.size} devices not yet scheduled")
      _ <- campaigns.cancelDevices(campaignId, requested.map(_._1))
      _ <- cancelNotApprovedUpdates(affected)
    } yield Done
  }

  def run(): Future[Done] = for {
    campaign <- campaignRepo.find(campaignId)
    _ <- deviceUpdateRepo.findByCampaignStream(campaignId, DeviceStatus.scheduled, DeviceStatus.accepted, DeviceStatus.requested)
      .grouped(batchSize)
      .mapAsync(1)(cancel(_, campaign))
      .runWith(Sink.ignore)
    _ <- cancelTaskRepo.setStatus(campaignId, CancelTaskStatus.completed)
  } yield Done

  def receive: Receive = {
    case Start =>
      log.debug(s"Start to cancel devices for $campaignId")
      run().recoverWith { case err =>
        cancelTaskRepo.setStatus(campaignId, CancelTaskStatus.error).map(_ => Failure(err))
      }.pipeTo(self)
    case Done =>
      log.debug(s"Done cancelling for $campaignId")
      context.stop(self)
    case Failure(err) =>
      log.error(err, s"errors when cancelling $campaignId")
      context.stop(self)
  }
}
