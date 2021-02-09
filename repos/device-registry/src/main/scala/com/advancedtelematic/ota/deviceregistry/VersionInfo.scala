/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import com.advancedtelematic.deviceregistry.AppBuildInfo
import com.advancedtelematic.libats.boot.VersionInfoProvider

trait VersionInfo extends com.advancedtelematic.libats.boot.VersionInfo {
  override protected lazy val provider: VersionInfoProvider = AppBuildInfo
}
