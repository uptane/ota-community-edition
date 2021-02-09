package com.advancedtelematic.tuf.cli.http

import java.net.URI

import com.advancedtelematic.libtuf.http.CliHttpClient
import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpBackend
import com.advancedtelematic.tuf.cli.DataType.{AuthPlusToken, OAuthConfig}
import io.circe.Decoder
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.Uri

import scala.concurrent.{ExecutionContext, Future}


object AuthPlusClient {
  def apply(conf: OAuthConfig)(implicit ec: ExecutionContext): AuthPlusClient =
    new AuthPlusClient(conf, AsyncHttpClientFutureBackend())

  def tokenFor(conf: OAuthConfig)(implicit ec: ExecutionContext): Future[AuthPlusToken] =
    apply(conf).authToken()
}


protected class AuthPlusClient(val config: OAuthConfig, httpBackend: CliHttpBackend)(implicit ec: ExecutionContext)
  extends CliHttpClient(httpBackend) {

  private def apiUri(path: String): Uri = Uri.apply(URI.create(config.server.toString + "/" + path))

  private val tokenResponseDecoder =
    Decoder.decodeString.prepare(_.downField("access_token")).map(AuthPlusToken.apply)

  def authToken(): Future[AuthPlusToken] = {
    val req = http.post(apiUri("token"))
      .auth.basic(config.client_id, config.client_secret)
      .body("grant_type" → "client_credentials")

    implicit val _decoder = tokenResponseDecoder
    execHttp[AuthPlusToken](req)().map(_.body)
  }
}
