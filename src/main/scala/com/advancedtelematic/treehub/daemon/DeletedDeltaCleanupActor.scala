package com.advancedtelematic.treehub.daemon

import akka.actor.{Actor, ActorLogging, Props}
import akka.actor.Status.Failure
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import com.advancedtelematic.data.DataType.StaticDeltaMeta
import com.advancedtelematic.treehub.daemon.DeletedDeltaCleanupActor.{Done, Tick, defaultTickInterval}
import com.advancedtelematic.treehub.db.StaticDeltaMetaRepositorySupport
import com.advancedtelematic.treehub.object_store.BlobStore
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object DeletedDeltaCleanupActor {
  case object Tick
  case class Done(count: Long, failedCount: Long)

  private val defaultTickInterval = 10.minutes

  def props(storage: BlobStore, tickInterval: FiniteDuration = defaultTickInterval, autoStart: Boolean = false)(implicit db: Database, ec: ExecutionContext) =
    Props(new DeletedDeltaCleanupActor(storage, tickInterval, autoStart))

  def withBackOff(blobStore: BlobStore, tickInterval: FiniteDuration = defaultTickInterval, autoStart: Boolean = false)(implicit db: Database, ec: ExecutionContext) =
    BackoffSupervisor.props(BackoffOpts.onFailure(props(blobStore, tickInterval, autoStart), "deleted-deltas-worker", 10.minutes, 1.hour, 0.25))
}

class DeletedDeltaCleanupActor(storage: BlobStore, tickInterval: FiniteDuration = defaultTickInterval, autoStart: Boolean = false)(implicit val db: Database, ec: ExecutionContext) extends Actor
  with StaticDeltaMetaRepositorySupport
  with ActorLogging {

  import akka.pattern.pipe

  import scala.async.Async.*

  override def preStart(): Unit = {
    if(autoStart)
      self ! Tick
  }

  def cleanupDelta(delta: StaticDeltaMeta): Future[Boolean] =
    storage.deleteObjects(delta.namespace, delta.id.asPrefixedPath)
      .flatMap { _ =>
        staticDeltaMetaRepository.delete(delta.namespace, delta.id)
          .map { _ => true }
      }
      .recover { case ex =>
        log.error(ex, s"Failed to delete delta ${delta.id}")
        false
      }

  def processAll: Future[Done] = async {
    val deltas = await { staticDeltaMetaRepository.findByStatus(StaticDeltaMeta.Status.Deleted) }

    val failedCount = await { Future.sequence(deltas.map(cleanupDelta)) }.count(_ == false)

    Done(deltas.length, failedCount)
  }

  override def receive: Receive = {
    case Done(count, failedCount) =>
      if(count > 0)
        log.info(s"Batch done, processed ${count - failedCount} objects successfully - with $failedCount failures")
      else
        log.debug("Tick, scheduling next execution")

      context.system.scheduler.scheduleOnce(tickInterval, self, Tick)

    case Failure(ex) =>
      throw ex

    case Tick =>
      processAll.pipeTo(sender())
  }
}