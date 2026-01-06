package com.advancedtelematic.tuf.cli

import java.io.{File, FileOutputStream, OutputStream}

import scala.util.Try
import CliConfigOptionOps._

trait WriteOutput {
  def write(bytes: Array[Byte]): Try[Unit]
}

object WriteOutput {

  def fromConfig(config: Config): WriteOutput =
    if (config.inplace)
      fromFile(config.inputPath.valueOrConfigError.toFile)
    else
      (bytes: Array[Byte]) =>
        Try {
          val outputStream = config.outputPath.streamOrStdout
          outputStream.write(bytes)
        }

  def fromFile(file: File): WriteOutput = (bytes: Array[Byte]) =>
    Try {
      val out = new FileOutputStream(file)
      out.write(bytes)
    }

  def fromOutputStream(out: OutputStream): WriteOutput = (bytes: Array[Byte]) =>
    Try {
      out.write(bytes)
    }

}
