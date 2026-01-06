/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import org.apache.pekko.http.scaladsl.marshalling.Marshaller.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{Directive1, Route}
import org.apache.pekko.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.db.deviceregistry.{
  DeviceRepository,
  PublicCredentialsRepository
}
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.CredentialsType
import com.advancedtelematic.director.deviceregistry.data.CredentialsType.CredentialsType
import com.advancedtelematic.director.deviceregistry.data.DataType.DeviceT
import com.advancedtelematic.director.deviceregistry.messages.{
  DeviceCreated,
  DevicePublicCredentialsSet
}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

object PublicCredentialsResource {

  final case class FetchPublicCredentials(uuid: DeviceId,
                                          credentialsType: CredentialsType,
                                          credentials: String)

  implicit val fetchPublicCredentialsEncoder
    : io.circe.Encoder.AsObject[PublicCredentialsResource.FetchPublicCredentials] =
    io.circe.generic.semiauto.deriveEncoder[FetchPublicCredentials]

}

class PublicCredentialsResource(
  authNamespace: Directive1[Namespace],
  messageBus: MessageBusPublisher,
  deviceNamespaceAuthorizer: Directive1[DeviceId])(implicit db: Database, ec: ExecutionContext) {

  import PublicCredentialsResource.*
  import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*

  lazy val base64Decoder = Base64.getDecoder()
  lazy val base64Encoder = Base64.getEncoder()

  def fetchPublicCredentials(uuid: DeviceId): Route =
    complete(db.run(PublicCredentialsRepository.findByUuid(uuid)).map { creds =>
      FetchPublicCredentials(uuid, creds.typeCredentials, new String(creds.credentials))
    })

  def createDeviceWithPublicCredentials(ns: Namespace, devT: DeviceT): Route = {
    val act = devT.credentials match {
      case Some(credentials) =>
        val cType = devT.credentialsType.getOrElse(CredentialsType.PEM)
        val dbact = for {
          (created, uuid) <- DeviceRepository.findUuidFromUniqueDeviceIdOrCreate(
            ns,
            devT.deviceId,
            devT
          )
          _ <- PublicCredentialsRepository.update(uuid, cType, credentials.getBytes)
        } yield (created, uuid)

        for {
          (created, uuid) <- db.run(dbact.transactionally)
          _ <-
            if (created) {
              messageBus.publish(
                DeviceCreated(
                  ns,
                  uuid,
                  devT.deviceName,
                  devT.deviceId,
                  devT.deviceType,
                  Instant.now()
                )
              )
            } else { Future.successful(()) }
          _ <- messageBus.publish(
            DevicePublicCredentialsSet(ns, uuid, cType, credentials, Instant.now())
          )
        } yield uuid
      case None => FastFuture.failed(Errors.RequestNeedsCredentials)
    }
    complete(act)
  }

  def api: Route =
    (pathPrefix("devices") & authNamespace) { ns =>
      pathEnd {
        (put & entity(as[DeviceT])) { devT =>
          createDeviceWithPublicCredentials(ns, devT)
        }
      } ~
        deviceNamespaceAuthorizer { uuid =>
          path("public_credentials") {
            get {
              fetchPublicCredentials(uuid)
            }
          }
        }
    }

  val route: Route = api
}
