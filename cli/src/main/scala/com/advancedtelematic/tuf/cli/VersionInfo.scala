package com.advancedtelematic.tuf.cli

trait VersionInfo {
  lazy val projectName: String = buildinfo.BuildInfo.name

  lazy val projectVersion: String = buildinfo.BuildInfo.version

  lazy val versionMap: Map[String, Any] = buildinfo.BuildInfo.toMap
}
