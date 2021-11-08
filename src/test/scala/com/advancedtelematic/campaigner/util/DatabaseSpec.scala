package com.advancedtelematic.campaigner.util

import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import org.scalatest.Suite

trait DatabaseSpec extends MysqlDatabaseSpec {
  self: Suite =>
}
