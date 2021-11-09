/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1, Directives}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.UUIDKey.{UUIDKey, UUIDKeyObj}
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId

import scala.concurrent.Future

object AllowUUIDPath {
  def deviceUUID(namespaceExtractor: Directive1[Namespace], allowFn: DeviceId => Future[Namespace]): Directive1[DeviceId] =
    apply(DeviceId)(namespaceExtractor, allowFn)

  def apply[T <: UUIDKey](idValue: UUIDKeyObj[T])
                         (namespaceExtractor: Directive1[Namespace], allowFn: T => Future[Namespace])
                         (implicit gen : idValue.SelfGen): Directive1[T] =
    (Directives.pathPrefix(idValue.Path(gen)) & namespaceExtractor).tflatMap {
      case (value, ans) =>
        onSuccess(allowFn(value)).flatMap {
          case namespace if namespace == ans =>
            provide(value)
          case _ =>
            reject(AuthorizationFailedRejection)
        }
    }
}
