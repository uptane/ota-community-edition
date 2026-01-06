/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.data

final object CredentialsType extends Enumeration {
  type CredentialsType = Value
  val PEM, OAuthClientCredentials = Value

  implicit val EncoderInstance: io.circe.Encoder[Value] =
    io.circe.Encoder.encodeEnumeration(CredentialsType)

  implicit val DecoderInstance: io.circe.Decoder[Value] =
    io.circe.Decoder.decodeEnumeration(CredentialsType)

}
