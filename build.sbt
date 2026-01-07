name := "ota-lith"
organization := "io.github.uptane"
scalaVersion := "2.13.16"

updateOptions := updateOptions.value.withLatestSnapshots(false)

libraryDependencies ++= {
  val bouncyCastleV = "1.80"
  val pekkoV = "1.1.5"
  val pekkoHttpV = "1.2.0"

  Seq(
    "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleV,

    "org.apache.pekko" %% "pekko-actor" % pekkoV,
    "org.apache.pekko" %% "pekko-stream" % pekkoV,
    "org.apache.pekko" %% "pekko-http" % pekkoHttpV,
  )
}

lazy val treehub = (ProjectRef(file("./repos/treehub"), "treehub"))
lazy val director = (ProjectRef(file("./repos/director"), "director"))
lazy val keyserver = (ProjectRef(file("./repos/ota-tuf"), "keyserver"))
lazy val reposerver = (ProjectRef(file("./repos/ota-tuf"), "reposerver"))

dependsOn(treehub, director, keyserver, reposerver)

enablePlugins(BuildInfoPlugin, GitVersioning, JavaAppPackaging)

buildInfoOptions += BuildInfoOption.ToMap
buildInfoOptions += BuildInfoOption.BuildTime

Compile / mainClass := Some("com.advancedtelematic.ota_lith.OtaLithCombinedBoot")

import com.typesafe.sbt.packager.docker._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager.Docker
import DockerPlugin.autoImport._
import com.github.sbt.git.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._

Docker / dockerRepository := Some("uptane")

Docker / packageName := packageName.value

dockerUpdateLatest := true

Docker / dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

Docker / defaultLinuxInstallLocation := s"/opt/${moduleName.value}"

dockerCommands := Seq(
  Cmd("FROM", "eclipse-temurin:21-jre"),
  ExecCmd("RUN", "mkdir", "-p", s"/var/log/${moduleName.value}"),
  Cmd("ADD", "opt /opt"),
  Cmd("WORKDIR", s"/opt/${moduleName.value}"),
  ExecCmd("ENTRYPOINT", s"/opt/${moduleName.value}/bin/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /opt/${moduleName.value}"),
  Cmd("RUN", s"mkdir /var/lib/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /var/lib/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /var/log/${moduleName.value}"),
  Cmd("USER", "daemon")
)

// fork := true // TODO: Not compatible with .properties ?
