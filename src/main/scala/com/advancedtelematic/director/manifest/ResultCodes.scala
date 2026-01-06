package com.advancedtelematic.director.manifest

import com.advancedtelematic.libats.data.DataType.ResultCode

object ResultCodes {

  val DirectorCancelledAssignment: ResultCode = ResultCode("101")
  val DeviceSentInvalidInstallationReport: ResultCode = ResultCode("102")
}
