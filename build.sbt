name := "director-v2"
organization := "io.github.uptane"
scalaVersion := "2.13.16"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-feature",
  "-Xlog-reflective-calls",
  "-Xasync",
  "-Xsource:3",
  "-Ywarn-unused",
  "-Wconf:cat=other-match-analysis:error"
)

resolvers += Resolver.mavenCentral
resolvers += "maven-snapshots"at "https://central.sonatype.com/repository/maven-snapshots"

Global / bloopAggregateSourceDependencies := true

libraryDependencies ++= {
  val pekkoV = "1.1.5"
  val pekkoHttpV = "1.2.0"
  val tufV = "5.0.0"
  val scalaTestV = "3.2.19"
  val bouncyCastleV = "1.80"
  val libatsV = "5.0.0"

  Seq(
    "org.apache.pekko" %% "pekko-actor" % pekkoV,
    "org.apache.pekko" %% "pekko-stream" % pekkoV,
    "org.apache.pekko" %% "pekko-http" % pekkoHttpV,
    "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoV,
    "org.apache.pekko" %% "pekko-slf4j" % pekkoV,
    "org.scalatest" %% "scalatest" % scalaTestV % Test,
    "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
    "io.github.uptane" %% "libats" % libatsV,
    "io.github.uptane" %% "libats-messaging" % libatsV,
    "io.github.uptane" %% "libats-messaging-datatype" % libatsV,
    "io.github.uptane" %% "libats-metrics-pekko" % libatsV,
    "io.github.uptane" %% "libats-metrics-prometheus" % libatsV,
    "io.github.uptane" %% "libats-http-tracing" % libatsV,
    "io.github.uptane" %% "libats-slick" % libatsV,
    "io.github.uptane" %% "libats-logging" % libatsV,
    "io.github.uptane" %% "libtuf" % tufV,
    "io.github.uptane" %% "libtuf-server" % tufV,
    "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleV,
    "org.scala-lang.modules" %% "scala-async" % "1.0.1",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
    "org.mariadb.jdbc" % "mariadb-java-client" % "3.5.7",
    "com.beachape" %% "enumeratum" % "1.9.0",
    "com.beachape" %% "enumeratum-circe" % "1.9.0",

    // Device registry specific dependencies
    "org.apache.pekko" %% "pekko-connectors-csv" % "1.0.0",
    "io.circe" %% "circe-testing" % "0.14.13",
    "tech.sparse" %% "toml-scala" % "0.2.2",
    "org.tpolecat" %% "atto-core" % "0.9.5",
    "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test
  )
}

javacOptions ++= Seq("-source", "21", "-target", "21")

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
  Tests.Argument(TestFrameworks.ScalaTest, "-oDS")
)

buildInfoObject := "AppBuildInfo"
buildInfoPackage := "com.advancedtelematic.director"
buildInfoUsePackageAsPath := true
buildInfoOptions += BuildInfoOption.Traits("com.advancedtelematic.libats.boot.VersionInfoProvider")
buildInfoOptions += BuildInfoOption.ToMap
buildInfoOptions += BuildInfoOption.BuildTime

enablePlugins(BuildInfoPlugin, GitVersioning, JavaAppPackaging)

Compile / mainClass := Some("com.advancedtelematic.director.Boot")

dockerRepository := Some("advancedtelematic")

Docker / packageName := packageName.value

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

Docker / defaultLinuxInstallLocation := s"/opt/${moduleName.value}"

dockerBaseImage := "eclipse-temurin:21.0.1_12-jre-jammy"

Docker / daemonUser := "daemon"

fork := true
