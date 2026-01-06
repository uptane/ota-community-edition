package com.advancedtelematic.tuf.cli.http

import java.net.URI
import com.advancedtelematic.libtuf.http.CliHttpClient
import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpBackend
import com.advancedtelematic.tuf.cli.DataType.{OAuth2Token, OAuthConfig}
import io.circe.Decoder
import sttp.client4.httpclient.HttpClientFutureBackend
import sttp.model.Uri

import scala.concurrent.{ExecutionContext, Future}

object OAuth2Client {

  def apply(conf: OAuthConfig)(implicit ec: ExecutionContext): OAuth2Client =
    new OAuth2Client(conf, HttpClientFutureBackend())

  def tokenFor(conf: OAuthConfig)(implicit ec: ExecutionContext): Future[OAuth2Token] =
    apply(conf).authToken()

}

protected class OAuth2Client(val config: OAuthConfig, httpBackend: CliHttpBackend)(
  implicit ec: ExecutionContext)
    extends CliHttpClient(httpBackend) {

  private def cognitoTokenRequest =
    http
      .post(Uri(URI.create(config.server.toString)))
      .auth
      .basic(config.client_id, config.client_secret)
      .body("grant_type" -> "client_credentials", "scope" -> config.scope)

  private def authPlusTokenRequest =
    http
      .post(Uri(URI.create(config.server.toString + "/token")))
      .auth
      .basic(config.client_id, config.client_secret)
      .body("grant_type" -> "client_credentials")

  private val tokenResponseDecoder =
    Decoder.decodeString.prepare(_.downField("access_token")).map(OAuth2Token.apply)

  def authToken(): Future[OAuth2Token] = {
    // We need this check for backwards-compatibility with previous versions of treehub.json.
    // Previous versions have the server URL *without* the token path, so it needs to be hardcoded.
    // The new version has the full URL with the `/oauth2/token` path at the end, so nothing needs
    // to be appended. Also we can't send the `scope` to Auth+, it confuses it.
    // This check can be removed after we finish the migration to Cognito. At that point there's
    // no need to attempt to be backwards-compatible anymore, since old credentials will have a
    // client_id that will have been removed from user-profile, and a server URL to Auth+, which
    // will be in computer heaven.
    val request =
      if (config.server.toString.endsWith("/token"))
        cognitoTokenRequest
      else
        authPlusTokenRequest

    implicit val _decoder = tokenResponseDecoder
    execHttp[OAuth2Token](request)().map(_.body)
  }

}
