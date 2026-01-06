package com.advancedtelematic.tuf.cli.util

import com.advancedtelematic.tuf.cli.repo.TufRepo

object TufRepoNameOps {

  implicit class TufRepoNameConversion(value: TufRepo[_]) {
    def name: String = value.repoPath.getFileName.toString
  }

}
