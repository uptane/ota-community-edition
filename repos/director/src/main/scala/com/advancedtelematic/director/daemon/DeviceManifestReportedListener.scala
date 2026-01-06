package com.advancedtelematic.director.daemon

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.CommitterSettings
import org.apache.pekko.kafka.ConsumerMessage.{CommittableMessage, CommittableOffsetBatch}
import org.apache.pekko.kafka.scaladsl.Committer
import org.apache.pekko.stream.scaladsl.{Flow, RestartSource, Sink}
import org.apache.pekko.stream.{Attributes, RestartSettings}
import com.advancedtelematic.director.VersionInfo
import com.advancedtelematic.director.data.Messages
import com.advancedtelematic.director.data.Messages.{
  deviceManifestReportedMsgLike,
  DeviceManifestReported
}
import com.advancedtelematic.director.db.DeviceManifestRepositorySupport
import com.advancedtelematic.libats.messaging.kafka.KafkaClient
import com.advancedtelematic.metrics.MetricsSupport
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

class DeviceManifestReportedListener(globalConfig: Config)(
  implicit val db: Database,
  val ec: ExecutionContext,
  val system: ActorSystem)
    extends DeviceManifestRepositorySupport
    with VersionInfo {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private lazy val committerSettings =
    CommitterSettings(globalConfig.getConfig("ats.messaging.kafka.committer"))

  private lazy val groupInstanceId =
    if (globalConfig.hasPath(s"ats.$projectName.consumers.instance-id"))
      Some(
        s"$projectName-${Messages.deviceManifestReportedMsgLike.streamName}-${globalConfig.getString(s"ats.$projectName.consumers.instance-id")}"
      )
    else
      None

  protected[director] val processingFlow
    : Flow[CommittableMessage[Array[Byte], DeviceManifestReported], Seq[
      CommittableMessage[Array[Byte], DeviceManifestReported]
    ], NotUsed] =
    Flow[CommittableMessage[Array[Byte], DeviceManifestReported]]
      .groupedWithin(100, 3.seconds)
      .mapAsync(1) { group =>
        val manifests = group.map { msg =>
          val manifestMsg = msg.record.value()
          (manifestMsg.deviceId, manifestMsg.manifest.signed, manifestMsg.receivedAt)
        }

        system.log.info(s"Received ${manifests.size} manifests")

        deviceManifestRepository
          .createOrUpdate(manifests)
          .map(_ => group)
      }

  def start(): Future[Unit] = {
    if (globalConfig.getString("ats.messaging.mode") != "kafka") {
      log.warn("device-manifest-reported-listener is disabled, as messaging mode is not kafka")
      return Future.successful(())
    }

    val restartSettings = RestartSettings(1.second, 30.seconds, 0.1)
    val committerFlow = Committer.flow(committerSettings)

    lazy val counter = MetricsSupport.metricRegistry.counter(
      s"director.${deviceManifestReportedMsgLike.streamName}.subscriptions"
    )

    lazy val failureCount = MetricsSupport.metricRegistry.counter(
      s"director.${deviceManifestReportedMsgLike.streamName}.failures"
    )

    lazy val batchSizeHist = MetricsSupport.metricRegistry.histogram(
      s"director.${deviceManifestReportedMsgLike.streamName}.batch-size"
    )

    RestartSource
      .withBackoff(restartSettings) { () =>
        counter.inc()
        KafkaClient
          .committableSource[DeviceManifestReported](globalConfig, projectName, groupInstanceId)
          .via(processingFlow)
          .map { group =>
            batchSizeHist.update(group.size)
            CommittableOffsetBatch(group.map(_.committableOffset))
          }
          .log("device-manifest-reported-listener")
          .addAttributes(
            Attributes
              .logLevels(onFinish = Attributes.logLevelWarning)
          )
          .wireTap(batch => batch.offsets.size )
          .via(committerFlow)
      }
      .watchTermination() { (_, done) =>
        done
          .failed
          .map { err => failureCount.inc()
            log.error("device-manifest-reported-listener failed", err)
          }
        NotUsed
      }
      .runWith(Sink.ignore)
      .map(_ => ())
  }

}
