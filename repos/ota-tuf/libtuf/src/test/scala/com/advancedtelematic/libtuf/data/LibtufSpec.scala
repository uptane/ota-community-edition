package com.advancedtelematic.libtuf.data

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

trait LibtufSpec extends AnyFunSuite with Matchers {
  Security.addProvider(new BouncyCastleProvider)
}
