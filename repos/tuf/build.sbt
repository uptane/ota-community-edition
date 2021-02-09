import CustomSettings._

def itFilter(name: String): Boolean = name endsWith "IntegrationSpec"

def unitFilter(name: String): Boolean = !itFilter(name)

lazy val ItTest = config("it").extend(Test)

lazy val UnitTest = config("ut").extend(Test)

lazy val commonConfigs = Seq(ItTest, UnitTest)

lazy val commonDeps = libraryDependencies ++= {
  val scalaTestV = "3.0.8"
  lazy val libatsV = libatsVersion.value

  Seq(
    "org.scala-lang.modules" %% "scala-async" % "0.9.6",
    "com.advancedtelematic" %% "libats" % libatsV,
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}

lazy val serverDependencies = libraryDependencies ++= {
  lazy val akkaV = "2.5.26"
  lazy val akkaHttpV = "10.1.11"
  lazy val libatsV = libatsVersion.value
  lazy val slickV = "3.2.0"
  lazy val catsV = "2.0.0"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
    "com.softwaremill.sttp.client" %% "akka-http-backend" % "2.0.6" % "test",

    "com.advancedtelematic" %% "libats-http" % libatsV,
    "com.advancedtelematic" %% "libats-http-tracing" % libatsV,
    "com.advancedtelematic" %% "libats-messaging" % libatsV,
    "com.advancedtelematic" %% "libats-metrics-akka" % libatsV,
    "com.advancedtelematic" %% "libats-metrics-prometheus" % libatsV,
    "com.advancedtelematic" %% "libats-slick" % libatsV,
    "com.advancedtelematic" %% "libats-logging" % libatsV,
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "org.mariadb.jdbc" % "mariadb-java-client" % "2.4.4",

    "org.typelevel" %% "cats-core" % catsV withSources(),
    "org.typelevel" %% "cats-kernel" % catsV,
    "org.typelevel" %% "cats-macros" % catsV
  )
}

lazy val commonSettings = Seq(
  organization := "com.advancedtelematic",
  scalaVersion := "2.12.10",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-Xexperimental", "-Ypartial-unification"),
  scalacOptions in (Compile, console) ~= (_.filterNot(_ == "-Ywarn-unused-import")),
  resolvers += "ATS Releases" at "https://nexus.ota.here.com/content/repositories/releases",
  resolvers += "ATS Snapshots" at "https://nexus.ota.here.com/content/repositories/snapshots",
  resolvers += "version99 Empty loggers" at "https://version99.qos.ch",
  libatsVersion := "0.4.0-17-ga03bec5-SNAPSHOT",
  licenses += ("MPL-2.0", url("http://mozilla.org/MPL/2.0/")),
  dependencyCheckAssemblyAnalyzerEnabled := Some(false)) ++
  Seq(inConfig(ItTest)(Defaults.testTasks): _*) ++
  Seq(inConfig(UnitTest)(Defaults.testTasks): _*) ++
  (testOptions in UnitTest := Seq(Tests.Filter(unitFilter))) ++
  (testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))) ++
  Versioning.settings ++
  commonDeps

lazy val sonarSettings = Seq(
  sonarProperties ++= Map(
    "sonar.projectName" -> "OTA Connect TUF",
    "sonar.projectKey" -> "ota-connect-tuf",
    "sonar.sources" -> "src/main/scala",
    "sonar.tests" -> "src/test/scala",
    "sonar.host.url" -> "http://sonar.in.here.com",
    "sonar.links.issue" -> "https://saeljira.it.here.com/projects/OTA/issues",
    "sonar.links.scm" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/ota-tuf",
    "sonar.links.ci" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/ota-tuf/pipelines",
    "sonar.language" -> "scala",
    "sonar.projectVersion" -> version.value,
    "sonar.modules" -> "libtuf,libtuf-server,keyserver,reposerver,cli",
    "libtuf.sonar.projectName" -> "OTA Connect Libtuf",
    "libtuf-server.sonar.projectName" -> "OTA Connect Libtuf Server",
    "keyserver.sonar.projectName" -> "OTA Connect TUF Keyserver",
    "reposerver.sonar.projectName" -> "OTA Connect TUF Repository Server",
    "cli.sonar.projectName" -> "OTA Connect TUF CLI (garage-sign)",
  )
)

lazy val libtuf = (project in file("libtuf"))
  .enablePlugins(Versioning.Plugin)
  .configs(commonConfigs:_*)
  .settings(commonSettings)
  .settings(Publish.settings)

lazy val libtuf_server = (project in file("libtuf-server"))
  .enablePlugins(Versioning.Plugin)
  .configs(commonConfigs:_*)
  .settings(commonSettings)
  .settings(serverDependencies)
  .settings(Publish.settings)
  .dependsOn(libtuf)

lazy val keyserver = (project in file("keyserver"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin, JavaAppPackaging)
  .configs(commonConfigs:_*)
  .settings(commonSettings)
  .settings(Packaging.docker("tuf-keyserver"))
  .settings(serverDependencies)
  .settings(BuildInfoSettings("com.advancedtelematic.tuf.keyserver"))
  .dependsOn(libtuf)
  .dependsOn(libtuf_server)

lazy val reposerver = (project in file("reposerver"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin, JavaAppPackaging)
  .configs(commonConfigs:_*)
  .settings(commonSettings)
  .settings(serverDependencies)
  .settings(Packaging.docker("tuf-reposerver"))
  .settings(BuildInfoSettings("com.advancedtelematic.tuf.reposerver"))
  .dependsOn(libtuf)
  .dependsOn(libtuf_server)

lazy val cli = (project in file("cli"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin, JavaAppPackaging, S3ReleasePlugin)
  .configs(commonConfigs:_*)
  .settings(commonSettings)
  .settings(Publish.disable)
  .settings(
    topLevelDirectory := Some("garage-sign"),
    executableScriptName := "garage-sign",
    mappings in Universal += (file("cli/LICENSE") -> "docs/LICENSE"),
    s3Bucket := "ota-tuf-cli-releases",
    libraryDependencies += "com.typesafe" % "config" % "1.3.4" % Test
  )
  .dependsOn(libtuf)

lazy val ota_tuf = (project in file("."))
  .settings(scalaVersion := "2.12.10")
  .settings(Publish.disable)
  .settings(Release.settings(libtuf, libtuf_server, keyserver, reposerver))
  .aggregate(libtuf_server, libtuf, keyserver, reposerver, cli)
  .settings(sonarSettings)
  .settings(aggregate in sonarScan := false)
