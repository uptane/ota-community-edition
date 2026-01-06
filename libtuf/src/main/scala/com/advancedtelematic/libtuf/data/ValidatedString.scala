package com.advancedtelematic.libtuf.data

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import io.circe.{Decoder, Encoder}

// TODO: This can be made more generic (not string)
// TODO: See UUIDKey to make this prettier
object ValidatedString {

  trait ValidatedString { self =>
    val value: String

    override def equals(obj: scala.Any): Boolean =
      if (self.isInstanceOf[self.type])
        value.equals(obj.asInstanceOf[self.type].value)
      else
        false

    override def toString = s"${this.getClass.getSimpleName}($value)"
  }

  trait ValidatedStringValidation[W <: ValidatedString] {
    def apply(value: String): ValidatedNel[String, W]

    def unsafeApply(value: String): W
  }

  object ValidatedStringValidation {

    def apply[W <: ValidatedString](cons: String => W)(
      fn: String => ValidatedNel[String, W]): ValidatedStringValidation[W] =
      new ValidatedStringValidation[W] {
        override def apply(value: String): ValidatedNel[String, W] = fn(value)

        override def unsafeApply(value: String): W = cons(value)
      }

  }

  def validatedStringDecoder[W <: ValidatedString](
    implicit validation: ValidatedStringValidation[W]): Decoder[W] =
    Decoder.decodeString.flatMap { wrapped =>
      validation(wrapped) match {
        case Valid(wrapper) => Decoder.const(wrapper)
        case Invalid(err)   =>
          val msg = err.toList.mkString(",")
          Decoder.failedWithMessage(msg)
      }
    }

  def validatedStringEncoder[W <: ValidatedString]: Encoder[W] =
    Encoder.encodeString.contramap(_.value)

  implicit class StringToValidatedStringOps(value: String) {

    def unsafeApply[W <: ValidatedString](implicit validation: ValidatedStringValidation[W]): W =
      validation.unsafeApply(value)

  }

}
