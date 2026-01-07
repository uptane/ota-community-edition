package com.advancedtelematic.treehub

import org.apache.pekko.http.scaladsl.unmarshalling.{PredefinedFromStringUnmarshallers, Unmarshaller}
import org.apache.pekko.http.scaladsl.util.FastFuture

package object http {
  val nonNegativeLong: Unmarshaller[String, Long] =
    PredefinedFromStringUnmarshallers.longFromStringUnmarshaller
      .flatMap { ec => mat => value =>
        if (value < 0) FastFuture.failed(new IllegalArgumentException("Value cannot be negative"))
        else FastFuture.successful(value)
      }
}
