import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.GitVersioning
import com.typesafe.sbt.git.ConsoleGitRunner

import sbt._

object Versioning {
  lazy val settings = Seq(
    git.useGitDescribe := true
  )

  val Plugin = GitVersioning
}
