package com.advancedtelematic.tuf.cli.http

import java.io.FileInputStream
import java.nio.file.Path
import java.security.{KeyStore, SecureRandom}
import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpBackend
import com.advancedtelematic.tuf.cli.DataType.OAuth2Token
import sttp.capabilities
import sttp.client4.{Backend, GenericRequest, Request, Response, WebSocketStreamBackend}

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory, X509TrustManager}
import sttp.client4.httpclient.HttpClientFutureBackend
import sttp.client4.logging.slf4j.Slf4jLoggingBackend
import sttp.monad.MonadError

import java.net.http.HttpClient
import scala.jdk.CollectionConverters.*
import scala.concurrent.Future

protected class AuthPlusCliHttpBackend[F[_]](token: OAuth2Token, delegate: Backend[F])
    extends Backend[F] {

  override def send[T](
    request: GenericRequest[T, Any with capabilities.Effect[F]]): F[Response[T]] = {
    val authReq = if (request.uri.host.exists(_.endsWith(".amazonaws.com"))) {
      request
    } else {
      request.auth.bearer(token.value)
    }
    delegate.send(authReq)
  }

  override def close(): F[Unit] = delegate.close()

  override def monad: MonadError[F] = delegate.monad

}

object AuthenticatedHttpBackend {
  lazy val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  lazy val trustFactory = TrustManagerFactory.getInstance("SunX509")

  def none: CliHttpBackend = defaultSttpBackend

  def authPlusHttpBackend(token: OAuth2Token): CliHttpBackend =
    new AuthPlusCliHttpBackend[Future](token, defaultSttpBackend)

  private def defaultSttpBackend = {
    val sttpBackend = HttpClientFutureBackend()
    Slf4jLoggingBackend[Future](sttpBackend)
  }

  def mutualTls(tlsCertPath: Path, serverCertPath: Option[Path]): CliHttpBackend = {
    val keyInput = new FileInputStream(tlsCertPath.toFile)
    val keyStore = KeyStore.getInstance("PKCS12", "BC")
    keyStore.load(keyInput, "".toCharArray)
    keyInput.close()
    keyManagerFactory.init(keyStore, "".toCharArray)

    val trustManagers = serverCertPath.map { serverP12 =>
      val trustInput = new FileInputStream(serverP12.toFile)
      val trustKeyStore = KeyStore.getInstance("PKCS12", "BC")
      trustKeyStore.load(trustInput, "".toCharArray)
      trustInput.close()

      trustFactory.init(trustKeyStore)
      trustFactory.getTrustManagers
    }

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagers.orNull, new SecureRandom())

    val client = HttpClient.newBuilder().sslContext(context).build()

    HttpClientFutureBackend.usingClient(client)
  }

}
