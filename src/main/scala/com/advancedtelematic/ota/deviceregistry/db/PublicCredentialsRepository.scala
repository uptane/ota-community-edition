/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.ota.deviceregistry.db

import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.ota.deviceregistry.common.Errors
import com.advancedtelematic.ota.deviceregistry.data.CredentialsType.CredentialsType
import com.advancedtelematic.ota.deviceregistry.db.SlickMappings._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

object PublicCredentialsRepository {
  case class DevicePublicCredentials(device: DeviceId, typeCredentials: CredentialsType, credentials: Array[Byte])

  class PublicCredentialsTable(tag: Tag) extends Table[DevicePublicCredentials](tag, "DevicePublicCredentials") {
    def device            = column[DeviceId]("device_uuid")
    def typeCredentials   = column[CredentialsType]("type_credentials")
    def publicCredentials = column[Array[Byte]]("public_credentials")

    def * =
      (device, typeCredentials, publicCredentials).shaped <>
      ((DevicePublicCredentials.apply _).tupled, DevicePublicCredentials.unapply)

    def pk = primaryKey("device_uuid", device)
  }

  val allPublicCredentials = TableQuery[PublicCredentialsTable]

  def findByUuid(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[DevicePublicCredentials] =
    allPublicCredentials
      .filter(_.device === uuid)
      .result
      .failIfNotSingle(Errors.MissingDevicePublicCredentials)

  def update(uuid: DeviceId, cType: CredentialsType, creds: Array[Byte])(
      implicit ec: ExecutionContext
  ): DBIO[Unit] =
    allPublicCredentials
      .insertOrUpdate(DevicePublicCredentials(uuid, cType, creds))
      .map(_ => ())

  def delete(uuid: DeviceId)(implicit ec: ExecutionContext): DBIO[Int] =
    allPublicCredentials.filter(_.device === uuid).delete
}
