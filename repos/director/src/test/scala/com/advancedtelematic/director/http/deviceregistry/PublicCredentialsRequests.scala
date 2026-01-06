/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes}
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.CredentialsType.CredentialsType
import com.advancedtelematic.director.deviceregistry.data.DataType.DeviceT
import com.advancedtelematic.director.deviceregistry.data.DeviceName.validatedDeviceType
import com.advancedtelematic.director.http.deviceregistry.PublicCredentialsResource.FetchPublicCredentials
import com.advancedtelematic.director.util.ResourceSpec
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId

import java.util.Base64

trait PublicCredentialsRequests { self: ResourceSpec =>

  import StatusCodes.*
  import com.advancedtelematic.director.deviceregistry.data.Device.*
  import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*

  private val credentialsApi = "devices"

  private lazy val base64Decoder = Base64.getDecoder
  private lazy val base64Encoder = Base64.getEncoder

  def fetchPublicCredentials(device: DeviceId): HttpRequest = {
    import cats.syntax.show.*
    Get(DeviceRegistryResourceUri.uri(credentialsApi, device.show, "public_credentials"))
  }

  def fetchPublicCredentialsOk(device: DeviceId): Array[Byte] =
    fetchPublicCredentials(device) ~> routes ~> check {
      implicit val CredentialsDecoder =
        io.circe.generic.semiauto.deriveDecoder[FetchPublicCredentials]
      status shouldBe OK
      val resp = responseAs[FetchPublicCredentials]
      base64Decoder.decode(resp.credentials)
    }

  def createDeviceWithCredentials(devT: DeviceT): HttpRequest =
    Put(DeviceRegistryResourceUri.uri(credentialsApi), devT)

  def updatePublicCredentials(device: DeviceOemId,
                              creds: Array[Byte],
                              cType: Option[CredentialsType]): HttpRequest = {
    val devT = validatedDeviceType
      .from(device.underlying)
      .map(
        DeviceT(None, _, device, DeviceType.Other, Some(base64Encoder.encodeToString(creds)), cType)
      )
    createDeviceWithCredentials(devT.toOption.get)
  }

  def updatePublicCredentialsOk(device: DeviceOemId,
                                creds: Array[Byte],
                                cType: Option[CredentialsType] = None): DeviceId =
    updatePublicCredentials(device, creds, cType) ~> routes ~> check {
      status shouldBe OK
      responseAs[DeviceId]
    }

}
