package com.advancedtelematic.director.deviceregistry.data

import com.advancedtelematic.libats.codecs.CirceValidatedGeneric
import com.advancedtelematic.libats.data.{ValidatedGeneric, ValidationError}
import io.circe.{Decoder, Encoder}

import scala.annotation.nowarn

@nowarn
final case class GroupName private (value: String) extends AnyVal

object GroupName {

  implicit val validatedGroupName: com.advancedtelematic.libats.data.ValidatedGeneric[
    com.advancedtelematic.director.deviceregistry.data.GroupName,
    String
  ] = new ValidatedGeneric[GroupName, String] {
    override def to(expression: GroupName): String = expression.value
    override def from(s: String): Either[ValidationError, GroupName] = GroupName.from(s)
  }

  def from(s: String): Either[ValidationError, GroupName] =
    if (s.length < 2 || s.length > 100)
      Left(ValidationError(s"$s should be between two and a hundred alphanumeric characters long."))
    else
      Right(new GroupName(s))

  implicit val groupNameEncoder: Encoder[GroupName] = CirceValidatedGeneric.validatedGenericEncoder
  implicit val groupNameDecoder: Decoder[GroupName] = CirceValidatedGeneric.validatedGenericDecoder
}
