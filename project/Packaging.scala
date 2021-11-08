
import com.typesafe.sbt.packager.docker._
import sbt.Keys._
import sbt._
import com.typesafe.sbt.SbtNativePackager.Docker
import DockerPlugin.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._

object Packaging {
  def docker(distPackageName: String) = {
    Seq(
      Docker / dockerRepository := Some("advancedtelematic"),

      Docker / packageName := distPackageName,

      dockerUpdateLatest := true,

      dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value)),

      Docker / defaultLinuxInstallLocation := s"/opt/${moduleName.value}",

      dockerCommands := Seq(
        Cmd("FROM", "advancedtelematic/alpine-jre:adoptopenjdk-jre8u262-b10"),
        ExecCmd("RUN", "mkdir", "-p", s"/var/log/${moduleName.value}"),
        Cmd("ADD", "opt /opt"),
        Cmd("WORKDIR", s"/opt/${moduleName.value}"),
        ExecCmd("ENTRYPOINT", s"/opt/${moduleName.value}/bin/${moduleName.value}"),
        Cmd("RUN", s"chown -R daemon:daemon /opt/${moduleName.value}"),
        Cmd("RUN", s"chown -R daemon:daemon /var/log/${moduleName.value}"),
        Cmd("USER", "daemon")
      )
    )
  }
}
