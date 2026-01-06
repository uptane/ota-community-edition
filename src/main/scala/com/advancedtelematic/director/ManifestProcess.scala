package com.advancedtelematic.director

import cats.implicits.*
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.DbDataType.DeviceKnownState
import com.advancedtelematic.director.data.DeviceRequest.DeviceManifest
import com.advancedtelematic.director.manifest.ManifestCompiler
import com.advancedtelematic.libats.data.DataType.Namespace
import io.circe.syntax.*

import java.nio.file.Paths

object ManifestProcess {

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println(
        s"usage: ${this.getClass.getCanonicalName} <path to json manifest file> <path to json state file>"
      )
      sys.exit(1)
    }

    val manifestFile = Paths.get(args(0))

    val deviceManifest =
      io.circe.jawn.parsePath(manifestFile).flatMap(_.as[DeviceManifest]).valueOr(throw _)

    val deviceStatusFile = Paths.get(args(1))

    val deviceKnownState =
      io.circe.jawn.parsePath(deviceStatusFile).flatMap(_.as[DeviceKnownState]).valueOr(throw _)

    val newState =
      ManifestCompiler.apply(Namespace("notused"), deviceManifest).apply(deviceKnownState).get

    println(newState.knownState.asJson)
  }

}
