package com.advancedtelematic.tuf.cli

import java.io.FileOutputStream
import java.nio.file.Path

import com.advancedtelematic.tuf.cli.Errors.CliArgumentMissing

import scala.reflect.ClassTag
import scala.tools.nsc.interpreter.OutputStream

object CliConfigOptionOps {

  implicit class CliConfigOptionConversion[T](value: Option[T])(implicit tag: ClassTag[T]) {
    def valueOrConfigError: T =
      value.getOrElse(throw CliArgumentMissing(s"Argument of type ${tag.runtimeClass.getCanonicalName} is missing after Cli parsing"))
  }

  implicit class CliConfigOptionPathConversion(value: Option[Path]) {
    def streamOrStdout: OutputStream = value.map(p => new FileOutputStream(p.toFile)).getOrElse(System.out)
  }
}
