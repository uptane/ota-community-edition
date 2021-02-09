package com.advancedtelematic.director

import com.advancedtelematic.libats.boot.VersionInfoProvider

trait VersionInfo extends com.advancedtelematic.libats.boot.VersionInfo {
  override protected lazy val provider: VersionInfoProvider = AppBuildInfo
}
