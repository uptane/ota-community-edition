package com.advancedtelematic.tuf.reposerver.http

import java.time.Instant
import com.advancedtelematic.libtuf.data.ClientDataType.TargetCustom
import io.circe.parser._
import cats.syntax.either._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TargetCustomDecoderSpec extends AnyFunSuite with Matchers {
  import com.advancedtelematic.libtuf.data.ClientCodecs.targetCustomDecoder

  test("decoder can decode new json") {
    val instant = Instant.parse("2017-07-10T13:27:28Z")
    val str =
      """{"name":"qemux86-64-ota","version":"ffd79847609f2c979deed5d81ec87833bd88f35bb15aa860454442db05d3129c","hardwareIds":["qemux86-64-ota"],"targetFormat":null,"createdAt":"2017-07-10T13:27:28Z","updatedAt":"2017-07-10T13:27:28Z"}"""
    val tc = parse(str).flatMap(_.as[TargetCustom]).valueOr(throw _)
    tc shouldBe a[TargetCustom]

    tc.createdAt shouldBe instant
    tc.updatedAt shouldBe instant
  }

}
