package com.advancedtelematic.tuf.util

import java.security.Security
import com.advancedtelematic.tuf.keyserver.Settings
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

abstract class TufKeyserverSpec extends FunSuite with Matchers with ScalaFutures with Settings {
  Security.addProvider(new BouncyCastleProvider)
}
