package com.advancedtelematic.ota.deviceregistry.data

import java.nio.file.Paths

import akka.dispatch.ExecutionContexts
import com.advancedtelematic.ota.deviceregistry.SystemInfoUpdatePublisher
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures
import cats.syntax.either._
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers


class SystemInfoUpdatePublisherSpec extends AnyFunSuite
  with Matchers
  with ScalaFutures {

  implicit val ec = ExecutionContexts.global()

  lazy val messageBus = MessageBusPublisher.ignore

  val subject = new SystemInfoUpdatePublisher(messageBus)

  lazy val sampleJsonFile = Paths.get(this.getClass.getResource(s"/system-info.sample.json").toURI)

  lazy val sampleJson = io.circe.jawn.parseFile(sampleJsonFile.toFile).valueOr(throw _)

  test("can parse product out of device json") {
    val out = subject.parse(sampleJson)
    out.product should contain("Raspberry Pi 3 Model B")
  }

  test("parses product as None with invalid json") {
    val out = subject.parse(Map("id" -> "otherid").asJson)
    out.product shouldBe None
  }
}
