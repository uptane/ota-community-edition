package com.advancedtelematic.tuf.cli

import java.time.{Instant, Period}
import com.advancedtelematic.tuf.cli.Errors.PastDate
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers


class CommandHandlerExpirationDateSpec extends AnyFunSuite with Matchers {
  val now = Instant.EPOCH
  val oneDay = Period.ofDays(1)
  val inADay = now.plus(oneDay)
  val aDayAgo = now.minus(oneDay)

  // the actual command doesn't matter
  private val cmd = Commands.SignRoot

  test("expirationDate from previous value") {
    val d = CommandHandler.expirationDate(Config(cmd), now)(inADay)
    d shouldBe inADay
  }

  test("expirationDate from previous value in the past") {
    intercept[PastDate] {
      CommandHandler.expirationDate(Config(cmd), now)(aDayAgo)
    }
  }

  test("expirationDate from expireOn") {
    val d = CommandHandler.expirationDate(Config(cmd, expireOn = Some(inADay)), now)(aDayAgo)
    d shouldBe inADay
  }

  test("expirationDate from expireOn in the past") {
    intercept[PastDate] {
      CommandHandler.expirationDate(Config(cmd, expireOn = Some(now.minusSeconds(1))), now)(aDayAgo)
    }
  }

  test("expirationDate from expireOn in the past, but forced") {
    val d = CommandHandler.expirationDate(Config(cmd, force = true, expireOn = Some(now.minusSeconds(1))), now)(aDayAgo)
    d shouldBe now.minusSeconds(1)
  }

  test("expirationDate from expireAfter") {
    val d = CommandHandler.expirationDate(Config(cmd, expireAfter = Some(oneDay)), now)(aDayAgo)
    d shouldBe inADay
  }

  test("expirationDate from expireAfter in the past") {
    intercept[PastDate] {
      CommandHandler.expirationDate(Config(cmd, expireAfter = Some(Period.ofDays(-1))), now)(aDayAgo)
    }
  }

  test("expirationDate from expireAfter in the past, but forced") {
    val d = CommandHandler.expirationDate(Config(cmd, force = true, expireAfter = Some(Period.ofDays(-1))), now)(aDayAgo)
    d shouldBe now.minus(oneDay)
  }

}
