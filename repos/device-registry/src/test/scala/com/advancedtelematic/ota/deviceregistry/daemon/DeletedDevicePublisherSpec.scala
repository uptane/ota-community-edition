package com.advancedtelematic.ota.deviceregistry.daemon

import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import com.advancedtelematic.libats.messaging.test.MockMessageBus
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps.GenSample
import com.advancedtelematic.ota.deviceregistry.{DeviceRequests, ResourceSpec}
import cats.syntax.show._
import org.scalatest.OptionValues._
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.funsuite.AnyFunSuite


class DeletedDevicePublisherSpec extends AnyFunSuite with ResourceSpec with DeviceRequests with ScalaFutures {

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(15, Millis))

  val deleteDeviceHandler = new DeleteDeviceListener()
  val msgPub = new MockMessageBus
  val subject = new DeletedDevicePublisher(msgPub)

  test("deleted devices are published") {
    import org.scalatest.time.SpanSugar._

    val device = genDeviceT.generate
    val deviceId = createDeviceOk(device)

    fetchDevice(deviceId) ~> route ~> check {
      status shouldBe OK
    }

    // delete the device from the DB, no message bus involved
    deleteDeviceHandler(DeleteDeviceRequest(defaultNs, deviceId)).futureValue

    fetchDevice(deviceId) ~> route ~> check {
      status shouldBe NotFound
    }

    subject.run().futureValue

    eventually(timeout(5.seconds), interval(100.millis)) {
      val msg = msgPub.findReceived[DeleteDeviceRequest](deviceId.show)
      msg.value.uuid shouldBe deviceId
    }
  }
}
