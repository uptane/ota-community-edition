package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq

import com.advancedtelematic.libtuf.data.ClientDataType.TargetCustom
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, TargetFormat, TargetName, TargetVersion}
import io.circe._
import akka.http.scaladsl.unmarshalling._
import com.advancedtelematic.libats.http.RefinedMarshallingSupport._
import com.advancedtelematic.libats.http.AnyvalMarshallingSupport._
import scala.collection.immutable
import com.advancedtelematic.libtuf_server.data.Marshalling.targetFormatFromStringUnmarshaller

object TargetCustomParameterExtractors {
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server._
  import com.advancedtelematic.libats.http.RefinedMarshallingSupport._

  val all: Directive1[TargetCustom] =
    parameters(
      'name.as[TargetName],
      'version.as[TargetVersion],
      'hardwareIds.as(CsvSeq[HardwareIdentifier]).?(immutable.Seq.empty[HardwareIdentifier]),
      'targetFormat.as[TargetFormat].?,
    ).tmap { case (name, version, hardwareIds, targetFormat) =>
      TargetCustom(name, version, hardwareIds, targetFormat.orElse(Some(TargetFormat.BINARY)))
    }
}
