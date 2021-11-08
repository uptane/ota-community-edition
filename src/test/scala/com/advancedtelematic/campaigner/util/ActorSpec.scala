package com.advancedtelematic.campaigner.util

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import com.advancedtelematic.campaigner.Settings
import com.advancedtelematic.libats.test.DatabaseSpec
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}
import scala.concurrent.ExecutionContext

abstract class ActorSpec[T](implicit m: reflect.Manifest[T])
    extends TestKit(ActorSystem(m.toString.split("""\.""").last + "Spec"))
    with FlatSpecLike
    with Settings
    with BeforeAndAfterAll
    with DatabaseSpec {

  implicit lazy val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  lazy val deviceRegistry = new FakeDeviceRegistry()
  lazy val director = new FakeDirectorClient()
  val batch = schedulerBatchSize.toInt

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
    system.terminate()
  }

}
