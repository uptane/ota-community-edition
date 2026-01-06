package com.advancedtelematic.tuf.cli

import java.io.InputStream
import java.nio.file.Path

import cats.data.Validated.{Invalid, Valid}
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientDataType.RootRole
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, SignedPayload}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.control.NoStackTrace
import cats.syntax.either._
import io.circe.jawn.parseFile
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.TufCodecs._
import io.circe.Decoder

import scala.io.Source
import scala.util.Try

object CliUtil {

  private val _log = LoggerFactory.getLogger(this.getClass)

  case class InvalidPayload(msg: String) extends Throwable(msg) with NoStackTrace

  def readJsonFrom[T](is: InputStream)(implicit decoder: Decoder[T]): Try[T] =
    io.circe.parser.parse(Source.fromInputStream(is).mkString).flatMap(_.as[T](decoder)).toTry

  def verifyRootFile(rootPath: Path): Future[SignedPayload[RootRole]] = {
    val validatedPayload =
      parseFile(rootPath.toFile)
        .flatMap(_.as[SignedPayload[RootRole]])
        .leftMap(_.toString)
        .toValidatedNel

    val isValidPayload = validatedPayload.andThen { parsedPayload =>
      TufCrypto.payloadSignatureIsValid(parsedPayload.signed, parsedPayload)
    }

    isValidPayload match {
      case Valid(rr) =>
        _log.info(s"Signatures for $rootPath are valid")
        Future.successful(rr)

      case Invalid(err) =>
        err.map(_log.error)
        Future.failed(InvalidPayload("Invalid root.json file"))
    }
  }

  def verifyRoot(payload: SignedPayload[RootRole]): Future[SignedPayload[RootRole]] =
    TufCrypto.payloadSignatureIsValid(payload.signed, payload) match {
      case Valid(rr) =>
        Future.successful(rr)

      case Invalid(err) =>
        err.map(_log.error)
        Future.failed(InvalidPayload("Invalid root.json file"))
    }

}
