package com.advancedtelematic.libtuf_server.repo.server

import java.net.URI
import java.time.Instant
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.{ClientDataType, TufCodecs}
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole
import com.advancedtelematic.libtuf.data.TufDataType.JsonSignedPayload
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import com.advancedtelematic.libtuf.data.ClientDataType.{MetaItem, MetaPath}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import io.circe.Decoder
import io.circe.syntax.*

import scala.concurrent.Future
import scala.util.Try

object DataType {
  import com.advancedtelematic.libtuf.data.TufCodecs._

  implicit class JavaUriToPekkoUriConversion(value: URI) {
    def toUri: Uri = Uri(value.toString)
  }

  implicit class PekkoUriToJavaUriConversion(value: Uri) {
    def toURI: URI = URI.create(value.toString)
  }

  case class SignedRole[T: TufRole](content: JsonSignedPayload,
                                    checksum: Checksum,
                                    length: Long,
                                    version: Int,
                                    expiresAt: Instant) {

    def role(implicit dec: Decoder[T]): T =
      content.signed.as[T] match {
        case Left(err) =>
          throw new IllegalArgumentException(
            s"Could not decode a role persisted as ${implicitly[TufRole[T]].metaPath} but not parseable as such a type: $err"
          )
        case Right(p) => p
      }

    def asMetaRole: (MetaPath, MetaItem) = {
      val hashes = Map(checksum.method -> checksum.hash)
      tufRole.metaPath -> ClientDataType.MetaItem(hashes, length, version)
    }

    def tufRole: TufRole[T] = implicitly[TufRole[T]]
  }

  object SignedRole {

    def withChecksum[T: TufRole: Decoder](content: JsonSignedPayload,
                                          version: Int,
                                          expireAt: Instant): Future[SignedRole[T]] = FastFuture {
      Try {
        val canonicalJson = TufCodecs.jsonSignedPayloadEncoder(content).canonical
        val checksum = Sha256Digest.digest(canonicalJson.getBytes)
        val signedRole =
          SignedRole[T](content, checksum, canonicalJson.getBytes.length, version, expireAt)
        signedRole.role // Decode the role to make sure it's valid
        signedRole
      }
    }

  }

}
