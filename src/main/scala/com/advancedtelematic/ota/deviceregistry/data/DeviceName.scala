package com.advancedtelematic.ota.deviceregistry.data

import com.advancedtelematic.libats.codecs.CirceValidatedGeneric
import com.advancedtelematic.libats.data.{ValidatedGeneric, ValidationError}
import io.circe.{Decoder, Encoder}

final case class DeviceName private(value: String) extends AnyVal

object DeviceName {

  implicit val validatedDeviceType = new ValidatedGeneric[DeviceName, String] {
    override def to(deviceType: DeviceName): String = deviceType.value
    override def from(s: String): Either[ValidationError, DeviceName] = DeviceName.from(s)
  }

  def from(s: String): Either[ValidationError, DeviceName] =
    if (s.length > 200)
      Left(ValidationError(s"$s is not a valid DeviceName since it is longer than 200 characters"))
    else
      Right(new DeviceName(s))

  implicit val deviceNameEncoder: Encoder[DeviceName] = CirceValidatedGeneric.validatedGenericEncoder
  implicit val deviceNameDecoder: Decoder[DeviceName] = CirceValidatedGeneric.validatedGenericDecoder
}