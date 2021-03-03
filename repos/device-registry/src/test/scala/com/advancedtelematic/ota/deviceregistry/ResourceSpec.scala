/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry


import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import com.advancedtelematic.libats.auth.NamespaceDirectives
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.http.ServiceHttpClientSupport
import com.advancedtelematic.libats.http.tracing.NullServerRequestTracing
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.ota.deviceregistry.data.{DeviceGenerators, GroupGenerators, PackageIdGenerators, SimpleJsonGenerator}
import com.advancedtelematic.ota.deviceregistry.db.DeviceRepository
import org.scalatest.{BeforeAndAfterAll, Matchers, PropSpec, Suite}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.Future
import scala.concurrent.duration._

trait ResourceSpec
    extends ScalatestRouteTest
    with BeforeAndAfterAll
    with DatabaseSpec
    with DeviceGenerators
    with DeviceRequests
    with GroupGenerators
    with GroupRequests
    with PublicCredentialsRequests
    with PackageIdGenerators
    with Matchers
    with SimpleJsonGenerator with ServiceHttpClientSupport { // TODO: Use mock

  self: Suite =>

  implicit val routeTimeout: RouteTestTimeout =
    RouteTestTimeout(10.second)

  lazy val defaultNs: Namespace = Namespace("default")

  lazy val namespaceExtractor = NamespaceDirectives.defaultNamespaceExtractor

  private val namespaceAuthorizer = AllowUUIDPath.deviceUUID(namespaceExtractor, deviceAllowed)

  private def deviceAllowed(deviceId: DeviceId): Future[Namespace] =
    db.run(DeviceRepository.deviceNamespace(deviceId))

  lazy val messageBus = MessageBus.publisher(system, system.settings.config)

  implicit val tracing = new NullServerRequestTracing

  // Route
  lazy implicit val route: Route =
    new DeviceRegistryRoutes(namespaceExtractor, namespaceAuthorizer, messageBus).route
}

trait ResourcePropSpec extends PropSpec with ResourceSpec with ScalaCheckPropertyChecks
