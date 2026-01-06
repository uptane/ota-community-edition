package com.advancedtelematic.director.http.deviceregistry

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path

object DeviceRegistryResourceUri {

  def uri(pathSuffixes: String*): Uri = {
    val BasePath = Path("/device-registry/api") / "v1"
    Uri.Empty.withPath(pathSuffixes.foldLeft(BasePath)(_ / _))
  }

  def uriV2(pathSuffixes: String*): Uri = {
    val BasePath = Path("/device-registry/api") / "v2"
    Uri.Empty.withPath(pathSuffixes.foldLeft(BasePath)(_ / _))
  }

}
