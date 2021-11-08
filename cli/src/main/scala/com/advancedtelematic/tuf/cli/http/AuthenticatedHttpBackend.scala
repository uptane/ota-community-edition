package com.advancedtelematic.tuf.cli.http

import java.io.FileInputStream
import java.nio.file.Path
import java.security.{KeyStore, SecureRandom}

import com.advancedtelematic.libtuf.http.CliHttpClient.CliHttpBackend
import com.advancedtelematic.tuf.cli.DataType.OAuth2Token
import io.netty.handler.ssl.{SslContextBuilder, SupportedCipherSuiteFilter}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory, X509TrustManager}
import sttp.client.{SttpBackend, _}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.logging.slf4j.{Slf4jCurlBackend, Slf4jLoggingBackend}
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.higherKinds

protected class AuthPlusCliHttpBackend[F[_], S, WS_HANDLER[_]](token: OAuth2Token, delegate: SttpBackend[F, S, WS_HANDLER]) extends SttpBackend[F, S, WS_HANDLER] {
  override def send[T](request: Request[T, S]): F[Response[T]] = {
    val authReq = if(request.uri.host.endsWith(".amazonaws.com")) {
      request
    } else {
      request.auth.bearer(token.value)
    }
    delegate.send(authReq)
  }

  override def openWebsocket[T, WS_RESULT](request: Request[T, S], handler: WS_HANDLER[WS_RESULT]): F[WebSocketResponse[WS_RESULT]] = {
    val authReq = request.auth.bearer(token.value)
    delegate.openWebsocket(authReq, handler)
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object AuthenticatedHttpBackend {
  lazy val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  lazy val trustFactory = TrustManagerFactory.getInstance("SunX509")

  def none: CliHttpBackend = defaultSttpBackend

  def authPlusHttpBackend(token: OAuth2Token): CliHttpBackend = {
    new AuthPlusCliHttpBackend[Future, Nothing, Nothing](token, defaultSttpBackend)
  }

  private def defaultSttpBackend = {
    val sttpBackend = AsyncHttpClientFutureBackend()
    Slf4jLoggingBackend[Future, Nothing, Nothing](Slf4jCurlBackend[Future, Nothing, Nothing](sttpBackend))
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

    val trustManager = trustManagers.flatMap(_.headOption).map(_.asInstanceOf[X509TrustManager]).orNull

    val sslContext = SslContextBuilder.forClient.startTls(true)
      .ciphers(context.getDefaultSSLParameters.getCipherSuites.toIterable.asJava, SupportedCipherSuiteFilter.INSTANCE)
      .trustManager(trustManager)
      .protocols(context.getDefaultSSLParameters.getProtocols.toIterable.asJava)
      .keyManager(keyManagerFactory)
      .build()

    AsyncHttpClientFutureBackend.usingConfigBuilder(builder => builder.setSslContext(sslContext))
  }
}
