package com.advancedtelematic.data

import com.advancedtelematic.data.DataType.{StaticDeltaIndex, SuperBlockHash}
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import scodec.Codec
import scodec.bits.ByteVector

import scala.annotation.tailrec
import scala.util.{Success, Try, Failure}

trait GVariantEncoder[T] {
  def apply(value: T): Try[Array[Byte]]
}

object GVariantEncoder {

  implicit class GVariantEncoderOps[T](value: T)(implicit encoder: GVariantEncoder[T]) {
    def encodeGVariant: Try[Array[Byte]] = encoder.apply(value)
  }

  // Serialize a `StaticDeltaIndex` into a format that can e read by ostree
  // We chose to provide a GVariantEncoder[StaticDeltaIndex] rather than a generic encoder of `Map<String, T>` so that
  // we can make assumptions and simplify this encoder. For example, we do not serialize an empty map and we assume each
  // array size will fit in a single byte (toByte)
  implicit val StaticDeltaIndex: GVariantEncoder[StaticDeltaIndex] = (value: StaticDeltaIndex) => {
    if(value.deltas.isEmpty) {
      Failure(new IllegalArgumentException(s"cannot serialize an empty Static"))
    } else {
      var output = ByteVector.empty

      val HEADER = "ostree.static-deltas"

      output ++= ByteVector(HEADER.getBytes()) ++ ByteVector.fromByte(0)
      output = output.padRight(HEADER.length + 8 - HEADER.length % 8)

      @tailrec def encodeEntries(acc: ByteVector, offsets: List[Long],
                                 hashes: List[((Commit, Commit), SuperBlockHash)]): (ByteVector, List[Long]) = (acc, offsets, hashes) match {
        case (acc, offsets, (from, to) -> superblockHash :: xs) =>
          var buf = acc

          val deltaIdBuf = ByteVector(s"${from.value}-${to.value}".getBytes()) ++ ByteVector.fromByte(0)
          buf ++= deltaIdBuf

          val keyEndsAt = deltaIdBuf.length

          buf ++= ByteVector.fill(8 - buf.length % 8)(0)

          buf ++= ByteVector.fromValidHex(superblockHash.value) ++ ByteVector.fromByte(0)
            ++ ByteVector("ay".getBytes()) ++ ByteVector.fromByte(keyEndsAt.toByte)

          val offsetBeforePadding = buf.length

          // Only pad if it's not the last element of the array
          if (xs.nonEmpty) {
            buf ++= ByteVector.fill(8 - buf.length % 8)(0)
          }

          encodeEntries(buf, offsetBeforePadding :: offsets, xs)
        case (buf, offsets, Nil) =>
          buf -> offsets.reverse
      }

      val (entryBuf, offsets) = encodeEntries(ByteVector.empty, List.empty, value.deltas.toList)
      output ++= entryBuf

      val codec = longEncoderForSize(offsets.max)
      val encoded = offsets.map(i => codec.unsafeEncode(i)).flatMap(_.toArray)

      output ++= ByteVector(encoded) ++ ByteVector.fromByte(0) ++ ByteVector("a{sv}".getBytes())

      // We need to find the smallest codec than can encode `encoded(HEADER.length + 1)` + output.length
      val offsetCodecSize = longEncoderForSize(HEADER.length + 1).sizeBound.lowerBound + output.length
      val offsetCodec = longEncoderForSize(offsetCodecSize)

      output ++= offsetCodec.unsafeEncode(HEADER.length + 1)
      output ++= offsetCodec.unsafeEncode(output.length)

      Success(output.toArray)
    }
  }

  private def longEncoderForSize(i: Long): Codec[Long] = {
    import scala.math.*
    scodec.codecs.ulongL(ceil((log(i.toInt) / log(2)) / 8.0).toInt * 8)
  }

  private implicit class UnsafeEncodeOps[T](value: Codec[T]) {
    def unsafeEncode(payload: T): ByteVector = value.encode(payload).toTry.get.bytes
  }
}
