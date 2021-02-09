package com.advancedtelematic.director

import akka.event.Logging
import akka.http.scaladsl.model.Uri
import com.typesafe.config.ConfigFactory

trait Settings {
  private lazy val _config = ConfigFactory.load().getConfig("ats.director")

  val host = _config.getString("http.server.host")
  val port = _config.getInt("http.server.port")

  val tufUri = Uri(_config.getString("http.client.keyserver.uri"))

  val requestLogLevel = Logging.levelFor(_config.getString("requestLogLevel")).getOrElse(Logging.DebugLevel)

  val allowEcuReplacement = _config.getBoolean("allowEcuReplacement")
}
