package com.advancedtelematic.campaigner.db

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Source
import cats.data.NonEmptyList
import com.advancedtelematic.campaigner.data.DataType.CampaignStatus._
import com.advancedtelematic.campaigner.data.DataType.CancelTaskStatus.CancelTaskStatus
import com.advancedtelematic.campaigner.data.DataType.DeviceStatus._
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.campaigner.data.DataType.UpdateType.UpdateType
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.Errors.{CampaignFKViolation, UpdateFKViolation}
import com.advancedtelematic.campaigner.db.SlickMapping._
import com.advancedtelematic.campaigner.db.SlickUtil.{sortBySlickOrderedCampaignConversion, sortBySlickOrderedUpdateConversion}
import com.advancedtelematic.campaigner.http.Errors._
import com.advancedtelematic.libats.data.DataType.{Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.{DeviceId, UpdateId}
import com.advancedtelematic.libats.slick.db.SlickAnyVal._
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._

import java.util.UUID
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

case class Repositories(campaignRepo: CampaignRepository,
                        deviceUpdateRepo: DeviceUpdateRepository,
                        cancelTaskRepo: CancelTaskRepository,
                        updateRepo: UpdateRepository,
                        campaignMetadataRepo: CampaignMetadataRepository)

object Repositories {
  def apply()(implicit db: Database, ec: ExecutionContext): Repositories =
    Repositories(
      new CampaignRepository(),
      new DeviceUpdateRepository(),
      new CancelTaskRepository(),
      new UpdateRepository(),
      new CampaignMetadataRepository()
    )
}

class CampaignMetadataRepository()(implicit db: Database) {
  def findFor(campaign: CampaignId): Future[Seq[CampaignMetadata]] = db.run {
    Schema.campaignMetadata.filter(_.campaignId === campaign).result
  }
}


class DeviceUpdateRepository()(implicit db: Database, ec: ExecutionContext) {

  def findDeviceCampaigns(deviceId: DeviceId, status: DeviceStatus*): Future[Seq[(Campaign, Option[CampaignMetadata])]] = db.run {
    assert(status.nonEmpty)

    Schema.deviceUpdates
      .filter { d => d.deviceId === deviceId && d.status.inSet(status) }
      .join(Schema.campaigns).on(_.campaignId === _.id)
      .joinLeft(Schema.campaignMetadata).on { case ((d, c), m) => c.id === m.campaignId }
      .map { case ((d, c), m) => (c, m) }
      .result
  }

  def findAllByCampaign(campaign: CampaignId): Future[Set[DeviceId]] =
    db.run(Schema.deviceUpdates.filter(_.campaignId === campaign).map(_.deviceId).result.map(_.toSet))

  def findByCampaign(campaign: CampaignId, status: DeviceStatus): Future[Set[DeviceId]] =
    db.run(findByCampaignAction(campaign, status))

  private val findUpdateStatusByDeviceCampaignQuery =
    Compiled { (campaignId: Rep[CampaignId], deviceId: Rep[DeviceId]) =>
      Schema.deviceUpdates
        .filter(_.campaignId === campaignId)
        .filter(_.deviceId === deviceId)
        .map(_.status)
  }

  def findUpdateStatusByDeviceCampaign(campaign: CampaignId, deviceId: DeviceId): Future[Option[DeviceStatus]] =
    db.run(findUpdateStatusByDeviceCampaignQuery((campaign, deviceId)).result.headOption)

  protected[db] def findByCampaignAction(campaign: CampaignId, status: DeviceStatus): DBIO[Set[DeviceId]] =
    Schema.deviceUpdates
      .filter(_.campaignId === campaign)
      .filter(_.status === status)
      .map(_.deviceId)
      .result
      .map(_.toSet)

  def findByCampaignStream(campaign: CampaignId, status: DeviceStatus*): Source[(DeviceId, DeviceStatus), NotUsed] =
    Source.fromPublisher {
      db.stream {
        Schema.deviceUpdates
          .filter(_.campaignId === campaign)
          .filter(_.status.inSet(status))
          .map { r => r.deviceId -> r.status }
          .result
          .withStatementParameters(fetchSize = 1024)
      }
    }

  /**
   * Returns the IDs of all the devices that were processed in the campaign with `campaignId` and failed with
   * the code `failureCode`.
   */
  protected[db] def findFailedByFailureCode(campaignId: CampaignId, failureCode: ResultCode): Future[Set[DeviceId]] = db.run {
    Schema.deviceUpdates
      .filter(_.campaignId === campaignId)
      .filter(_.status === DeviceStatus.failed)
      .filter(_.resultCode === failureCode)
      .map(_.deviceId)
      .result
      .map(_.toSet)
  }

  protected[db] def setUpdateStatusAction(campaign: CampaignId, devices: Seq[DeviceId], status: DeviceStatus, resultCode: Option[ResultCode], resultDescription: Option[ResultDescription]): DBIO[Unit] = {
    val cappedResultDescription = resultDescription.map(rd => ResultDescription(rd.value.take(1024)))

    Schema.deviceUpdates
      .filter(_.campaignId === campaign)
      .filter(_.deviceId inSet devices)
      .map(du => (du.status, du.resultCode, du.resultDescription))
      .update((status, resultCode, cappedResultDescription))
      .flatMap {
        case n if devices.length == n => DBIO.successful(())
        case _ => DBIO.failed(DeviceNotScheduled)
      }.map(_ => ())
  }


  def persistMany(updates: Seq[DeviceUpdate]): Future[Unit] =
    db.run(persistManyAction(updates))

  def persistManyAction(updates: Seq[DeviceUpdate]): DBIO[Unit] =
    DBIO.sequence(updates.map(Schema.deviceUpdates.insertOrUpdate)).transactionally.map(_ => ())

  /**
   * Given a set of campaigns, finds device updates happened in them, groups them
   * by status and counts.
   */
  def countByStatus(campaignIds: Set[CampaignId]): DBIO[Map[DeviceStatus, Int]] = {
    Schema.deviceUpdates
      .filter(_.campaignId.inSet(campaignIds))
      .groupBy(_.status)
      .map { case (st, upds) => (st, upds.size) }
      .result
      .map(_.toMap)
  }
}

class CampaignRepository()(implicit db: Database, ec: ExecutionContext) {
  import com.advancedtelematic.libats.slick.db.SlickAnyVal._

  def persist(campaign: Campaign, groups: Set[GroupId], devices: Set[DeviceId], metadata: Seq[CampaignMetadata]): Future[CampaignId] = db.run {
    val f = for {
      _ <- (Schema.campaigns += campaign).recover {
        case Failure(UpdateFKViolation()) => DBIO.failed(MissingUpdateSource)
        case Failure(CampaignFKViolation()) => DBIO.failed(MissingMainCampaign)
      }.handleIntegrityErrors(ConflictingCampaign)
      _ <- Schema.campaignGroups ++= groups.map(campaign.id -> _)
      _ <- Schema.deviceUpdates ++= devices.map(did => DeviceUpdate(campaign.id, campaign.updateId, did, DeviceStatus.requested))
      _ <- (Schema.campaignMetadata ++= metadata).handleIntegrityErrors(ConflictingMetadata)
    } yield campaign.id

    f.transactionally
  }

  val findCampaignStatusQuery = Compiled((id: Rep[CampaignId]) => Schema.campaigns.filter(_.id === id).map(_.status))

  protected[db] def findCampaignStatusAction(campaignId: CampaignId): DBIO[CampaignStatus] =
    findCampaignStatusQuery(campaignId).result.failIfNotSingle(CampaignMissing)

  protected[db] def findAction(campaign: CampaignId, ns: Option[Namespace] = None): DBIO[Campaign] =
    Schema.campaigns
      .maybeFilter(_.namespace === ns)
      .filter(_.id === campaign)
      .result
      .failIfNotSingle(CampaignMissing)

  protected[db] def findByUpdateAction(update: UpdateId): DBIO[Seq[Campaign]] =
    Schema.campaigns.filter(_.update === update).result

  def find(campaign: CampaignId, ns: Option[Namespace] = None): Future[Campaign] =
    db.run(findAction(campaign, ns))

  def all(ns: Namespace, sortBy: SortBy, offset: Long, limit: Long, status: Option[CampaignStatus], nameContains: Option[String]): Future[PaginationResult[Campaign]] = {
    db.run {
      Schema.campaigns
        .filter(_.namespace === ns)
        .filter(_.mainCampaignId.isEmpty)
        .maybeFilter(_.status === status)
        .maybeContains(_.name, nameContains)
        .sortBy(sortBy)
        .paginateResult(offset, limit)
    }
  }

  /**
   * Return all the failed campaigns, i.e campaigns with at least one failed device.
   */
  def allWithErrors(ns: Namespace, sortBy: SortBy, offset: Long, limit: Long): Future[PaginationResult[Campaign]] =
    db.run {
      Schema.campaigns
        .filter(_.namespace === ns)
        .join(Schema.deviceUpdates)
        .on(_.id === _.campaignId)
        .filter(_._2.status === DeviceStatus.failed)
        .map(_._1)
        .distinct
        .sortBy(sortBy)
        .paginateResult(offset, limit)
    }

  /**
   * Returns all campaigns that have at least one device in `requested` state
   */
  def findAllWithRequestedDevices: DBIO[Set[Campaign]] =
    Schema.deviceUpdates.join(Schema.campaigns.filter(_.status =!= CampaignStatus.cancelled)).on(_.campaignId === _.id)
      .filter { case (deviceUpdate, _) => deviceUpdate.status === DeviceStatus.requested }
      .map(_._2)
      .result
      .map(_.toSet)

  def findAllLaunched: DBIO[Set[Campaign]] = {
    implicit val gr = GetResult(r => CampaignId(UUID.fromString(r.nextString)))

    // I brought back this query because it keeps the campaign supervisor actor from picking up
    // and scheduling campaigns indefinitely. There should be a less surprising way to do this.
    val queryAllNewlyCreatedCampaignIds = sql"""
      select campaign_id
      from device_updates
      group by campaign_id
      having group_concat(distinct status) = 'requested';
    """.as[CampaignId]

    queryAllNewlyCreatedCampaignIds.flatMap { cids =>
      Schema.campaigns
        .filter(_.status === CampaignStatus.launched)
        .filter(_.id inSet cids)
        .result
        .map(_.toSet)
    }
  }

  def update(campaign: CampaignId, name: String, metadata: Seq[CampaignMetadata]): Future[Unit] =
    db.run {
      findAction(campaign).flatMap { _ =>
        Schema.campaigns
          .filter(_.id === campaign)
          .map(_.name)
          .update(name)
          .handleIntegrityErrors(ConflictingCampaign)
      }.andThen {
        Schema.campaignMetadata.filter(_.campaignId === campaign).delete
      }.andThen {
        (Schema.campaignMetadata ++= metadata).handleIntegrityErrors(ConflictingMetadata)
      }.map(_ => ())
    }

  def countByStatus: DBIO[Map[CampaignStatus, Int]] =
    Schema.campaigns
    .groupBy(_.status)
    .map { case (status, campaigns) => status -> campaigns.length }
    .result
    .map(_.toMap)

  def setStatusAction(campaignId: CampaignId, status: CampaignStatus): DBIO[CampaignId] =
    Schema.campaigns
      .filter(_.id === campaignId).map(_.status).update(status).map(_ => campaignId)

  /**
   * Given a main campaign ID, finds all the corresponding retry campaigns.
   */
  def findRetryCampaignsOf(mainId: CampaignId): Future[Set[Campaign]] =
    db.run(findRetryCampaignsOfAction(mainId))

  /**
   * Given a main campaign ID, finds all the corresponding retry campaigns.
   */
  protected[db] def findRetryCampaignsOfAction(mainId: CampaignId): DBIO[Set[Campaign]] =
    Schema.campaigns
      .filter(_.mainCampaignId === mainId)
      .result
      .map(_.toSet)
}


class CancelTaskRepository()(implicit db: Database, ec: ExecutionContext) {
  protected [db] def cancelAction(campaign: CampaignId): DBIO[CancelTask] = {
    val cancel = CancelTask(campaign, CancelTaskStatus.pending)
    (Schema.cancelTasks += cancel)
      .map(_ => cancel)
  }

  private val cancelTaskExistsQuery =
    Compiled((campaignId: Rep[CampaignId]) => Schema.cancelTasks.filter(_.campaignId === campaignId).exists)

  def isCancelled(campaignId: CampaignId): Future[Boolean] =
    db.run(cancelTaskExistsQuery(campaignId).result)

  def setStatus(campaign: CampaignId, status: CancelTaskStatus): Future[Done] = db.run {
    Schema.cancelTasks
      .filter(_.campaignId === campaign)
      .map(_.taskStatus)
      .update(status)
      .map(_ => Done)
  }

  private def findStatus(status: CancelTaskStatus): DBIO[Seq[(Namespace, CampaignId)]] =
    Schema.cancelTasks
      .filter(_.taskStatus === status)
      .join(Schema.campaigns).on { case (task, campaign) => task.campaignId === campaign.id }
      .map(_._2)
      .map(c => (c.namespace, c.id))
      .distinct
      .result

  def findPending(): Future[Seq[(Namespace, CampaignId)]] = db.run {
    findStatus(CancelTaskStatus.pending)
  }

  def findInprogress(): Future[Seq[(Namespace, CampaignId)]] = db.run {
    findStatus(CancelTaskStatus.inprogress)
  }
}

class UpdateRepository()(implicit db: Database, ec: ExecutionContext) {
  import com.advancedtelematic.libats.slick.db.SlickPagination._

  def persist(update: Update): Future[UpdateId] = db.run {
    (Schema.updates += update).map(_ => update.uuid).handleIntegrityErrors(ConflictingUpdate)
  }

  def findById(id: UpdateId): Future[Update] = db.run {
    Schema.updates.filter(_.uuid === id).result.failIfNotSingle(MissingUpdate(id))
  }

  def findByIds(ns: Namespace, ids: NonEmptyList[UpdateId]): Future[List[Update]] = db.run(
    Schema.updates.filter(xs => xs.namespace === ns && xs.uuid.inSet(ids.toList)).to[List].result
  )

  private def findByExternalIdsAction(ns: Namespace, ids: Seq[ExternalUpdateId]): DBIO[Seq[Update]] =
    Schema.updates.filter(_.namespace === ns).filter(_.updateId.inSet(ids)).result

  def findByExternalIds(ns: Namespace, ids: Seq[ExternalUpdateId]): Future[Seq[Update]] = db.run {
    findByExternalIdsAction(ns, ids)
  }

  def findByExternalId(ns: Namespace, id: ExternalUpdateId): Future[Update] = db.run {
    findByExternalIdsAction(ns, Seq(id)).failIfNotSingle(MissingExternalUpdate(id))
  }

  def all(ns: Namespace, sortBy: SortBy = SortBy.Name, nameContains: Option[String] = None, updateType: Option[UpdateType] = None): Future[Seq[Update]] = db.run {
    Schema.updates
      .filter(_.namespace === ns)
      .maybeFilter(_.updateSourceType === updateType)
      .maybeContains(_.name, nameContains)
      .sortBy(sortBy)
      .result
  }

  def allPaginated(ns: Namespace, sortBy: SortBy, offset: Long, limit: Long, nameContains: Option[String]): Future[PaginationResult[Update]] = db.run {
    Schema.updates
      .filter(_.namespace === ns)
      .maybeContains(_.name, nameContains)
      .paginateAndSortResult(sortBy, offset, limit)
  }
}
