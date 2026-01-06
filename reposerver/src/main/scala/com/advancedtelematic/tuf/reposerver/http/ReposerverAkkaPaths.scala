package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.server.Directives.Segments
import org.apache.pekko.http.scaladsl.server.PathMatcher1
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.libtuf.data.TufDataType.ValidTargetFilename
import eu.timepit.refined.api.Refined

object ReposerverPekkoPaths {

  val TargetFilenamePath: PathMatcher1[Refined[String, ValidTargetFilename]] = Segments.flatMap {
    _.mkString("/").refineTry[ValidTargetFilename].toOption
  }

}
