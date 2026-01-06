package com.advancedtelematic.data

import com.advancedtelematic.data.DataType.{StaticDeltaIndex, SuperBlockHash}
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import eu.timepit.refined.api.RefType
import org.scalatest.{FunSuite, Matchers}

class GVariantEncoderSpec extends FunSuite with Matchers {

  def Commit(str: String): Commit = RefType.applyRef[Commit](str).toOption.get

  test("with one delta superblock") {
    val deltas = Map(
      (Commit("4cb16113d0c81a3069152ed46cf2c1a403f27d317f9c76e634899c3fadf1a036"), Commit("1569da8f93bb047f8aed79c83f28501588fd9bfd236d19f0edf7ad649b5f9971")) -> RefType.applyRef[SuperBlockHash]("aef9c5dd8ddbf34e69aa207fd9dd47752d79b648ba6de9cc1b3301a787f0db39").toOption.get,
    )

    val toEncode = StaticDeltaIndex(deltas)

    val s = GVariantEncoder.StaticDeltaIndex.apply(toEncode).get

    val asHex = s.grouped(16).map(_.map(s => f"$s%02x").mkString(" ")).mkString("\n")

    val expected = """6f 73 74 72 65 65 2e 73 74 61 74 69 63 2d 64 65
                     |6c 74 61 73 00 00 00 00 34 63 62 31 36 31 31 33
                     |64 30 63 38 31 61 33 30 36 39 31 35 32 65 64 34
                     |36 63 66 32 63 31 61 34 30 33 66 32 37 64 33 31
                     |37 66 39 63 37 36 65 36 33 34 38 39 39 63 33 66
                     |61 64 66 31 61 30 33 36 2d 31 35 36 39 64 61 38
                     |66 39 33 62 62 30 34 37 66 38 61 65 64 37 39 63
                     |38 33 66 32 38 35 30 31 35 38 38 66 64 39 62 66
                     |64 32 33 36 64 31 39 66 30 65 64 66 37 61 64 36
                     |34 39 62 35 66 39 39 37 31 00 00 00 00 00 00 00
                     |ae f9 c5 dd 8d db f3 4e 69 aa 20 7f d9 dd 47 75
                     |2d 79 b6 48 ba 6d e9 cc 1b 33 01 a7 87 f0 db 39
                     |00 61 79 82 ac 00 61 7b 73 76 7d 15 cc""".stripMargin

    asHex shouldBe expected
  }

  test("with more than one delta superblock") {
    val deltas = Map(
      (Commit("4cb16113d0c81a3069152ed46cf2c1a403f27d317f9c76e634899c3fadf1a036"), Commit("b01c235f81125fd655a2f033e22c88caa59705f19d5285e39d9971e68e7528bb")) -> RefType.applyRef[SuperBlockHash]("381c4da9007e9a81269b8dd0c5bc9c15c2997a3c0f6473e57eee9623b5e847b7").toOption.get,
      (Commit("1569da8f93bb047f8aed79c83f28501588fd9bfd236d19f0edf7ad649b5f9971"), Commit("b01c235f81125fd655a2f033e22c88caa59705f19d5285e39d9971e68e7528bb")) -> RefType.applyRef[SuperBlockHash]("1b8f26f18843cba8ac5ca1aba52e0ce3ff4b4b7e9aaa4d8fbe546f7f9ae4d63d").toOption.get,
    )

    val toEncode = StaticDeltaIndex(deltas)

    val s = GVariantEncoder.StaticDeltaIndex.apply(toEncode).get

    val asHex = s.grouped(16).map(_.map(s => f"$s%02x").mkString(" ")).mkString("\n")

    val expected = """6f 73 74 72 65 65 2e 73 74 61 74 69 63 2d 64 65
                     |6c 74 61 73 00 00 00 00 34 63 62 31 36 31 31 33
                     |64 30 63 38 31 61 33 30 36 39 31 35 32 65 64 34
                     |36 63 66 32 63 31 61 34 30 33 66 32 37 64 33 31
                     |37 66 39 63 37 36 65 36 33 34 38 39 39 63 33 66
                     |61 64 66 31 61 30 33 36 2d 62 30 31 63 32 33 35
                     |66 38 31 31 32 35 66 64 36 35 35 61 32 66 30 33
                     |33 65 32 32 63 38 38 63 61 61 35 39 37 30 35 66
                     |31 39 64 35 32 38 35 65 33 39 64 39 39 37 31 65
                     |36 38 65 37 35 32 38 62 62 00 00 00 00 00 00 00
                     |38 1c 4d a9 00 7e 9a 81 26 9b 8d d0 c5 bc 9c 15
                     |c2 99 7a 3c 0f 64 73 e5 7e ee 96 23 b5 e8 47 b7
                     |00 61 79 82 00 00 00 00 31 35 36 39 64 61 38 66
                     |39 33 62 62 30 34 37 66 38 61 65 64 37 39 63 38
                     |33 66 32 38 35 30 31 35 38 38 66 64 39 62 66 64
                     |32 33 36 64 31 39 66 30 65 64 66 37 61 64 36 34
                     |39 62 35 66 39 39 37 31 2d 62 30 31 63 32 33 35
                     |66 38 31 31 32 35 66 64 36 35 35 61 32 66 30 33
                     |33 65 32 32 63 38 38 63 61 61 35 39 37 30 35 66
                     |31 39 64 35 32 38 35 65 33 39 64 39 39 37 31 65
                     |36 38 65 37 35 32 38 62 62 00 00 00 00 00 00 00
                     |1b 8f 26 f1 88 43 cb a8 ac 5c a1 ab a5 2e 0c e3
                     |ff 4b 4b 7e 9a aa 4d 8f be 54 6f 7f 9a e4 d6 3d
                     |00 61 79 82 ac 00 5c 01 00 61 7b 73 76 7d 15 00
                     |80 01""".stripMargin

    asHex shouldBe expected
  }
}
