import com.github.sbt.git.SbtGit._
import com.github.sbt.git.SbtGit.GitKeys._
import com.github.sbt.git.GitVersioning
import scala.util.Try
import sbt._
import sbt.Keys._

object Versioning {
  lazy val settings = Seq(
    git.useGitDescribe := true
  )
}
