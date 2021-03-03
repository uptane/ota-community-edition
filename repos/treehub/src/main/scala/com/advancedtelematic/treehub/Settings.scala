package com.advancedtelematic.treehub

import java.nio.file.Paths

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.treehub.object_store.S3Credentials
import com.amazonaws.regions.Regions
import com.typesafe.config.ConfigFactory

trait Settings {
  private lazy val _config = ConfigFactory.load().getConfig("ats.treehub")

  val host = _config.getString("http.server.host")
  val port = _config.getInt("http.server.port")

  val treeHubUri = {
    val uri = Uri(_config.getString("http.server.treehubUri"))
    if(!uri.isAbsolute) throw new IllegalArgumentException("Treehub host is not an absolute uri")
    uri
  }

  val localStorePath = Paths.get(_config.getString("storage.local.path"))

  val deviceRegistryUri = Uri(_config.getString("http.client.device_registry.baseUri"))
  val deviceRegistryMyApi = Uri(_config.getString("http.client.device_registry.mydeviceUri"))

  lazy val s3Credentials = {
    val accessKey = _config.getString("storage.s3.accessKey")
    val secretKey = _config.getString("storage.s3.secretKey")
    val objectBucketId = _config.getString("storage.s3.bucketId")
    val deltasBucketId = _config.getString("storage.s3.deltasBucketId")
    val region = Regions.fromName(_config.getString("storage.s3.region"))
    val endpointUrl = _config.getString("storage.s3.endpointUrl")

    new S3Credentials(accessKey, secretKey, objectBucketId, deltasBucketId, region, endpointUrl)
  }

  lazy val useS3 = _config.getString("storage.type").equals("s3")

  lazy val staleObjectExpireAfter = _config.getDuration("storage.staleObjectsExpireAfter")

  lazy val allowRedirectsToS3 = _config.getBoolean("storage.s3.allowRedirects")
}
