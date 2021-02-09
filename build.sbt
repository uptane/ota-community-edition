name := "ota-lith"
organization := "com.advancedtelematic"
scalaVersion := "2.12.12"

resolvers += "ATS Releases" at "https://nexus.ota.here.com/content/repositories/releases"

resolvers += "ATS Snapshots" at "https://nexus.ota.here.com/content/repositories/snapshots"

updateOptions := updateOptions.value.withLatestSnapshots(false)

// todo: pin akka-http, akka versions

libraryDependencies ++= {
  val bouncyCastleV = "1.59"
  val tufV = "0.7.1-23-g3ea21d4-SNAPSHOT"
  val akkaV = "2.6.5"
  val akkaHttpV = "10.1.12"

  Seq(
//    "com.advancedtelematic" %% "director-v2" % "e55efb5b82384c53f9d6063e34513d64f90139ec-SNAPSHOT",
    "com.advancedtelematic" %% "keyserver" % tufV,
    "com.advancedtelematic" %% "reposerver" % tufV,

    "com.advancedtelematic" %% "libats" % "0.4.0-17-ga03bec5-SNAPSHOT",
    "com.advancedtelematic" %% "libats-slick" % "0.4.0-17-ga03bec5-SNAPSHOT",

    "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleV,

    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  )
}

// TODO: Add to libraryDependencies when done
lazy val treehub = (ProjectRef(file("./repos/treehub"), "root"))
lazy val device_registry = (ProjectRef(file("./repos/device-registry"), "ota-device-registry"))
lazy val campaigner = (ProjectRef(file("./repos/campaigner"), "campaigner"))
lazy val director = (ProjectRef(file("./repos/ats/director"), "director"))
lazy val keyserver = (ProjectRef(file("/home/simao/ats/ota-tuf"), "keyserver"))
lazy val reposerver = (ProjectRef(file("/home/simao/ats/ota-tuf"), "reposerver"))
// lazy val libats_slick = (ProjectRef(file("/home/simao/ats/libats"), "libats_slick"))
//lazy val crypt = (ProjectRef(file("/home/simao/ats/crypt-service"), "crypt-service"))
//lazy val user_profile = (ProjectRef(file("/home/simao/ats/ota-plus-user-profile"), "ota-plus-user-profile"))
//lazy val api_provider = (ProjectRef(file("/home/simao/ats/api-provider"), "root"))

dependsOn(treehub, device_registry, campaigner, director, keyserver, reposerver)

enablePlugins(BuildInfoPlugin, GitVersioning, JavaAppPackaging)

buildInfoOptions += BuildInfoOption.ToMap
buildInfoOptions += BuildInfoOption.BuildTime

mainClass in Compile := Some("com.advancedtelematic.ota_lith.OtaLithBoot")

import com.typesafe.sbt.packager.docker._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager.Docker
import DockerPlugin.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport._

dockerRepository in Docker := Some("advancedtelematic")

packageName in Docker := packageName.value

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

defaultLinuxInstallLocation in Docker := s"/opt/${moduleName.value}"

dockerCommands := Seq(
  Cmd("FROM", "advancedtelematic/alpine-jre:adoptopenjdk-jre8u262-b10"),
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
