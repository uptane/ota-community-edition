package com.advancedtelematic.libtuf_server.repo.server

import java.net.URI
import java.time.Instant

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.ClientDataType
import com.advancedtelematic.libtuf.data.ClientDataType.{MetaItem, MetaPath, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import io.circe.Decoder
import io.circe.syntax._
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.crypt.CanonicalJson._
import com.advancedtelematic.libtuf.data.ClientDataType.{MetaItem, MetaPath, _}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{JsonSignedPayload, RepoId, TargetFilename}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import io.circe.Decoder
import io.circe.syntax._

object DataType {
  import com.advancedtelematic.libtuf.data.ClientCodecs._
  import com.advancedtelematic.libtuf.data.TufCodecs._

  implicit class JavaUriToAkkaUriConversion(value: URI) {
    def toUri: Uri = Uri(value.toString)
  }

  implicit class AkkaUriToJavaUriConversion(value: Uri) {
    def toURI: URI = URI.create(value.toString)
  }

  case class SignedRole[T : TufRole](content: JsonSignedPayload, checksum: Checksum, length: Long, version: Int, expiresAt: Instant) {
    def role(implicit dec: Decoder[T]): T =
      content.signed.as[T] match {
        case Left(err) =>
          throw new IllegalArgumentException(s"Could not decode a role saved in database as ${implicitly[TufRole[T]]} but not parseable as such a type: $err")
        case Right(p) => p
      }

    def asMetaRole: (MetaPath, MetaItem) = {
      val hashes = Map(checksum.method -> checksum.hash)
      tufRole.metaPath -> ClientDataType.MetaItem(hashes, length, version)
    }

    def tufRole: TufRole[T] = implicitly[TufRole[T]]
  }

  object SignedRole {
    def withChecksum[T : TufRole](repoId: RepoId, content: JsonSignedPayload, version: Int, expireAt: Instant): SignedRole[T] = {
      val canonicalJson = content.asJson.canonical
      val checksum = Sha256Digest.digest(canonicalJson.getBytes)
      SignedRole[T](content, checksum, canonicalJson.length, version, expireAt)
    }
  }
}
