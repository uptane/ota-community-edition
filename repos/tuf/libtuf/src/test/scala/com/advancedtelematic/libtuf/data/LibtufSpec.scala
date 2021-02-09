package com.advancedtelematic.libtuf.data

import java.security.Security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.{FunSuite, Matchers}

trait LibtufSpec extends FunSuite with Matchers {
  Security.addProvider(new BouncyCastleProvider)
}
