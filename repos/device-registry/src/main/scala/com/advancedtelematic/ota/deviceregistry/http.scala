package com.advancedtelematic.ota.deviceregistry

import akka.http.scaladsl.model.ContentType.WithFixedCharset
import akka.http.scaladsl.model.HttpCharsets.`UTF-8`
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.unmarshalling.{PredefinedFromStringUnmarshallers, Unmarshaller}
import akka.http.scaladsl.util.FastFuture

package object http {
  val `application/toml`: WithFixedCharset = MediaType.applicationWithFixedCharset("toml", `UTF-8`).toContentType

  val nonNegativeLong: Unmarshaller[String, Long] =
    PredefinedFromStringUnmarshallers.longFromStringUnmarshaller
      .flatMap { ec => mat => value =>
        if (value < 0) FastFuture.failed(new IllegalArgumentException("Value cannot be negative"))
        else FastFuture.successful(value)
      }
}
