package com.advancedtelematic.libtuf_server.crypto

import java.security.MessageDigest

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.DataType.{Checksum, HashMethod, ValidChecksum}
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.{ExecutionContext, Future}

object Sha256Digest {

  def digest(data: Array[Byte]): Checksum = {
    val digest = new SHA256Digest()
    val buf = Array.fill[Byte](digest.getDigestSize)(0)
    digest.update(data, 0, data.length)
    digest.doFinal(buf, 0)
    val checksum = Hex.toHexString(buf).refineTry[ValidChecksum].get

    Checksum(HashMethod.SHA256, checksum)
  }

  def asSink(implicit ec: ExecutionContext): Sink[ByteString, Future[Checksum]] =
    Sink
      .fold(MessageDigest.getInstance("SHA-256")) { (d, b: ByteString) =>
        d.update(b.toArray)
        d
      }
      .mapMaterializedValue {
        _.flatMap { dd =>
          val hex = Hex.toHexString(dd.digest())
          Future.fromTry {
            hex.refineTry[ValidChecksum].map(Checksum(HashMethod.SHA256, _))
          }
        }
      }

}
