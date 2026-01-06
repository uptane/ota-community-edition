package com.advancedtelematic.director.data

import com.advancedtelematic.libats.data.EcuIdentifier
import org.scalacheck.Gen
import GeneratorOps.*
import akka.http.scaladsl.model.Uri
import com.advancedtelematic.director.data.AdminDataType.{
  MultiTargetUpdate,
  RegisterEcu,
  TargetUpdate,
  TargetUpdateRequest
}
import com.advancedtelematic.director.data.DeviceRequest.{
  DeviceManifest,
  EcuManifest,
  InstallationItem,
  InstallationReport,
  MissingInstallationReport
}
import com.advancedtelematic.director.data.UptaneDataType.*
import com.advancedtelematic.libats.data.DataType.{
  Checksum,
  CorrelationId,
  HashMethod,
  MultiTargetUpdateId,
  OfflineUpdateId,
  ResultCode,
  ResultDescription,
  ValidChecksum,
  ValidLockboxHash
}
import com.advancedtelematic.libats.messaging_datatype.DataType.InstallationResult
import com.advancedtelematic.libtuf.data.TufDataType.{
  Ed25519KeyType,
  HardwareIdentifier,
  KeyType,
  RsaKeyType,
  SignedPayload,
  TargetFilename,
  TufKey,
  TufKeyPair,
  ValidTargetFilename
}
import eu.timepit.refined.api.{RefType, Refined}
import io.circe.Json
import Codecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.ClientTargetItem

trait Generators {
  lazy val GenHexChar: Gen[Char] = Gen.oneOf(('0' to '9') ++ ('a' to 'f'))

  lazy val GenEcuIdentifier: Gen[EcuIdentifier] =
    Gen
      .choose(10, 64)
      .flatMap(GenStringByCharN(_, Gen.alphaChar))
      .map(EcuIdentifier.from(_).toOption.get)

  lazy val GenHardwareIdentifier: Gen[HardwareIdentifier] =
    Gen.choose(10, 200).flatMap(GenRefinedStringByCharN(_, Gen.alphaChar))

  lazy val GenHashes: Gen[Hashes] = for {
    hash <- GenRefinedStringByCharN[ValidChecksum](64, GenHexChar)
  } yield Hashes(hash)

  lazy val GenFileInfo: Gen[FileInfo] = for {
    hs <- GenHashes
    len <- Gen.posNum[Int]
  } yield FileInfo(hs, len)

  lazy val GenTargetFilename: Gen[TargetFilename] =
    Gen.alphaStr
      .suchThat(x => x.nonEmpty && x.length < 254)
      .map(Refined.unsafeApply[String, ValidTargetFilename])

  lazy val GenImage: Gen[Image] = for {
    fp <- GenTargetFilename
    fi <- GenFileInfo
  } yield Image(fp, fi)

  lazy val GenChecksum: Gen[Checksum] = for {
    hash <- GenRefinedStringByCharN[ValidChecksum](64, GenHexChar)
  } yield Checksum(HashMethod.SHA256, hash)

  def GenEcuManifestWithImage(ecuId: EcuIdentifier, image: Image): Gen[EcuManifest] = for {
    attacks <- Gen.alphaStr
  } yield EcuManifest(image, ecuId, attacks, custom = None)

  def GenEcuManifest(ecuId: EcuIdentifier): Gen[EcuManifest] =
    GenImage.flatMap(GenEcuManifestWithImage(ecuId, _))

  lazy val GenDeviceManifest: Gen[DeviceManifest] = for {
    primaryEcu <- GenEcuIdentifier
    ecuManifest <- GenEcuManifest(primaryEcu)
  } yield DeviceManifest(
    primaryEcu,
    Map(primaryEcu -> SignedPayload(Seq.empty, ecuManifest, Json.Null)),
    installation_report = Left(MissingInstallationReport)
  )

  def genIdentifier(maxLen: Int): Gen[String] = for {
    // use a minimum length of 10 to reduce possibility of naming conflicts
    size <- Gen.choose(10, maxLen)
    name <- Gen.containerOfN[Seq, Char](size, Gen.alphaNumChar)
  } yield name.mkString

  def GenInstallReportEntity(primaryEcu: EcuIdentifier, success: Boolean) = for {
    code <- Gen.alphaNumStr.map(ResultCode.apply)
    desc <- Gen.alphaNumStr.map(ResultDescription.apply)
    installItem = InstallationItem(primaryEcu, InstallationResult(success, code, desc))
    correlationId <- GenCorrelationId
    installationReport = InstallationReport(
      correlationId,
      InstallationResult(success, code, desc),
      Seq(installItem),
      raw_report = None
    )
  } yield DeviceRequest.InstallationReportEntity(
    "application/vnd.com.here.otac.installationReport.v1",
    installationReport
  )

  val GenTargetUpdate: Gen[TargetUpdate] = for {
    target <- genIdentifier(200).map(Refined.unsafeApply[String, ValidTargetFilename])
    size <- Gen.chooseNum(0, Long.MaxValue)
    checksum <- GenChecksum
    nr <- Gen.posNum[Int]
    uri <- Gen.option(Gen.const(Uri(s"http://test-$nr.example.com")))
    userDefinedCustom <- Gen.option(
      Gen.oneOf(
        Gen.const(Json.arr(Json.fromInt(0))),
        Gen.const(Json.obj("mycustom" -> Json.fromInt(0)))
      )
    )
  } yield TargetUpdate(target, checksum, size, uri, userDefinedCustom)

  val GenTargetUpdateRequest: Gen[TargetUpdateRequest] = for {
    targetUpdate <- GenTargetUpdate
  } yield TargetUpdateRequest(None, targetUpdate)

  val GenMultiTargetUpdateRequest: Gen[MultiTargetUpdate] = for {
    targets <- Gen.nonEmptyMap(Gen.zip(GenHardwareIdentifier, GenTargetUpdateRequest))
  } yield MultiTargetUpdate(targets)

  lazy val GenKeyType: Gen[KeyType] = Gen.oneOf(RsaKeyType, Ed25519KeyType)

  lazy val GenTufKeyPair: Gen[TufKeyPair] =
    GenKeyType.map { kt =>
      kt.crypto.generateKeyPair()
    }

  lazy val GenTufKey: Gen[TufKey] =
    GenTufKeyPair.map(_.pubkey)

  lazy val GenRegisterEcu: Gen[RegisterEcu] = for {
    ecu <- GenEcuIdentifier
    hwId <- GenHardwareIdentifier
    crypto <- GenTufKey
  } yield RegisterEcu(ecu, hwId, crypto)

  lazy val GenCorrelationId =
    Gen.uuid.map(u => MultiTargetUpdateId(u))

  lazy val GenOffLineCorrelationId = for {
    hash <- Gen
      .stringOfN(12, Gen.hexChar)
      .map(str => RefType.applyRef[ValidLockboxHash](str.toLowerCase()).toOption.get)
    lockbox <- Gen.alphaStr
    version <- Gen.long.suchThat(_ > 0)
  } yield OfflineUpdateId(lockbox, version, hash)

  lazy val GenRegisterEcuKeys: Gen[(RegisterEcu, TufKeyPair)] = for {
    ecu <- GenEcuIdentifier
    hwId <- GenHardwareIdentifier
    keyPair <- GenTufKeyPair
  } yield RegisterEcu(ecu, hwId, keyPair.pubkey) -> keyPair

  def GenInstallReport(ecuSerial: EcuIdentifier,
                       success: Boolean,
                       correlationId: Option[CorrelationId] = None): Gen[InstallationReport] = for {
    code <- Gen.alphaLowerStr.map(ResultCode.apply)
    desc <- Gen.alphaLowerStr.map(ResultDescription.apply)
    cid <- correlationId.map(Gen.const).getOrElse(GenCorrelationId)
    installItem = InstallationItem(ecuSerial, InstallationResult(success, code, desc))
  } yield InstallationReport(cid, InstallationResult(success, code, desc), Seq(installItem), None)

  lazy val GenTarget: Gen[(TargetFilename, ClientTargetItem)] = for {
    file <- GenTargetFilename
    length <- Gen.posNum[Long]
    hash <- GenRefinedStringByCharN[ValidChecksum](64, GenHexChar)
    hashes = Map(HashMethod.SHA256 -> hash)
  } yield file -> ClientTargetItem(hashes, length, custom = None)

}

object Generators extends Generators
