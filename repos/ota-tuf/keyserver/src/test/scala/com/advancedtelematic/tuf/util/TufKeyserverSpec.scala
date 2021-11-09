package com.advancedtelematic.tuf.util

import java.security.Security
import com.advancedtelematic.tuf.keyserver.Settings
import com.typesafe.config.{Config, ConfigFactory}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

abstract class TufKeyserverSpec extends AnyFunSuite with Matchers with ScalaFutures with Settings {
  val testDbConfig: Config = ConfigFactory.load().getConfig("ats.keyserver.database")

  Security.addProvider(new BouncyCastleProvider)
}
