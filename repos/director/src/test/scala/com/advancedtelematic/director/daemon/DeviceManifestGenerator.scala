package com.advancedtelematic.director.daemon

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.director.data.Generators.*
import com.advancedtelematic.director.data.Messages
import com.advancedtelematic.director.data.Messages.{
  deviceManifestReportedMsgLike,
  DeviceManifestReported
}
import com.advancedtelematic.libats.data.DataType
import com.advancedtelematic.libats.messaging.{MessageBus, MessageBusPublisher}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import com.typesafe.config.ConfigFactory
import io.circe.syntax.*

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

object DeviceManifestGenerator {

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: DeviceManifestGenerator <number_of_manifests> [device-id]")
      System.exit(1)
    }

    val numManifests = args(0).toInt

    val deviceId =
      Option(args(1)).map(d => DeviceId(UUID.fromString(d)))

    implicit val system: ActorSystem = ActorSystem("DeviceManifestGenerator")
    implicit val ec: ExecutionContext = system.dispatcher

    val config = ConfigFactory.load()
    val namespace = DataType.Namespace("device-manifest-generator")

    implicit val msgPublisher: MessageBusPublisher = MessageBus.publisher(system, config)

    println(s"Generating $numManifests random DeviceManifestReported messages...")

    val manifests = (1 to numManifests).map { _ =>
      val id = deviceId.getOrElse(DeviceId.generate())
      val manifest = GenDeviceManifest.generate
      val signedManifest = SignedPayload(Seq.empty, manifest.asJson, manifest.asJson)

      Messages.DeviceManifestReported(namespace, id, signedManifest, Instant.now())
    }

    val result = Source(manifests)
      .via(Flow[DeviceManifestReported].mapAsync(3) { msg =>
        println(s"Publishing message for device: ${msg.deviceId}")
        msgPublisher.publish(msg)
      })
      .runWith(Sink.ignore)

    Await.result(result, Duration.Inf)

    Await.result(system.terminate(), 5.seconds)
  }

}
