package com.advancedtelematic.campaigner

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite

trait DatabaseSpec extends com.advancedtelematic.libats.test.DatabaseSpec {
  self: Suite =>

  override protected def testDbConfig: Config =
    ConfigFactory.load().getConfig("ats.campaigner.database")
}
