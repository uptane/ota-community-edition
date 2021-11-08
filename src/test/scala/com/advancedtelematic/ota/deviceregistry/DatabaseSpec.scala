package com.advancedtelematic.ota.deviceregistry

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.Suite

trait DatabaseSpec extends com.advancedtelematic.libats.test.DatabaseSpec {
  self: Suite =>

  override protected def testDbConfig: Config =
    ConfigFactory.load().getConfig("ats.deviceregistry.database")
}

