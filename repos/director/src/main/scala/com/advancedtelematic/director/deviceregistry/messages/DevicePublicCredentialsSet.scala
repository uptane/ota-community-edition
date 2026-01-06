/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.deviceregistry.messages

import java.time.Instant

import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.advancedtelematic.director.deviceregistry.data.CredentialsType.CredentialsType

final case class DevicePublicCredentialsSet(namespace: Namespace,
                                            uuid: DeviceId,
                                            credentialsType: CredentialsType,
                                            credentials: String,
                                            timestamp: Instant = Instant.now())

object DevicePublicCredentialsSet {

  import cats.syntax.show._
  import com.advancedtelematic.libats.codecs.CirceCodecs._

  implicit val MessageLikeInstance: com.advancedtelematic.libats.messaging_datatype.MessageLike[
    com.advancedtelematic.director.deviceregistry.messages.DevicePublicCredentialsSet
  ] = MessageLike.derive[DevicePublicCredentialsSet](_.uuid.show)

}
