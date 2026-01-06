package com.advancedtelematic.libtuf.crypt

import java.nio.ByteBuffer
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import com.advancedtelematic.libats.data.DataType.{Checksum, HashMethod, ValidChecksum}
import eu.timepit.refined.refineV
import org.bouncycastle.util.encoders.Hex

object Sha256FileDigest {

  def from(path: Path): Checksum = {
    val digest = MessageDigest.getInstance("SHA-256")
    val channel = Files.newByteChannel(path)
    val buf = ByteBuffer.allocate(8192)

    while (channel.read(buf) > 0) {
      buf.flip()
      digest.update(buf)
      buf.clear()
    }

    val checksumStr = Hex.toHexString(digest.digest())

    Checksum(HashMethod.SHA256, refineV[ValidChecksum](checksumStr).toOption.get)
  }

}
