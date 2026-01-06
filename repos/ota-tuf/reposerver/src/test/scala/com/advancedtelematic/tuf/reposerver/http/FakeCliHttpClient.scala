package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.server.Route
import com.advancedtelematic.tuf.reposerver.util.ResourceSpec
import sttp.client4.Backend
import sttp.client4.pekkohttp.{PekkoHttpBackend, PekkoHttpClient}

import scala.concurrent.Future

trait FakeCliHttpClient {
  self: ResourceSpec =>

  val testBackend: Backend[Future] =
    PekkoHttpBackend.usingClient(system, http = PekkoHttpClient.stubFromRoute(Route.seal(routes)))

}
