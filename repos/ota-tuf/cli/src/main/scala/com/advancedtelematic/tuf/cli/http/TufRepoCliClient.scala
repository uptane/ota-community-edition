package com.advancedtelematic.tuf.cli.http

import java.net.URI

import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpBackend
import com.advancedtelematic.libtuf.http._
import com.advancedtelematic.tuf.cli.DataType.{MutualTlsConfig, OAuthConfig}
import com.advancedtelematic.tuf.cli.TryToFuture._
import com.advancedtelematic.tuf.cli.repo.TufRepo
import org.slf4j

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TufRepoCliClient {
  private val log = slf4j.LoggerFactory.getLogger(this.getClass)

  trait HttpClientBuilder[S <: TufServerClient] {
    def apply(reposerverUri: URI, auth: CliHttpBackend)(implicit ec: ExecutionContext): S
  }

  implicit val reposerverHttpClientBuilder
    : com.advancedtelematic.tuf.cli.http.TufRepoCliClient.HttpClientBuilder[
      com.advancedtelematic.libtuf.http.ReposerverClient
    ] = new HttpClientBuilder[ReposerverClient] {

    override def apply(reposerverUri: URI, httpBackend: CliHttpBackend)(
      implicit ec: ExecutionContext): ReposerverClient =
      new ReposerverHttpClient(reposerverUri, httpBackend)

  }

  implicit val directorHttpClientBuilder
    : com.advancedtelematic.tuf.cli.http.TufRepoCliClient.HttpClientBuilder[
      com.advancedtelematic.libtuf.http.DirectorClient
    ] = new HttpClientBuilder[DirectorClient] {

    override def apply(reposerverUri: URI, httpBackend: CliHttpBackend)(
      implicit ec: ExecutionContext): DirectorClient =
      new DirectorHttpClient(reposerverUri, httpBackend)

  }

  def forRepo[S <: TufServerClient](repo: TufRepo[S])(
    implicit ec: ExecutionContext,
    httpClientBuilder: HttpClientBuilder[S]): Future[S] =
    repo.authConfig match {
      case Success(Some(ac: OAuthConfig)) =>
        for {
          token <- OAuth2Client.tokenFor(ac)
          _ = log.debug(s"client token: ${token.value}")
          repoUri <- repo.repoServerUri.toFuture
        } yield httpClientBuilder.apply(
          repoUri,
          AuthenticatedHttpBackend.authPlusHttpBackend(token)
        )

      case Success(Some(tls: MutualTlsConfig)) =>
        repo.repoServerUri.toFuture.map { uri =>
          val clientCertPath = repo.repoPath.resolve(tls.certPath)
          val serverCertPath = tls.serverCertPath.map(repo.repoPath.resolve)

          httpClientBuilder.apply(
            uri,
            AuthenticatedHttpBackend.mutualTls(clientCertPath, serverCertPath)
          )
        }

      case Success(None) =>
        repo.repoServerUri.map { repoUri =>
          httpClientBuilder.apply(repoUri, AuthenticatedHttpBackend.none)
        }.toFuture

      case Failure(ex) => Future.failed(ex)
    }

}
