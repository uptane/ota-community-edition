package com.advancedtelematic.tuf.cli.repo

import java.net.URI
import java.nio.file.{Files, Paths}

import com.advancedtelematic.libtuf.http.ReposerverClient
import com.advancedtelematic.tuf.cli.DataType.RepoServer
import com.advancedtelematic.tuf.cli.http.TufRepoCliClient
import com.advancedtelematic.tuf.cli.util.{CliSpec, KeyTypeSpecSupport}
import com.typesafe.config.ConfigFactory

class RepoTlsAuthSpec extends CliSpec with KeyTypeSpecSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val credentialsZipTlsAuthEd25519 =
    Paths.get(this.getClass.getResource("/credentials_tls-auth_ed25519.zip").toURI)

  def randomRepoPath = Files.createTempDirectory("tuf-repo").resolve("repo")

  lazy val mtlsReposerverUri = new URI(
    ConfigFactory.load().getString("ats.tuf.cli.test.mtlsReposerverUri")
  )

  test("can pull targets when authenticated using tls") {
    val repo = RepoManagement
      .initialize(
        RepoServer,
        randomRepoPath,
        credentialsZipTlsAuthEd25519,
        repoUri = Some(mtlsReposerverUri)
      )
      .get
    val repo2 = new RepoServerRepo(repo.repoPath)

    val client = TufRepoCliClient.forRepo[ReposerverClient](repo2).futureValue

    repo2.repoServerUri.get shouldBe mtlsReposerverUri

    repo2.pullTargets(client).futureValue
  }

}
