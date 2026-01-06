/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.slick.codecs.{SlickEnumMapper, SlickEnumeratum}
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  IndexedEventType,
  MqttStatus,
  PackageListItemCount
}
import com.advancedtelematic.director.deviceregistry.data.{CredentialsType, GroupType, PackageId}
import slick.jdbc.MySQLProfile.api.*

object SlickMappings {

  implicit val groupTypeMapper: slick.jdbc.MySQLProfile.BaseColumnType[
    com.advancedtelematic.director.deviceregistry.data.GroupType.Value
  ] = SlickEnumMapper.enumMapper(GroupType)

  implicit val credentialsTypeMapper: slick.jdbc.MySQLProfile.BaseColumnType[
    com.advancedtelematic.director.deviceregistry.data.CredentialsType.Value
  ] = SlickEnumMapper.enumMapper(CredentialsType)

  implicit val indexedEventTypeMapper: slick.jdbc.MySQLProfile.BaseColumnType[
    com.advancedtelematic.director.deviceregistry.data.DataType.IndexedEventType.Value
  ] = SlickEnumMapper.enumMapper(IndexedEventType)

  private[db] implicit val namespaceColumnType
    : slick.jdbc.MySQLProfile.BaseColumnType[com.advancedtelematic.libats.data.DataType.Namespace] =
    MappedColumnType.base[Namespace, String](_.get, Namespace.apply)

  private[db] case class LiftedPackageId(name: Rep[PackageId.Name], version: Rep[PackageId.Version])

  private[db] implicit object LiftedPackageShape
      extends CaseClassShape(
        LiftedPackageId.tupled,
        (p: (PackageId.Name, PackageId.Version)) => PackageId(p._1, p._2)
      )

  private[db] case class LiftedPackageListItemCount(packageId: LiftedPackageId,
                                                    deviceCount: Rep[Int])

  private[db] implicit object ListedPackageListItemCountShape
      extends CaseClassShape(LiftedPackageListItemCount.tupled, PackageListItemCount.tupled)

  implicit val mqttStatusMapper: BaseColumnType[MqttStatus] =
    SlickEnumeratum.enumeratumMapper(MqttStatus)

}
