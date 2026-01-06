package com.advancedtelematic.tuf.reposerver.util

import java.security.Security
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, KeyType, RsaKeyType}
import com.typesafe.config.{Config, ConfigFactory}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalactic.source.Position
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

abstract class TufReposerverSpec extends AnyFunSuite with Matchers with ScalaFutures {
  Security.addProvider(new BouncyCastleProvider)

  val testDbConfig: Config = ConfigFactory.load().getConfig("ats.reposerver.database")

  def keyTypeTest(name: String)(fn: KeyType => Any)(implicit pos: Position): Unit = {
    test(name + " Ed25519")(fn(Ed25519KeyType))
    test(name + " RSA")(fn(RsaKeyType))
  }

}
