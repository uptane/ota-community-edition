name := "campaigner"
organization := "com.advancedtelematic"
scalaVersion := "2.12.10"

resolvers += "sonatype-releases" at "https://s01.oss.sonatype.org/content/repositories/releases"
resolvers += "sonatype-snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

scalacOptions := Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-Xlint:_",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import"
)

// allow imports in the console on a single line
scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import"))

libraryDependencies ++= {
  val akkaV = "2.6.17"
  val akkaHttpV = "10.2.6"
  val libatsV = "2.0.3"
  val libtufV = "1.0.0"
  val scalaTestV = "3.0.8"
  val slickV = "3.2.0"

  Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % Test,
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "org.mariadb.jdbc" % "mariadb-java-client" % "2.4.4",
    "io.github.uptane" %% "libats" % libatsV,
    "io.github.uptane" %% "libats-http" % libatsV,
    "io.github.uptane" %% "libats-http-tracing" % libatsV,
    "io.github.uptane" %% "libats-messaging" % libatsV,
    "io.github.uptane" %% "libats-messaging-datatype" % libatsV,
    "io.github.uptane" %% "libats-metrics" % libatsV,
    "io.github.uptane" %% "libats-metrics-akka" % libatsV,
    "io.github.uptane" %% "libats-metrics-prometheus" % libatsV,
    "io.github.uptane" %% "libats-slick" % libatsV,
    "io.github.uptane" %% "libats-logging" % libatsV,
    "io.github.uptane" %% "libtuf" % libtufV,
    "io.github.uptane" %% "libtuf-server" % libtufV,
    "org.scalacheck" %% "scalacheck" % "1.14.1" % Test,
    "org.scalatest" %% "scalatest" % scalaTestV % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % Test
  )
}


buildInfoOptions += BuildInfoOption.ToMap
buildInfoOptions += BuildInfoOption.BuildTime
buildInfoObject := "AppBuildInfo"
buildInfoPackage := "com.advancedtelematic.campaigner"
buildInfoOptions += BuildInfoOption.Traits("com.advancedtelematic.libats.boot.VersionInfoProvider")

Compile / mainClass := Some("com.advancedtelematic.campaigner.Boot")

import com.typesafe.sbt.packager.docker._

dockerRepository := Some("advancedtelematic")

Docker / packageName := packageName.value

dockerUpdateLatest := true

dockerAlias := dockerAlias.value.withTag(git.gitHeadCommit.value)

defaultLinuxInstallLocation in Docker := s"/opt/${moduleName.value}"

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

enablePlugins(JavaAppPackaging, GitVersioning, BuildInfoPlugin)

