package com.advancedtelematic.campaigner.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import akka.stream.Materializer
import com.advancedtelematic.campaigner.client._
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.Campaigns
import com.advancedtelematic.libats.data.DataType.Namespace

import scala.concurrent.duration._
import slick.jdbc.MySQLProfile.api._
import cats.syntax.show._

object CampaignSupervisor {

  private final object CleanUpCampaigns
  private final object PickUpCampaigns
  private final case class CancelCampaigns(campaigns: Set[(Namespace, CampaignId)])
  private final case class ResumeCampaigns(campaigns: Set[Campaign])

  // only for test
  final case class CampaignsScheduled(campaigns: Set[CampaignId])
  final case class CampaignsCancelled(campaigns: Set[CampaignId])

  def props(director: DirectorClient,
            pollingTimeout: FiniteDuration,
            delay: FiniteDuration,
            batchSize: Int)
           (implicit db: Database, mat: Materializer): Props =
    Props(new CampaignSupervisor(director, pollingTimeout, delay, batchSize))

}

class CampaignSupervisor(director: DirectorClient,
                         pollingTimeout: FiniteDuration,
                         delay: FiniteDuration,
                         batchSize: Int)
                        (implicit db: Database, mat: Materializer) extends Actor
  with ActorLogging {

  import CampaignScheduler._
  import CampaignSupervisor._
  import akka.pattern.pipe
  import context._

  val scheduler = system.scheduler

  val campaigns = Campaigns()

  override def preStart(): Unit = {
    // periodically clear out cancelled campaigns
    scheduler.schedule(0.milliseconds, pollingTimeout, self, CleanUpCampaigns)

    // periodically (re-)schedule non-completed campaigns
    scheduler.schedule(0.milliseconds, pollingTimeout, self, PickUpCampaigns)

    // pick up campaigns where they left
    campaigns
      .remainingCampaigns()
      .map(ResumeCampaigns)
      .pipeTo(self)

    // pick up cancelled campaigns where they left
    campaigns
      .remainingCancelling()
      .map(x => CancelCampaigns(x.toSet))
      .pipeTo(self)
  }

  def cancelCampaign(ns: Namespace, campaign: CampaignId): ActorRef =
    context.actorOf(CampaignCanceler.props(
      director,
      campaign,
      ns,
      batchSize
    ))

  def scheduleCampaign(campaign: Campaign): ActorRef = {
    val childProps = CampaignScheduler.props(director, campaign, delay, batchSize)

    val props = BackoffSupervisor.props(
      BackoffOpts.onFailure(
        childProps,
        childName = s"campaignScheduler-${campaign.id.show}",
        minBackoff = 3.seconds,
        maxBackoff = 1.hour,
        randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
      ).withAutoReset(10.seconds))

    context.actorOf(props)
  }

  def supervising(campaignSchedulers: Map[CampaignId, ActorRef]): Receive = {
    case CleanUpCampaigns =>
      log.debug(s"cleaning up campaigns")
      campaigns.freshCancelled()
        .map(x => CancelCampaigns(x.toSet))
        .pipeTo(self)

    case PickUpCampaigns =>
      log.debug(s"picking up campaigns")
      campaigns
        .launchedCampaigns
        .map(ResumeCampaigns)
        .pipeTo(self)

    case CancelCampaigns(cs) if cs.nonEmpty =>
      log.info(s"cancelling campaigns $cs")
      cs.foreach{case (_, c) => campaignSchedulers.get(c).foreach(stop)}
      cs.foreach{case (ns, c) => cancelCampaign(ns, c)}
      become(supervising(campaignSchedulers -- cs.map(_._2)))
      parent ! CampaignsCancelled(cs.map(_._2))

    case ResumeCampaigns(cs) if cs.nonEmpty =>
      log.info(s"resume campaigns ${cs.map(_.id)}")
      // only create schedulers for campaigns without a scheduler
      val newlyScheduled =
        cs
          .filterNot(c => campaignSchedulers.contains(c.id))
          .map(c => c.id -> scheduleCampaign(c))
          .toMap
      if (newlyScheduled.nonEmpty) {
        become(supervising(campaignSchedulers ++ newlyScheduled))
        parent ! CampaignsScheduled(newlyScheduled.keySet)
      } else
        log.debug(s"Not creating scheduler for campaigns, scheduler already exists")

    case CampaignComplete(id) =>
      log.info(s"$id completed")
      parent ! CampaignComplete(id)
      become(supervising(campaignSchedulers - id))

    case Status.Failure(ex) =>
      // TODO: Move campaign to failed?
      log.error(ex, s"An error occurred scheduling a campaign: ${ex.getMessage}")
  }

  def receive: Receive = supervising(Map.empty)
}
