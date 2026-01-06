package com.advancedtelematic.director.deviceregistry.device_monitoring

import org.apache.pekko.http.scaladsl.model.StatusCodes
import cats.syntax.show.*
import com.advancedtelematic.director.deviceregistry.data.DataType.ObservationPublishResult
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators
import com.advancedtelematic.director.http.deviceregistry.{
  DeviceRegistryResourceUri,
  RegistryDeviceRequests
}
import com.advancedtelematic.director.util.{DirectorSpec, ResourceSpec}
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId.*
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceMetricsObservation
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import org.scalatest.EitherValues.*
import org.scalatest.OptionValues.*
import org.scalatest.time.{Seconds, Span}

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}

object TestPayloads {

  val jsonPayload = io.circe.jawn
    .parse("""
      |{
      |    "cpu": {
      |        "cpu0.p_cpu": 0.19,
      |        "cpu0.p_system": 0.04666666666666667,
      |        "cpu0.p_user": 0.1433333333333333,
      |        "cpu1.p_cpu": 0.1933333333333333,
      |        "cpu1.p_system": 0.08333333333333333,
      |        "cpu1.p_user": 0.11,
      |        "cpu2.p_cpu": 0.2233333333333333,
      |        "cpu2.p_system": 0.06666666666666667,
      |        "cpu2.p_user": 0.1566666666666667,
      |        "cpu3.p_cpu": 0.22,
      |        "cpu3.p_system": 0.04666666666666667,
      |        "cpu3.p_user": 0.1733333333333333,
      |        "cpu4.p_cpu": 0.1333333333333333,
      |        "cpu4.p_system": 0.03666666666666667,
      |        "cpu4.p_user": 0.09666666666666666,
      |        "cpu5.p_cpu": 0.1533333333333333,
      |        "cpu5.p_system": 0.04666666666666667,
      |        "cpu5.p_user": 0.1066666666666667,
      |        "cpu_p": 0.1855555555555556,
      |        "system_p": 0.05444444444444444,
      |        "user_p": 0.1311111111111111
      |    },
      |    "date": 1621930398.017631,
      |    "docker": {
      |        "alive": true,
      |        "pid": 839,
      |        "proc_name": "dockerd"
      |    },
      |    "memory": {
      |        "Mem.free": 3105676,
      |        "Mem.total": 3797652,
      |        "Mem.used": 691976,
      |        "Swap.free": 0,
      |        "Swap.total": 0,
      |        "Swap.used": 0
      |    },
      |    "temperature": {
      |        "name": "thermal_zone0",
      |        "temp": 69.1,
      |        "type": "cpu-thermal0"
      |    }
      |}
      |""".stripMargin)
    .value

  val jsonPayloadBufferedList = io.circe.jawn
    .parse("""
      |[
      |{
      |    "cpu": {
      |        "cpu0.p_cpu": 0.19,
      |        "cpu0.p_system": 0.04666666666666667,
      |        "cpu0.p_user": 0.1433333333333333,
      |        "cpu1.p_cpu": 0.1933333333333333,
      |        "cpu1.p_system": 0.08333333333333333,
      |        "cpu1.p_user": 0.11,
      |        "cpu2.p_cpu": 0.2233333333333333,
      |        "cpu2.p_system": 0.06666666666666667,
      |        "cpu2.p_user": 0.1566666666666667,
      |        "cpu3.p_cpu": 0.22,
      |        "cpu3.p_system": 0.04666666666666667,
      |        "cpu3.p_user": 0.1733333333333333,
      |        "cpu4.p_cpu": 0.1333333333333333,
      |        "cpu4.p_system": 0.03666666666666667,
      |        "cpu4.p_user": 0.09666666666666666,
      |        "cpu5.p_cpu": 0.1533333333333333,
      |        "cpu5.p_system": 0.04666666666666667,
      |        "cpu5.p_user": 0.1066666666666667,
      |        "cpu_p": 0.1855555555555556,
      |        "system_p": 0.05444444444444444,
      |        "user_p": 0.1311111111111111
      |    },
      |    "date": 1621930398.017631
      |},
      |{
      |    "date": 1678390521.739909,
      |    "cpu": {
      |      "cpu_p": 3.25,
      |      "cpu5.p_system": 1.3
      |    },
      |    "docker": {
      |        "alive": true,
      |        "pid": 839,
      |        "proc_name": "dockerd"
      |    },
      |    "memory": {
      |        "Mem.free": 3105676,
      |        "Mem.total": 3797652,
      |        "Mem.used": 691976,
      |        "Swap.free": 0,
      |        "Swap.total": 0,
      |        "Swap.used": 0
      |    },
      |    "temperature": {
      |        "name": "thermal_zone0",
      |        "temp": 69.1,
      |        "type": "cpu-thermal0"
      |    }
      |}
      |]
      |""".stripMargin)
    .value

}

class DeviceMonitoringResourceSpec extends DirectorSpec with ResourceSpec with RegistryDeviceRequests {

  import DeviceGenerators.*
  import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*

  override implicit def patienceConfig: PatienceConfig =
    super.patienceConfig.copy(timeout = Span(3, Seconds))

  test("accepts metrics from device") {
    val uuid = createDeviceOk(genDeviceT.generate)

    Post(
      DeviceRegistryResourceUri.uri("devices", uuid.show, "monitoring"),
      TestPayloads.jsonPayload
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val msg = msgPub.findReceived[DeviceMetricsObservation]((msg: DeviceMetricsObservation) =>
      msg.uuid == uuid
    )

    msg.value.payload shouldBe TestPayloads.jsonPayload
    msg.value.namespace shouldBe defaultNs
  }

  test("accepts metrics from device when they are buffered as a list") {
    val uuid = createDeviceOk(genDeviceT.generate)

    Post(
      DeviceRegistryResourceUri.uri("devices", uuid.show, "monitoring", "fluentbit-metrics"),
      TestPayloads.jsonPayloadBufferedList
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val msgs =
      msgPub.findReceivedAll[DeviceMetricsObservation]((msg: DeviceMetricsObservation) =>
        msg.uuid == uuid
      )
    TestPayloads.jsonPayloadBufferedList.asArray.map(_.map { j =>
      msgs.map(_.payload) should contain(j)
      msgs.map { msg =>
        msg.namespace shouldBe defaultNs
      }
    })
  }

}

class BadMessageBus extends MockMessageBus {

  override def publish[T](
    msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit] =
    Future.failed(new IOException)

}

class DeviceMonitoringResourceSpecBadMsgPub
    extends DirectorSpec
    with ResourceSpec
    with RegistryDeviceRequests {

  import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*
  import DeviceGenerators.*

  override implicit def patienceConfig: PatienceConfig =
    super.patienceConfig.copy(timeout = Span(3, Seconds))

  override val msgPub: MockMessageBus = new BadMessageBus()

  test("Buffered metrics that failed to be published are returned with partial results body") {
    import com.advancedtelematic.director.deviceregistry.data.Codecs.ObservationPublishResultCodec
    val uuid = createDeviceOk(genDeviceT.generate)

    Post(
      DeviceRegistryResourceUri.uri("devices", uuid.show, "monitoring", "fluentbit-metrics"),
      TestPayloads.jsonPayloadBufferedList
    ) ~> routes ~> check {
      status shouldBe StatusCodes.RangeNotSatisfiable
      responseAs[List[ObservationPublishResult]].count(_.publishedSuccessfully == false) shouldBe 2
    }
  }

}
