// *****************************************************************************
// Projects
// *****************************************************************************

lazy val `ota-device-registry` =
  project
    .in(file("."))
    .enablePlugins(GitVersioning, BuildInfoPlugin, DockerPlugin, JavaAppPackaging)
    .settings(settings)
    .settings(
      resolvers += "Releases" at "https://nexus.ota.here.com/content/repositories/releases",
      resolvers += "Central" at "https://nexus.ota.here.com/content/repositories/central"
    )
    .settings(
      libraryDependencies ++= Seq(
        library.akkaAlpakkaCsv,
        library.akkaHttpTestKit % Test,
        library.akkaSlf4J,
        library.akkaStreamTestKit % Test,
        library.attoCore,
        library.circeTesting % Test,
        library.kafkaClient,
        library.libTuf,
        library.mariaDb,
        library.scalaCheck % Test,
        library.scalaTest  % Test,
        library.toml,
      )
    )
    .settings(libraryDependencies ++= library.libAts)

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val attoCore = "0.7.1"
      val scalaCheck = "1.14.1"
      val scalaTest  = "3.0.8"
      val libAts     = "0.4.0-17-ga03bec5-SNAPSHOT"
      val libTuf = "0.7.1-23-g3ea21d4-SNAPSHOT"
      val akka = "2.6.5"
      val akkaHttp = "10.1.12"
      val alpakkaCsv = "2.0.0"
      val mariaDb = "2.4.4"
      val circe = "0.12.3"
      val kafkaClient = "2.1.1"
      val toml = "0.2.2"
    }
    val scalaCheck = "org.scalacheck" %% "scalacheck" % Version.scalaCheck
    val scalaTest  = "org.scalatest"  %% "scalatest"  % Version.scalaTest
    val libAts = Seq(
      "libats-messaging",
      "libats-messaging-datatype",
      "libats-slick",
      "libats-auth",
      "libats-http",
      "libats-metrics",
      "libats-metrics-akka",
      "libats-metrics-prometheus",
      "libats-metrics-kafka",
      "libats-http-tracing",
      "libats-logging"
    ).map("com.advancedtelematic" %% _ % Version.libAts)
    val libTuf = "com.advancedtelematic" %% "libtuf-server" % Version.libTuf
    val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % Version.akkaHttp
    val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % Version.akka
    val akkaAlpakkaCsv = "com.lightbend.akka" %% "akka-stream-alpakka-csv" % Version.alpakkaCsv
    val mariaDb = "org.mariadb.jdbc" % "mariadb-java-client" % Version.mariaDb
    val circeTesting = "io.circe" %% "circe-testing" % Version.circe
    val akkaSlf4J = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
    val kafkaClient = "org.apache.kafka" % "kafka-clients" % Version.kafkaClient
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
dockerSettings ++
sonarSettings

lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.10",
    organization := "com.advancedtelematic",
    organizationName := "ATS Advanced Telematic Systems GmbH",
    name := "device-registry",
    startYear := Some(2017),
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
    unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
    unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value),
    // turn off the DotNet checker
    dependencyCheckAssemblyAnalyzerEnabled := Some(false),
    dependencyCheckSuppressionFiles := Seq(new File("dependency-check-suppressions.xml"))
  )

mainClass in Compile := Some("com.advancedtelematic.ota.deviceregistry.Boot")

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
    Cmd("USER", (daemonUser in Docker).value)
  )
)

lazy val buildInfoSettings = Seq(
  buildInfoOptions += BuildInfoOption.ToMap,
  buildInfoOptions += BuildInfoOption.BuildTime,
  buildInfoObject := "AppBuildInfo",
  buildInfoPackage := "com.advancedtelematic.deviceregistry",
  buildInfoUsePackageAsPath := true,
  buildInfoOptions += BuildInfoOption.Traits("com.advancedtelematic.libats.boot.VersionInfoProvider")
)

lazy val sonarSettings = Seq(
  sonarProperties ++= Map(
    "sonar.projectName" -> "OTA Connect Device Registry",
    "sonar.projectKey" -> "ota-connect-device-registry",
    "sonar.host.url" -> "http://sonar.in.here.com",
    "sonar.links.issue" -> "https://saeljira.it.here.com/projects/OTA/issues",
    "sonar.links.scm" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/ota-device-registry",
    "sonar.links.ci" -> "https://main.gitlab.in.here.com/olp/edge/ota/connect/back-end/ota-device-registry/pipelines",
    "sonar.projectVersion" -> version.value,
    "sonar.language" -> "scala"))
