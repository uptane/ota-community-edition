package com.advancedtelematic.tuf.keyserver

import com.advancedtelematic.libats.boot.VersionInfoProvider

trait VersionInfo extends com.advancedtelematic.libats.boot.VersionInfo {
  override protected lazy val provider: VersionInfoProvider = AppBuildInfo
}
