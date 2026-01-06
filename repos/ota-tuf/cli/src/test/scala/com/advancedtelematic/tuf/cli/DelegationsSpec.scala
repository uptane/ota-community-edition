package com.advancedtelematic.tuf.cli

import java.io.{ByteArrayOutputStream, FileOutputStream}
import java.nio.file.Files

import cats.syntax.either._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, TargetsRole}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, SignedPayload, ValidTargetFilename}
import com.advancedtelematic.tuf.cli.util.CliSpec
import io.circe.{jawn, Json}
import cats.syntax.either._
import com.advancedtelematic.libats.data.DataType.{HashMethod, ValidChecksum}
import com.advancedtelematic.tuf.cli.Errors.DelegationsAlreadySigned
import eu.timepit.refined._

import scala.util.Failure

class DelegationsSpec extends CliSpec {

  test("generates an empty delegations role to a given Path") {
    val p = new ByteArrayOutputStream()

    Delegations.writeNew(p).get

    val emptyDelegations = jawn.parse(p.toString).flatMap(_.as[TargetsRole]).valueOr(throw _)

    emptyDelegations.delegations shouldBe empty
  }

  test("signs a payload with the supplied keys") {
    val pair = KeyType.default.crypto.generateKeyPair()

    val in = Files.createTempFile("payload", ".json")
    Files.write(in, "{}".getBytes)

    val outBaos = new ByteArrayOutputStream()
    val out = WriteOutput.fromOutputStream(outBaos)

    Delegations.signPayload(List(pair.pubkey -> pair.privkey), in, out).get

    val signedPayload =
      jawn.parse(outBaos.toString).flatMap(_.as[SignedPayload[Json]]).valueOr(throw _)

    signedPayload.signatures shouldNot be(empty)

    signedPayload.signed shouldBe Json.obj()
  }

  test("returns descriptive error when adding a target to a signed delegations file") {
    val in = Files.createTempFile("delegations", ".json")
    Delegations.writeNew(new FileOutputStream(in.toFile)).get
    val pair = KeyType.default.crypto.generateKeyPair()
    val out = Files.createTempFile("delegations-signed", ".json")
    val outOutput = WriteOutput.fromFile(out.toFile)

    Delegations.signPayload(List(pair.pubkey -> pair.privkey), in, outOutput).get

    val filename = refineV[ValidTargetFilename]("test-0.0.1").toOption.get

    val item = ClientTargetItem(
      Map(
        HashMethod.SHA256 -> refineV[ValidChecksum](
          "4c89194898360ebba34059bb8a5dd47a0650ca66b37c0143c32f7e70416e29e0"
        ).toOption.get
      ),
      1024,
      custom = None
    )

    val result = Delegations.addTarget(
      out,
      WriteOutput.fromOutputStream(new ByteArrayOutputStream()),
      filename,
      item
    )

    result.failed.get shouldBe DelegationsAlreadySigned(out)
  }

  test("overwrites existing target") {
    val in = Files.createTempFile("payload", ".json")
    Delegations.writeNew(new FileOutputStream(in.toFile)).get

    val filename = refineV[ValidTargetFilename]("test-0.0.1").toOption.get

    val item = ClientTargetItem(
      Map(
        HashMethod.SHA256 -> refineV[ValidChecksum](
          "4c89194898360ebba34059bb8a5dd47a0650ca66b37c0143c32f7e70416e29e0"
        ).toOption.get
      ),
      1024,
      custom = None
    )

    Delegations.addTarget(in, WriteOutput.fromFile(in.toFile), filename, item).get

    val outBaos = new ByteArrayOutputStream()
    val out = WriteOutput.fromOutputStream(outBaos)

    val item2 = item.copy(length = 2048)

    Delegations.addTarget(in, out, filename, item2).get

    val targetsRole = jawn.parse(outBaos.toString).flatMap(_.as[TargetsRole]).valueOr(throw _)

    targetsRole.targets should have size 1

    targetsRole.targets(filename).length shouldBe 2048
  }

  test("keeps unknown attributes of delegations json file") {
    val in = Files.createTempFile("payload", ".json")
    Files.write(
      in,
      """
        |{
        |  "_type" : "Targets",
        |  "expires" : "2020-01-31T14:59:56Z",
        |  "targets" : { },
        |  "version" : 1,
        |  "unknown_field": true
        |}
        |
      """.stripMargin.getBytes
    )

    val filename = refineV[ValidTargetFilename]("test-0.0.1").toOption.get

    val item = ClientTargetItem(
      Map(
        HashMethod.SHA256 -> refineV[ValidChecksum](
          "4c89194898360ebba34059bb8a5dd47a0650ca66b37c0143c32f7e70416e29e0"
        ).toOption.get
      ),
      1024,
      custom = None
    )

    val outBaos = new ByteArrayOutputStream()
    val out = WriteOutput.fromOutputStream(outBaos)

    Delegations.addTarget(in, out, filename, item).get

    val unknownField =
      jawn.parse(outBaos.toString).flatMap(_.hcursor.downField("unknown_field").as[Boolean])

    unknownField.valueOr(throw _) shouldBe true
  }

}
