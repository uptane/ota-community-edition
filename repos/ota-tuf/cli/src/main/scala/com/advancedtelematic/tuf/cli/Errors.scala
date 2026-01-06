package com.advancedtelematic.tuf.cli

import java.nio.file.Path

import com.advancedtelematic.tuf.cli.DataType.TufServerType

import scala.util.control.NoStackTrace

object Errors {
  case class CliArgumentsException(msg: String) extends Exception(msg) with NoStackTrace
  case class CliArgumentMissing(msg: String) extends Exception(msg)

  case class CommandNotSupportedByRepositoryType(repoType: TufServerType, msg: String)
      extends Exception(msg)
      with NoStackTrace

  case class DelegationsAlreadySigned(path: Path)
      extends Exception(
        s"Delegations file $path is already signed, convert it to an unsigned file or provide an unsigned file"
      )

  case class PastDate() extends Exception
}
