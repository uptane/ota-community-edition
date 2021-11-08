name := "campaigner"
organization := "com.advancedtelematic"
scalaVersion := "2.12.10"

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

resolvers += "ATS Releases" at "https://nexus.ota.here.com/content/repositories/releases"

resolvers += "ATS Snapshots" at "https://nexus.ota.here.com/content/repositories/snapshots"

libraryDependencies ++= {
  val akkaV = "2.6.5"
  val akkaHttpV = "10.1.12"
  val libatsV = "0.4.0-8-g8c1c7f3"
  val libtufV = "0.7.1-6-gd36661d"
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
    "com.advancedtelematic" %% "libats" % libatsV,
    "com.advancedtelematic" %% "libats-http" % libatsV,
    "com.advancedtelematic" %% "libats-http-tracing" % libatsV,
    "com.advancedtelematic" %% "libats-auth" % libatsV,
    "com.advancedtelematic" %% "libats-messaging" % libatsV,
    "com.advancedtelematic" %% "libats-messaging-datatype" % libatsV,
    "com.advancedtelematic" %% "libats-metrics" % libatsV,
    "com.advancedtelematic" %% "libats-metrics-akka" % libatsV,
    "com.advancedtelematic" %% "libats-metrics-kafka" % libatsV,
    "com.advancedtelematic" %% "libats-metrics-prometheus" % libatsV,
    "com.advancedtelematic" %% "libats-slick" % libatsV,
    "com.advancedtelematic" %% "libats-logging" % libatsV,
    "com.advancedtelematic" %% "libtuf" % libtufV,
    "com.advancedtelematic" %% "libtuf-server" % libtufV,
    "org.scalacheck" %% "scalacheck" % "1.14.1" % Test,
    "org.scalatest" %% "scalatest" % scalaTestV % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % Test
  )
}

sonarProperties ++= Map(
  "sonar.projectName" -> "OTA Connect Campaigner",
  "sonar.projectKey" -> "ota-connect-campaigner",
  "sonar.host.url" -> "http://sonar.in.here.com",
  "sonar.links.issue" -> "https://saeljira.it.here.com/projects/OTA/issues",
  "sonar.links.scm" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/campaigner",
  "sonar.links.ci" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/campaigner/pipelines",
  "sonar.projectVersion" -> version.value,
  "sonar.language" -> "scala")


enablePlugins(BuildInfoPlugin)

buildInfoOptions += BuildInfoOption.ToMap

buildInfoOptions += BuildInfoOption.BuildTime

mainClass in Compile := Some("com.advancedtelematic.campaigner.Boot")

import com.typesafe.sbt.packager.docker._

dockerRepository := Some("advancedtelematic")

packageName in Docker := packageName.value

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

enablePlugins(JavaAppPackaging, GitVersioning)

