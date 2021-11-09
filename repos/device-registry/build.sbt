// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `ota-device-registry` =
  project
    .in(file("."))
    .enablePlugins(GitVersioning, BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.akkaAlpakkaCsv,
        library.akkaHttpTestKit % Test,
        library.akkaSlf4J,
        library.akkaStreamTestKit % Test,
        library.attoCore,
        library.circeTesting % Test,
        library.libTuf,
        library.mariaDb,
        library.scalaTest  % Test,
        library.toml,
      )
    )
    .settings(libraryDependencies ++= library.libAts)

// *****************************************************************************
// Library dependencies
// *****************************************************************************

libraryDependencies += "org.scalatestplus" %% "scalacheck-1-15" % "3.2.9.0" % "test"

lazy val library =
  new {
    object Version {
      val attoCore = "0.9.5"
      val scalaTest  = "3.2.9"
      val libAts     = "2.0.3"
      val libTuf = "0.8.1-26-gbdfd97a-SNAPSHOT"
      val akka = "2.6.17"
      val akkaHttp = "10.2.6"
      val alpakkaCsv = "2.0.0"
      val mariaDb = "2.7.3"
      val circe = "0.14.1"
      val toml = "0.2.2"
    }

    val scalaTest  = "org.scalatest"  %% "scalatest"  % Version.scalaTest
    val libAts = Seq(
      "libats-messaging",
      "libats-messaging-datatype",
      "libats-slick",
      "libats-http",
      "libats-metrics",
      "libats-metrics-akka",
      "libats-metrics-prometheus",
      "libats-http-tracing",
      "libats-logging"
    ).map("io.github.uptane" %% _ % Version.libAts)
    val libTuf = "io.github.uptane" %% "libtuf" % Version.libTuf
    val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % Version.akkaHttp
    val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % Version.akka
    val akkaAlpakkaCsv = "com.lightbend.akka" %% "akka-stream-alpakka-csv" % Version.alpakkaCsv
    val mariaDb = "org.mariadb.jdbc" % "mariadb-java-client" % Version.mariaDb
    val circeTesting = "io.circe" %% "circe-testing" % Version.circe
    val akkaSlf4J = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
    val toml = "tech.sparse" %% "toml-scala" % Version.toml
    val attoCore = "org.tpolecat" %% "atto-core" % Version.attoCore
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
commonSettings ++
gitSettings ++
buildInfoSettings ++
dockerSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.14",
    organization := "io.github.uptane",
    organizationName := "ATS Advanced Telematic Systems GmbH",
    name := "device-registry",
    startYear := Some(2017),
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += "sonatype-snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
    licenses += ("MPL-2.0", url("http://mozilla.org/MPL/2.0/")),
    scalacOptions ++= Seq(
      "-Ypartial-unification",
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding",
      "UTF-8"
    ),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories := Seq((Test / scalaSource).value),
  )

Compile / mainClass := Some("com.advancedtelematic.ota.deviceregistry.Boot")

lazy val gitSettings = Seq(
    git.useGitDescribe := true,
  )

import com.typesafe.sbt.packager.docker.Cmd
lazy val dockerSettings = Seq(
  dockerRepository := Some("advancedtelematic"),
  packageName := packageName.value,
  dockerBaseImage := "advancedtelematic/alpine-jre:adoptopenjdk-jre8u262-b10",
  dockerUpdateLatest := true,
  dockerAliases ++= Seq(dockerAlias.value.withTag(git.formattedShaVersion.value)),
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("USER", (Docker / daemonUser).value)
  )
)

lazy val buildInfoSettings = Seq(
  buildInfoOptions ++= Seq(BuildInfoOption.ToJson, BuildInfoOption.ToMap, BuildInfoOption.BuildTime),
  buildInfoObject := "AppBuildInfo",
  buildInfoPackage := "com.advancedtelematic.deviceregistry",
  buildInfoUsePackageAsPath := true,
  buildInfoOptions += BuildInfoOption.Traits("com.advancedtelematic.libats.boot.VersionInfoProvider")
)
