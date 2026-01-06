package com.advancedtelematic.libtuf.data

import java.util.Base64

import eu.timepit.refined.api.Validate

import scala.util.Try

object ValidationUtils {

  def validBase64(str: String): Boolean =
    Try(Base64.getDecoder.decode(str)).isSuccess

  def validBase64Validation[T](v: T): Validate.Plain[String, T] =
    Validate.fromPredicate(value => validBase64(value), hash => s"$hash is not a base64 string", v)

  def validHex(length: Long, str: String): Boolean =
    str.length == length && str.forall(h => ('0' to '9').contains(h) || ('a' to 'f').contains(h))

  def validHexValidation[T](v: T, length: Int): Validate.Plain[String, T] = {
    require(length % 2 == 0)

    Validate.fromPredicate(
      hash => validHex(length, hash),
      hash => s"$hash is not a $length hex string",
      v
    )
  }

  def validInBetween[T](min: Long, max: Long, proof: T): Validate.Plain[String, T] =
    Validate.fromPredicate(
      str => str.length >= min && str.length <= max,
      str => s"$str is not between $min and $max chars long",
      proof
    )

}
