package com.advancedtelematic.tuf.cli

import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libtuf.data.ErrorCodes
import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpClientError
import com.advancedtelematic.tuf.cli.Errors.{CommandNotSupportedByRepositoryType, PastDate}
import com.advancedtelematic.tuf.cli.repo.TufRepo.TargetsPullError
import io.circe.syntax._
import org.slf4j.LoggerFactory

object CliHelp {

  private val _log = LoggerFactory.getLogger(this.getClass)

  val explainError: PartialFunction[Throwable, Unit] = {
    case ex: CliHttpClientError if ex.remoteError.code == ErrorCodes.KeyServer.InvalidRootRole =>
      val causes = for {
        errorRepr <- ex.remoteError.cause.flatMap(_.as[ErrorRepresentation].toOption)
        errorMsgs <- errorRepr.cause.flatMap(_.as[List[String]].toOption)
      } yield (errorRepr.description :: errorMsgs).mkString("\n  ")

      System.err.println(ex.remoteError.asJson.noSpaces)

      System.err.println(s"""Server could not validate the root role:
           |  ${causes.getOrElse(ex.msg)}
          """.stripMargin)

    case ex: TargetsPullError =>
      System.err.println(s"""
          | ${ex.msg}
          |
          |Common causes for this error include:
          |
          |- You are trying to pull a version of targets.json signed with keys no longer present on root.json.
          |  This can happen if you rotate root.json and try to pull targets.json without first pushing a targets.json signed with the new targets.keys.
          |
          |  Sign a targets.json with the keys used to rotate root.json, push it to the server and try again.
          |
          |- The targets public key present in the credentials.zip file used to initialize this repository is not the same key the server is using as a targets key.
          |  Did you already rotate a root.json with different keys and you are trying to rotate it again using old keys?
          |  Did you remove the public key currently associated to the targets role?
          |
          |  If the targets key is still online on the server, you can try downloading a fresh credentials.zip
          |
          |- The Server could not return a valid checksum when pulling targets.
          |""".stripMargin)

    case CommandNotSupportedByRepositoryType(repoType, msg) =>
      _log.error(
        s"The local repository is of type $repoType which does not support this command: $msg"
      )

    case PastDate() =>
      _log.error(
        "The metadata expiration date lies in the past, use --force if you really want to use it"
      )
  }

}
