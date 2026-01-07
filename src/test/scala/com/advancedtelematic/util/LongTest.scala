package com.advancedtelematic.util


import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Millis, Seconds, Span}

trait LongTest extends PatienceConfiguration {
  override implicit def patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))
}
