package com.advancedtelematic.tuf.cli.repo

import com.advancedtelematic.libtuf.data.TufDataType.{TufKey, ValidSignatureType}

import scala.util.{Failure, Success, Try}

object TufRepoOps {

  implicit class MapSequencer(m: Map[Try[TufKey], ValidSignatureType]) {

    def sequence: Try[Map[TufKey, ValidSignatureType]] =
      m.foldLeft(Try(Map.empty[TufKey, ValidSignatureType])) {
        case (acc, (tryKey, validSignature)) =>
          tryKey match {
            case Success(key) => acc.map(_.updated(key, validSignature))
            case Failure(ex)  => Failure(ex)
          }
      }

  }

}
