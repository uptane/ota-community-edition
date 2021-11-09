package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.server.Route
import com.advancedtelematic.tuf.reposerver.util.ResourceSpec
import sttp.client.SttpBackend
import sttp.client.akkahttp.{AkkaHttpBackend, AkkaHttpClient}
import scala.concurrent.Future

trait FakeCliHttpClient {
  self: ResourceSpec ⇒

  val testBackend: SttpBackend[Future, Nothing, Nothing] = {
    AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(Route.seal(routes)))
  }
}
