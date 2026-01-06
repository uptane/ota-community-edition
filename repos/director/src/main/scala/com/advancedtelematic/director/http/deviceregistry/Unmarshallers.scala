package com.advancedtelematic.director.http.deviceregistry

import akka.http.scaladsl.unmarshalling.{PredefinedFromStringUnmarshallers, Unmarshaller}
import akka.http.scaladsl.util.FastFuture

object Unmarshallers {

  val nonNegativeLong: Unmarshaller[String, Long] =
    PredefinedFromStringUnmarshallers.longFromStringUnmarshaller
      .flatMap { _ => _ => value =>
        if (value < 0) FastFuture.failed(new IllegalArgumentException("Value cannot be negative"))
        else FastFuture.successful(value)
      }

}
