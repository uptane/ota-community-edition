package com.advancedtelematic.director.util

import akka.http.scaladsl.testkit.RouteTestTimeout
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Seconds, Span}

trait DefaultPatience extends PatienceConfiguration {
  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(10, Seconds), interval = Span(500, Millis))

  implicit val defaultTimeout = RouteTestTimeout(Span(5, Seconds))
}
