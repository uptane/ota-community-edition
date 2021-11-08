package com.advancedtelematic.ota.deviceregistry

import akka.http.scaladsl.model.ContentType.WithFixedCharset
import akka.http.scaladsl.model.HttpCharsets.`UTF-8`
import akka.http.scaladsl.model.MediaType

package object http {
  val `application/toml`: WithFixedCharset = MediaType.applicationWithFixedCharset("toml", `UTF-8`).toContentType
}
