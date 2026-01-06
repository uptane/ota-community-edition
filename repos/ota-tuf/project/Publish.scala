import com.jsuereth.sbtpgp.SbtPgp.autoImport.usePgpKeyHex
import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype.GitHubHosting
import xerial.sbt.Sonatype.autoImport._
import xerial.sbt.Sonatype.sonatypeCentralHost

import java.net.URI

object Publish {

  private def readSettings(envKey: String, propKey: Option[String] = None): String =
    sys.env
      .get(envKey)
      .orElse(propKey.flatMap(sys.props.get(_)))
      .getOrElse("")

  lazy val repoHost = URI.create(repoUrl).getHost

  lazy val repoUser = readSettings("PUBLISH_USER")

  lazy val repoPassword = readSettings("PUBLISH_PASSWORD")

  lazy val repoUrl = readSettings("PUBLISH_URL")

  lazy val repoRealm = readSettings("PUBLISH_REALM")

  lazy val settings = Seq(
    usePgpKeyHex("6ED5E5ABE9BF80F173343B98FFA246A21356D296"),
    isSnapshot := version.value.trim.endsWith("SNAPSHOT"),
    pomIncludeRepository := { _ => false },
    sonatypeCredentialHost := sonatypeCentralHost,
    publishMavenStyle := true,
    sonatypeProjectHosting := Some(GitHubHosting("uptane", "ota-tuf", "releases@uptane.github.io")),
    credentials += Credentials(repoRealm, repoHost, repoUser, repoPassword),
    publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
      else sonatypePublishToBundle.value
    }
  )

  lazy val disable = Seq(
    sonatypeCredentialHost := sonatypeCentralHost,
    sonatypeProfileName := "io.github.uptane",
    publish / skip := true,
    publishArtifact := true,
    publish := (()),
    publishTo := None,
    publishLocal := (())
  )

}
