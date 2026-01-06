import com.github.sbt.git.SbtGit._
import com.github.sbt.git.GitVersioning
import com.github.sbt.git.ConsoleGitRunner

import sbt._

object Versioning {
  lazy val settings = Seq(git.useGitDescribe := true)

  val Plugin = GitVersioning
}
