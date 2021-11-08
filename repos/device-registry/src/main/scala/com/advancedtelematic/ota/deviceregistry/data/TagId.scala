package com.advancedtelematic.ota.deviceregistry.data

import com.advancedtelematic.libats.codecs.CirceValidatedGeneric.{validatedGenericDecoder, validatedGenericEncoder}
import com.advancedtelematic.libats.data.{ValidatedGeneric, ValidationError}
import io.circe.Codec

final case class TagId private(value: String) extends AnyVal

object TagId {

  implicit val validatedTagId = new ValidatedGeneric[TagId, String] {
    override def to(expression: TagId): String = expression.value
    override def from(s: String): Either[ValidationError, TagId] = TagId.from(s)
  }

  def from(s: String): Either[ValidationError, TagId] =
    if (s.length <= 20 && s.matches("[\\w\\-_ ]+"))
      Right(new TagId(s))
    else
      Left(ValidationError(s"$s should contain between one and a twenty alphanumeric, hyphen, underscore or space characters."))

  implicit val tagIdCodec: Codec[TagId] = Codec.from(validatedGenericDecoder, validatedGenericEncoder)

}
