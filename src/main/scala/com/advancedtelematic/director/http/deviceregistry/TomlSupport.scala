package com.advancedtelematic.director.http.deviceregistry

import org.apache.pekko.http.scaladsl.model.ContentType.WithFixedCharset
import org.apache.pekko.http.scaladsl.model.HttpCharsets.`UTF-8`
import org.apache.pekko.http.scaladsl.model.MediaType

object TomlSupport {

  val `application/toml`: WithFixedCharset =
    MediaType.applicationWithFixedCharset("toml", `UTF-8`).toContentType

}
