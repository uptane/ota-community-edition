package com.advancedtelematic.ota.deviceregistry.daemon

import akka.Done
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest
import com.advancedtelematic.ota.deviceregistry.DatabaseSpec
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}


final class DeleteDeviceListenerSpec
    extends AnyFunSuite
    with Matchers
    with ScalaFutures
    with DatabaseSpec {

  import GeneratorOps._

  import scala.concurrent.ExecutionContext.Implicits.global
  val handler = new DeleteDeviceListener()

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(10, Millis))

  test("OTA-2445: do not fail when deleting non-existent device") {
    val msg = DeleteDeviceRequest(Gen.identifier.map(Namespace(_)).generate, Gen.uuid.map(DeviceId(_)).generate)
    handler(msg).futureValue shouldBe Done
  }
}
