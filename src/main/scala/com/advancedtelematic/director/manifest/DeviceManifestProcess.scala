package com.advancedtelematic.director.manifest

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DeviceRequest.{DeviceManifest, EcuManifest}
import com.advancedtelematic.director.db.EcuRepositorySupport
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.TufDataType.{SignedPayload, TufKey}
import io.circe.Decoder.Result
import io.circe.{Decoder, Json}
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}

object DeviceManifestProcess {
  import io.circe.generic.semiauto._

  val latestVersion = 3

  def manifestVersion(manifest: Json): Int = {
    if (manifest.hcursor.downField("installation_report").succeeded) 3
    else if (manifest.hcursor.downField("ecu_version_manifest").succeeded) 1
    else if (manifest.findAllByKey("operation_result").nonEmpty) 2
    else latestVersion
  }

  def fromJsonV1(manifest: Json): Result[DeviceManifest] = {
    import com.advancedtelematic.libtuf.data.TufCodecs.signedPayloadDecoder
    import cats.implicits._

    final case class DeviceManifestV1(primary_ecu_serial: EcuIdentifier,
                                      ecu_version_manifest: Seq[SignedPayload[EcuManifest]])

    implicit val decoderDeviceManifestV1: Decoder[DeviceManifestV1] = deriveDecoder

    manifest.as[DeviceManifestV1].flatMap { v1 =>
      val ecu_version_manifests: Result[Map[EcuIdentifier, SignedPayload[EcuManifest]]] =
        v1.ecu_version_manifest.toList.traverse { e =>
          e.json.as[EcuManifest].map(_.ecu_serial -> e)
        }.map(_.toMap)

      ecu_version_manifests.map(DeviceManifest(v1.primary_ecu_serial, _, None))
    }
  }

  def fromJsonV2(manifest: Json): Result[DeviceManifest] = {
    // can't create an InstallationReport from operation_results,
    // so we treat it as a V3 without an InstallationReport
    fromJsonV3(manifest)
  }

  private def fromJsonV3(manifest: Json) = {
    manifest.as[DeviceManifest]
  }

  def fromJson(manifest: Json): ValidatedNel[String, DeviceManifest] = {
    import cats.syntax.either._

    val res = manifestVersion(manifest) match {
      case 1 => fromJsonV1(manifest)
      case 2 => fromJsonV2(manifest)
      case _ => fromJsonV3(manifest)
    }

    res.leftMap(_.message).toValidatedNel
  }
}

class DeviceManifestProcess()(implicit val db: Database, val ec: ExecutionContext) extends EcuRepositorySupport {
  import cats.implicits._

  import com.advancedtelematic.libtuf.crypt.SignedPayloadSignatureOps._

  def validateManifestSignatures(ns: Namespace, deviceId: DeviceId, json: SignedPayload[Json]): Future[ValidatedNel[String, DeviceManifest]] = async {
    val primaryValidation = await(validateManifestPrimarySignatures(ns, deviceId, json))
    val deviceEcus = await(ecuRepository.findFor(deviceId)).mapValues(_.publicKey)

    primaryValidation.andThen { manifest =>
      validateManifestSecondarySignatures(deviceEcus, manifest)
    }
  }

  private def validateManifestSecondarySignatures(deviceEcuKeys: Map[EcuIdentifier, TufKey], deviceManifest: DeviceManifest): ValidatedNel[String, DeviceManifest] = {
    val verify = deviceManifest.ecu_version_manifests.map { case (ecuSerial, ecuManifest) =>
      deviceEcuKeys.get(ecuSerial) match {
        case None => Invalid(NonEmptyList.of(s"Device has no ECU with $ecuSerial"))
        case Some(key) =>
          if (ecuManifest.isValidFor(key))
            Valid(ecuManifest)
          else
            Invalid(NonEmptyList.of(s"ecu manifest for $ecuSerial not signed with key ${key.id}"))
      }
    }.toList.sequence

    verify.map(_ => deviceManifest)
  }

  private def validateManifestPrimarySignatures(ns: Namespace, deviceId: DeviceId, manifestJson: SignedPayload[Json]): Future[ValidatedNel[String, DeviceManifest]] = {
    ecuRepository.findDevicePrimary(ns, deviceId).map { primary =>
      if (manifestJson.isValidFor(primary.publicKey)) {
        DeviceManifestProcess.fromJson(manifestJson.json)
      } else {
        Invalid(NonEmptyList.of(s"Invalid primary ecu (${primary.ecuSerial}) signature for key ${primary.publicKey.id}"))
      }
    }
  }
}



