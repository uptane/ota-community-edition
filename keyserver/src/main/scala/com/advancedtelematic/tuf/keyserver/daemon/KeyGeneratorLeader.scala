package com.advancedtelematic.tuf.keyserver.daemon

import org.apache.pekko.actor.Status.{Failure, Success}
import org.apache.pekko.actor.{Actor, ActorLogging, Props, Status, SupervisorStrategy}
import org.apache.pekko.routing.RoundRobinPool
import cats.syntax.show._
import com.advancedtelematic.tuf.keyserver.daemon.KeyGeneratorLeader.Tick
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType._
import com.advancedtelematic.libtuf.data.TufDataType._
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libtuf.crypt.TufCrypto

import scala.async.Async._
import scala.concurrent.duration._
import org.apache.pekko.pattern.pipe
import com.advancedtelematic.tuf.keyserver.daemon.KeyGenerationOp.KeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus
import com.advancedtelematic.tuf.keyserver.db.{KeyGenRequestSupport, KeyRepositorySupport}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object KeyGeneratorLeader {
  case object Tick

  def props()(implicit db: Database, ec: ExecutionContext): Props =
    Props(new KeyGeneratorLeader(DefaultKeyGenerationOp()))

  def props(keyGenerationOp: KeyGenRequest => Future[Seq[Key]])(implicit db: Database): Props =
    Props(new KeyGeneratorLeader(keyGenerationOp))

}

class KeyGeneratorLeader(keyGenerationOp: KeyGenRequest => Future[Seq[Key]])(
  implicit val db: Database)
    extends Actor
    with ActorLogging
    with KeyGenRequestSupport {

  implicit val ec: scala.concurrent.ExecutionContextExecutor = context.dispatcher

  private val WORKER_COUNT = 10

  override def preStart(): Unit =
    self ! Tick

  private val router = {
    val routerProps = RoundRobinPool(WORKER_COUNT)
      .withSupervisorStrategy(SupervisorStrategy.defaultStrategy)
      .props(KeyGeneratorWorker.props(keyGenerationOp))

    context.system.actorOf(routerProps)
  }

  def waiting(totalTasks: Int, remaining: Int): Receive =
    if (remaining == 0) {
      val nextInterval = if (totalTasks > 0) 0.seconds else 3.seconds
      log.info("Finished generating {} keys", totalTasks)
      context.system.scheduler.scheduleOnce(nextInterval, self, Tick)
      receive
    } else
      {
        case Status.Success(_) =>
          context.become(waiting(totalTasks, remaining - 1))
        case Status.Failure(ex) =>
          log.error(ex, "Could not generate key")
          context.become(waiting(totalTasks, remaining - 1))
      }

  override def receive: Receive = {
    case Status.Failure(ex) =>
      throw ex

    case taskCount: Int =>
      log.info("Waiting for {} key generation tasks to complete", taskCount)
      context.become(waiting(taskCount, taskCount))

    case Tick =>
      log.info("Tick")

      val f = keyGenRepo.findPending(limit = WORKER_COUNT * 4).map { m =>
        m.foreach(router ! _)
        m.size
      }

      f.pipeTo(self)
  }

}

object KeyGenerationOp {
  type KeyGenerationOp = KeyGenRequest => Future[Seq[Key]]
}

object DefaultKeyGenerationOp {

  def apply()(implicit db: Database, ec: ExecutionContext): DefaultKeyGenerationOp =
    new DefaultKeyGenerationOp()

}

class DefaultKeyGenerationOp()(implicit val db: Database, val ec: ExecutionContext)
    extends KeyGenerationOp
    with KeyGenRequestSupport
    with KeyRepositorySupport {

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  protected def generateKeys(kgr: KeyGenRequest): Seq[(Key, TufKeyPair)] = {
    require(kgr.threshold > 0, "threshold must be greater than 0")

    (0 until kgr.threshold).map { _ =>
      val pair = TufCrypto.generateKeyPair(kgr.keyType, kgr.keySize)
      (Key(pair.pubkey.id, kgr.repoId, kgr.roleType, kgr.keyType, pair.pubkey, pair.privkey), pair)
    }
  }

  def apply(kgr: KeyGenRequest): Future[Seq[Key]] =
    async {
      val keyPairs = generateKeys(kgr)
      val keys = keyPairs.map(_._1)

      await(keyGenRepo.persistGenerated(kgr, keys, keyRepo))

      _log.info("Generated keys {}", keys.map(_.id.value).mkString(","))
      keys
    }

}

object KeyGeneratorWorker {

  def props(keyGenerationOp: KeyGenRequest => Future[Seq[Key]])(implicit db: Database): Props =
    Props(new KeyGeneratorWorker(keyGenerationOp))

}

class KeyGeneratorWorker(keyGenerationOp: KeyGenRequest => Future[Seq[Key]])(
  implicit val db: Database)
    extends Actor
    with ActorLogging
    with KeyGenRequestSupport
    with KeyRepositorySupport {

  implicit val ec: scala.concurrent.ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = { case kgr: KeyGenRequest =>
    log.info(s"Received key gen request for {}", kgr.id.show)

    keyGenerationOp(kgr)
      .map(Success.apply)
      .recoverWith { case ex =>
        log.error("Key generation failed: {}", ex.getMessage)
        keyGenRepo
          .setStatus(kgr.id, KeyGenRequestStatus.ERROR, Option(ex))
          .map(_ => Failure(ex))
      }
      .pipeTo(sender())
  }

}
