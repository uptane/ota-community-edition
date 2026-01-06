/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.db.deviceregistry

import java.time.Instant
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import SlickMappings.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.{PackageId, PackageStat}
import com.advancedtelematic.director.deviceregistry.data.PackageId.Name
import DbOps.*
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext

object InstalledPackages {

  type InstalledPkgRow = (DeviceId, PackageId.Name, PackageId.Version, Instant)

  case class InstalledPackage(device: DeviceId, packageId: PackageId, lastModified: Instant)

  object InstalledPackage {
    import com.advancedtelematic.libats.codecs.CirceCodecs._

    implicit val EncoderInstance: io.circe.Encoder.AsObject[InstalledPackages.InstalledPackage] =
      io.circe.generic.semiauto.deriveEncoder[InstalledPackage]

  }

  case class DevicesCount(deviceCount: Int, groupIds: Set[GroupId])

  object DevicesCount {

    implicit val EncoderInstance: io.circe.Encoder.AsObject[InstalledPackages.DevicesCount] =
      io.circe.generic.semiauto.deriveEncoder[DevicesCount]

  }

  private def toTuple(fp: InstalledPackage): Option[InstalledPkgRow] =
    Some((fp.device, fp.packageId.name, fp.packageId.version, fp.lastModified))

  private def fromTuple(installedForeignPkgRow: InstalledPkgRow): InstalledPackage =
    installedForeignPkgRow match {
      case (device, name, version, lastModified) =>
        InstalledPackage(device, PackageId(name, version), lastModified)
    }

  class InstalledPackageTable(tag: Tag) extends Table[InstalledPackage](tag, "InstalledPackage") {
    def device = column[DeviceId]("device_uuid")
    def name = column[PackageId.Name]("name")
    def version = column[PackageId.Version]("version")
    def lastModified = column[Instant]("last_modified")(javaInstantMapping)

    def pk = primaryKey("pk_foreignInstalledPackage", (name, version, device))

    def * = (device, name, version, lastModified) <> (fromTuple, toTuple)
  }

  private[db] val installedPackages = TableQuery[InstalledPackageTable]

  def setInstalled(device: DeviceId, packages: Set[PackageId]): DBIO[Unit] =
    DBIO
      .seq(
        installedPackages.filter(_.device === device).delete,
        installedPackages ++= packages.map(InstalledPackage(device, _, Instant.now()))
      )
      .transactionally

  def installedOn(device: DeviceId,
                  nameContains: Option[String],
                  offset: Offset,
                  limit: Limit)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[InstalledPackage]] =
    installedPackages
      .filter(_.device === device)
      .maybeContains(
        ip => ip.name.mappedTo[String] ++ "-" ++ ip.version.mappedTo[String],
        nameContains
      )
      .paginateResult(offset, limit)

  def getDevicesCount(pkg: PackageId, ns: Namespace)(
    implicit ec: ExecutionContext): DBIO[DevicesCount] =
    for {
      devices <- installedPackages
        .filter(p => p.name === pkg.name && p.version === pkg.version)
        .join(Schema.devices)
        .on(_.device === _.id)
        .filter(_._2.namespace === ns)
        .map(_._1.device)
        .distinct
        .length
        .result
      groups <- installedPackages
        .filter(p => p.name === pkg.name && p.version === pkg.version)
        .join(GroupMemberRepository.groupMembers)
        .on(_.device === _.deviceUuid)
        .join(Schema.devices)
        .on(_._2.deviceUuid === _.id)
        .filter(_._2.namespace === ns)
        .map(_._1._2.groupId)
        .distinct
        .result
    } yield DevicesCount(devices, groups.toSet)

  private def installedForAllDevicesQuery(ns: Namespace): Query[
    (Rep[PackageId.Name], Rep[PackageId.Version]),
    (PackageId.Name, PackageId.Version),
    Seq
  ] =
    Schema.devices
      .filter(_.namespace === ns)
      .join(installedPackages)
      .on(_.id === _.device)
      .map(r => (r._2.name, r._2.version))
      .distinct

  def getInstalledForAllDevices(ns: Namespace)(
    implicit ec: ExecutionContext): DBIO[Seq[PackageId]] =
    installedForAllDevicesQuery(ns).result.map(_.map { case (name, version) =>
      PackageId(name, version)
    })

  def getInstalledForAllDevices(ns: Namespace, offset: Offset, limit: Limit)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[PackageId]] = {
    val query = installedForAllDevicesQuery(ns)
      .paginateAndSortResult(identity, offset, limit)
    query.map { nameVersionResult =>
      PaginationResult(
        nameVersionResult.values.map(nameVersion => PackageId(nameVersion._1, nameVersion._2)),
        nameVersionResult.total,
        nameVersionResult.offset,
        nameVersionResult.limit
      )
    }
  }

  protected[db] def inSetQuery(ids: Set[PackageId]): Query[InstalledPackageTable, _, Seq] =
    installedPackages.filter { pkg =>
      (pkg.name.mappedTo[String] ++ pkg.version.mappedTo[String])
        .inSet(ids.map(id => id.name.value + id.version.value))
    }

  // this isn't paginated as it's only intended to be called by core, hence it also not being in swagger
  def allInstalledPackagesById(namespace: Namespace,
                               ids: Set[PackageId]): DBIO[Seq[(DeviceId, PackageId)]] =
    inSetQuery(ids)
      .join(Schema.devices)
      .on(_.device === _.id)
      .filter(_._2.namespace === namespace)
      .map(r => (r._1.device, LiftedPackageId(r._1.name, r._1.version)))
      .result

  def listAllWithPackageByName(ns: Namespace,
                               name: Name,
                               offset: Offset,
                               limit: Limit)(
    implicit ec: ExecutionContext): DBIO[PaginationResult[PackageStat]] = {
    val query = installedPackages
      .filter(_.name === name)
      .join(Schema.devices)
      .on(_.device === _.id)
      .filter(_._2.namespace === ns)
      .groupBy(_._1.version)
      .map { case (version, installedPkg) => (version, installedPkg.length) }

    val pkgResult = query
      .paginate(offset, limit)
      .result
      .map(_.map { case (version, count) => PackageStat(version, count) })

    query.length.result.zip(pkgResult).map { case (total, values) =>
      PaginationResult(values, total, offset, limit)
    }
  }

}
