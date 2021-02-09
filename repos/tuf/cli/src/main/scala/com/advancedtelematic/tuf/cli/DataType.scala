package com.advancedtelematic.tuf.cli

import java.net.URI
import java.nio.file.Path

import io.circe.Json

object DataType {
  case class KeyName(value: String) extends AnyVal {
    def publicKeyName: String = value + ".pub"
    def privateKeyName: String = value + ".sec"
  }

  case class RepoName(value: String) extends AnyVal

  case class TreehubConfig(oauth2: Option[OAuthConfig], no_auth: Boolean, ostree: Json)

  sealed trait CliAuth
  case class OAuthConfig(server: URI, client_id: String, client_secret: String) extends CliAuth
  case class MutualTlsConfig(certPath: Path, serverCertPath: Option[Path]) extends CliAuth

  sealed trait TufServerType
  case object RepoServer extends TufServerType
  case object Director extends TufServerType

  case class RepoConfig(reposerver: URI, auth: Option[CliAuth],
                        treehub: TreehubConfig,
                        repoServerType: TufServerType = RepoServer)

  case class AuthPlusToken(value: String) extends AnyVal
}
