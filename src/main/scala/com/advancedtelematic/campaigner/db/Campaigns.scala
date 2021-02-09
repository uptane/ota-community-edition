package com.advancedtelematic.campaigner.db

import java.time.Instant

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.advancedtelematic.campaigner.data.DataType.CampaignStatus.CampaignStatus
import com.advancedtelematic.campaigner.data.DataType.DeviceStatus.DeviceStatus
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.SlickMapping._
import com.advancedtelematic.campaigner.http.Errors._
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

object Campaigns {
  def apply()(implicit db: Database, ec: ExecutionContext): Campaigns = new Campaigns()
}

protected [db] class Campaigns(implicit db: Database, ec: ExecutionContext)
    extends CampaignSupport
    with CampaignMetadataSupport
    with DeviceUpdateSupport
    with CancelTaskSupport {

  val campaignStatusTransition = new CampaignStatusTransition()

  def remainingCancelling(): Future[Seq[(Namespace, CampaignId)]] = cancelTaskRepo.findInprogress()

  /**
   * Returns all campaigns that have devices in `requested` state
   */
  def remainingCampaigns(): Future[Set[Campaign]] =
    db.run(campaignRepo.findAllWithRequestedDevices)

  /**
   * Given a campaign ID, returns IDs of all devices that are in `requested`
   * state
   */
  def requestedDevicesStream(campaign: CampaignId): Source[DeviceId, NotUsed] =
    deviceUpdateRepo.findByCampaignStream(campaign, DeviceStatus.requested).map(_._1)

  /**
   * Re-calculates the status of the campaign and updates the table
   */
  def updateStatus(campaignId: CampaignId): Future[Unit] =
    db.run(campaignStatusTransition.updateToCalculatedStatus(campaignId))

  def freshCancelled(): Future[Seq[(Namespace, CampaignId)]] =
    cancelTaskRepo.findPending()

  def launchedCampaigns: Future[Set[Campaign]] =
    db.run(campaignRepo.findAllLaunched)

  /**
   * Sets status of each given device to `rejected` for a given campaign and
   * update.
   */
  def rejectDevices(campaignId: CampaignId, deviceIds: Seq[DeviceId]): Future[Unit] =
    updateDevicesStatus(campaignId, deviceIds, DeviceStatus.rejected)

  def scheduleDevices(campaignId: CampaignId, deviceIds: Seq[DeviceId]): Future[Unit] =
    updateDevicesStatus(campaignId, deviceIds, DeviceStatus.scheduled)

  def markDevicesAccepted(campaignId: CampaignId, deviceIds: Seq[DeviceId]): Future[Unit] =
    updateDevicesStatus(campaignId, deviceIds, DeviceStatus.accepted)

  def succeedDevices(campaignId: CampaignId, devices: Seq[DeviceId], successCode: ResultCode, successDescription: ResultDescription): Future[Unit] =
    finishDevices(campaignId, devices, DeviceStatus.successful, Some(successCode), Some(successDescription))

  def failDevices(campaignId: CampaignId, devices: Seq[DeviceId], failureCode: ResultCode, failureDescription: ResultDescription): Future[Unit] =
    finishDevices(campaignId, devices, DeviceStatus.failed, Some(failureCode), Some(failureDescription))

  def cancelDevices(campaignId: CampaignId, devices: Seq[DeviceId]): Future[Unit] =
    finishDevices(campaignId, devices, DeviceStatus.cancelled, None, None)

  private def finishDevices(campaignId: CampaignId, devices: Seq[DeviceId], status: DeviceStatus, resultCode: Option[ResultCode], resultDescription: Option[ResultDescription]): Future[Unit] = db.run {
    deviceUpdateRepo
      .setUpdateStatusAction(campaignId, devices, status, resultCode, resultDescription)
      .andThen(campaignStatusTransition.devicesFinished(campaignId))
  }

  private def updateDevicesStatus(campaignId: CampaignId, deviceIds: Seq[DeviceId], status: DeviceStatus): Future[Unit] = db.run {
    for {
      campaign <- campaignRepo.findAction(campaignId)
      _ <- deviceUpdateRepo.persistManyAction(deviceIds.map(deviceId =>
        DeviceUpdate(campaign.id, campaign.updateId, deviceId, status)
      ))
    } yield ()
  }

  /**
    * Returns the most recent device updates that have failed with the code `failureCode` in the campaign with ID
    * `mainCampaignId` or any of its retry-campaigns.
    */
  def findLatestFailedUpdates(mainCampaignId: CampaignId, failureCode: ResultCode): Future[Set[DeviceUpdate]] = db.run {
    campaignRepo
      .findRetryCampaignsOfAction(mainCampaignId)
      .flatMap(cids => findFailedDeviceUpdatesAction(cids.map(_.id) + mainCampaignId))
      .map(_.filter(_.resultCode.contains(failureCode)))
  }

  def countByStatus: Future[Map[CampaignStatus, Int]] =
    db
      .run(campaignRepo.countByStatus)
      .map { counts =>
        CampaignStatus.values.map(s => s -> counts.getOrElse(s, 0)).toMap
      }

  def findCampaignsWithErrors(ns: Namespace, sortBy: SortBy, offset: Long, limit: Long): Future[PaginationResult[Campaign]] =
    campaignRepo.allWithErrors(ns, sortBy, offset, limit)

  def findCampaigns(ns: Namespace, sortBy: SortBy, offset: Long, limit: Long, status: Option[CampaignStatus], nameContains: Option[String]): Future[PaginationResult[Campaign]] =
    campaignRepo.all(ns, sortBy, offset, limit, status, nameContains)

  def findNamespaceCampaign(ns: Namespace, campaignId: CampaignId): Future[Campaign] =
    campaignRepo.find(campaignId, Option(ns))

  def findClientCampaign(campaignId: CampaignId): Future[GetCampaign] = for {
    c <- campaignRepo.find(campaignId)
    retryIds <- campaignRepo.findRetryCampaignsOf(campaignId).map(_.map(_.id))
    metadata <- campaignMetadataRepo.findFor(campaignId)
  } yield GetCampaign(c, retryIds, metadata)

  def findCampaignsByUpdate(update: UpdateId): Future[Seq[Campaign]] =
    db.run(campaignRepo.findByUpdateAction(update))

  /**
   * Given a set of campaign IDs, finds all device updates that happened in
   * these campaigns, selects the most recent failures and returns them.
   */
  protected[db] def findFailedDeviceUpdatesAction(campaignIds: Set[CampaignId]): DBIO[Set[DeviceUpdate]] = {
    val latestFinishedUpdates =
      Schema.deviceUpdates
        .filter(_.status inSet Set(DeviceStatus.rejected, DeviceStatus.successful, DeviceStatus.cancelled, DeviceStatus.failed))
        .filter(_.campaignId inSet campaignIds)
        .groupBy(_.deviceId)
        .map { case (id, upd) => (id, upd.map(_.updatedAt).max) }

    Schema.deviceUpdates
      .join(latestFinishedUpdates)
      .on { (fst, snd) => fst.deviceId === snd._1 && fst.updatedAt === snd._2 }
      .map(_._1)
      .filter(_.status === DeviceStatus.failed)
      .result
      .map(_.toSet)
  }

  /**
   * Calculates campaign-wide statistic counters, also taking retry campaigns
   * into account if any exist.
   */
  def campaignStats(campaignId: CampaignId): Future[CampaignStats] = db.run {
    final case class Counts(
      processed: Long,
      affected: Long,
      rejected: Long,
      cancelled: Long,
      finished: Long)

    def processCounts(counts: Map[DeviceStatus, Int]): Counts = Counts(
      processed = counts.values.sum.toLong,
      affected = counts.filterKeys(_ != DeviceStatus.rejected).values.sum.toLong,
      rejected = counts.getOrElse(DeviceStatus.rejected, 0).toLong,
      cancelled = counts.getOrElse(DeviceStatus.cancelled, 0).toLong,
      finished =
        counts.getOrElse(DeviceStatus.successful, 0).toLong +
        counts.getOrElse(DeviceStatus.failed, 0).toLong
    )

    def processFailures(failedDevices: Set[DeviceUpdate], retryCampaigns: Set[Campaign]): Set[CampaignFailureStats] = {
      val missingErrorCode = ResultCode("MISSING_ERROR_CODE")
      val retryStatusByFailureCode = retryCampaigns
        .groupBy(_.failureCode.getOrElse(missingErrorCode))
        .mapValues { campaigns =>
          if (campaigns.exists(_.status == CampaignStatus.finished)) {
            RetryStatus.finished
          } else {
            RetryStatus.launched
          }
        }

      failedDevices
        .groupBy(_.resultCode.getOrElse(missingErrorCode))
        .map { case (errorCode, devices) => CampaignFailureStats(
          code = errorCode,
          count = devices.size.toLong,
          retryStatus = retryStatusByFailureCode.getOrElse(errorCode, RetryStatus.not_launched),
        )}
        .toSet
    }

    val statsAction = for {
      mainCampaign <- campaignRepo.findAction(campaignId)
      retryCampaigns <- campaignRepo.findRetryCampaignsOfAction(campaignId)
      retryCampaignIds = retryCampaigns.map(_.id)
      failedDevices <- findFailedDeviceUpdatesAction(retryCampaignIds + campaignId)
      mainCnt <- deviceUpdateRepo.countByStatus(Set(campaignId)).map(processCounts)
      retryCnt <- deviceUpdateRepo.countByStatus(retryCampaignIds).map(processCounts)
    } yield {
      val finished = mainCnt.finished - retryCnt.rejected - retryCnt.cancelled
      val failed = failedDevices.size.toLong
      val failures = processFailures(failedDevices, retryCampaigns)

      CampaignStats(
        campaign = campaignId,
        status = mainCampaign.status,
        processed = mainCnt.processed,
        affected  = mainCnt.affected - retryCnt.rejected,
        cancelled = mainCnt.cancelled + retryCnt.cancelled,
        finished = finished,
        failed = failed,
        successful = finished - failed,
        failures = failures,
      )
    }

    statsAction.transactionally
  }

  def cancel(campaignId: CampaignId): Future[Unit] = db.run {
    campaignStatusTransition.cancel(campaignId)
  }

  def launch(campaignId: CampaignId): Future[Unit] = db.run {
    val action = for {
      campaign <- campaignRepo.findAction(campaignId)
      _ <- if (campaign.status != CampaignStatus.prepared) {
        throw CampaignAlreadyLaunched
      } else {
        DBIO.successful(())
      }
      _ <- campaignRepo.setStatusAction(campaignId, CampaignStatus.launched)
    } yield ()

    action.transactionally
  }

  def create(campaign: Campaign, groups: Set[GroupId], devices: Set[DeviceId], metadata: Seq[CampaignMetadata]): Future[CampaignId] =
    campaignRepo.persist(campaign, groups, devices, metadata)

  /**
    * Create and immediately launch a retry-campaign for all the devices that were processed by `mainCampaign` and
    * failed with a code `failureCode`. There should be at least one failed device with that code. If there were not
    * any such devices in `mainCampaign`, an exception is thrown.
    */
  def retryCampaign(ns: Namespace, mainCampaign: Campaign, failureCode: ResultCode): Future[CampaignId] =
    findLatestFailedUpdates(mainCampaign.id, failureCode)
      .map(_.map(_.device))
      .flatMap {
        case deviceIds if deviceIds.isEmpty =>
          Future.failed(MissingFailedDevices(failureCode))

        case deviceIds =>
          val retryCampaign = Campaign(
            ns,
            CampaignId.generate(),
            s"retryCampaignWith-mainCampaign-${mainCampaign.id.uuid}-failureCode-$failureCode",
            mainCampaign.updateId,
            CampaignStatus.prepared,
            Instant.now(),
            Instant.now(),
            Some(mainCampaign.id),
            Some(failureCode)
          )
          create(retryCampaign, Set.empty, deviceIds, Nil)
    }
    .map { cid => launch(cid); cid }

  def update(id: CampaignId, name: String, metadata: Seq[CampaignMetadata]): Future[Unit] =
    campaignRepo.update(id, name, metadata)

  /**
   * Given a campaign and sets of accepted, scheduled and rejected
   * devices, calculates and updates campaign and devices statuses in the database.
   */
  def updateCampaignAndDevicesStatuses(
      campaign: Campaign,
      acceptedDeviceIds: Set[DeviceId],
      scheduledDeviceIds: Set[DeviceId],
      rejectedDeviceIds: Set[DeviceId]): Future[Unit] = {
    def persistDeviceUpdates(deviceIds: Set[DeviceId], status: DeviceStatus): DBIO[Unit] =
      deviceUpdateRepo.persistManyAction(deviceIds.toSeq.map(deviceId =>
        DeviceUpdate(campaign.id, campaign.updateId, deviceId, status)
      ))

    val action = for {
      _ <- persistDeviceUpdates(acceptedDeviceIds, DeviceStatus.accepted)
      _ <- persistDeviceUpdates(scheduledDeviceIds, DeviceStatus.scheduled)
      _ <- persistDeviceUpdates(rejectedDeviceIds, DeviceStatus.rejected)
      _ <- campaignStatusTransition.updateToCalculatedStatus(campaign.id)
    } yield ()

    db.run(action.transactionally)
  }
}

// TODO (OTA-2384) refactor and get rid of this class
protected [db] class CampaignStatusTransition(implicit db: Database, ec: ExecutionContext)
    extends CampaignSupport
    with CancelTaskSupport  {

  def devicesFinished(campaignId: CampaignId): DBIO[Unit] =
    updateToCalculatedStatus(campaignId)

  def cancel(campaignId: CampaignId): DBIO[Unit] = {
    val dbio = for {
      _ <- campaignRepo.findAction(campaignId)
      _ <- cancelTaskRepo.cancelAction(campaignId)
      _ <- campaignRepo.setStatusAction(campaignId, CampaignStatus.cancelled)
    } yield ()

    dbio.transactionally
  }

  protected[db] def updateToCalculatedStatus(campaignId: CampaignId): DBIO[Unit] =
    for {
      campaign <- campaignRepo.findAction(campaignId)
      campaignedFinished <- isFinished(campaignId, Some(campaign.status))
      _ <- if (campaignedFinished) campaignRepo.setStatusAction(campaignId, CampaignStatus.finished)
           else DBIO.successful(())
    } yield ()

  protected[db] def isFinished(campaignId: CampaignId,
                               currentCampaignStatus: Option[CampaignStatus] = None): DBIO[Boolean] = {
    val deviceCountByStatus = Schema.deviceUpdates
      .filter(_.campaignId === campaignId)
      .groupBy(_.status)
      .map(t => t._1 -> t._2.length)
      .result
      .map(_.toMap)

    for {
      affected <- deviceCountByStatus
        .map(_.filterKeys(k => k != DeviceStatus.requested && k != DeviceStatus.rejected))
        .map(_.values.sum)
      finished <- deviceCountByStatus
        .map(_.filterKeys(k => k == DeviceStatus.successful || k == DeviceStatus.failed || k == DeviceStatus.cancelled))
        .map(_.values.sum)
      requested <- deviceCountByStatus
        .map(_.filterKeys(k => k == DeviceStatus.requested))
        .map(_.values.sum)
      wasCampaignCancelled = currentCampaignStatus.contains(CampaignStatus.cancelled)
    } yield !wasCampaignCancelled && requested == 0 && affected == finished
  }
}
