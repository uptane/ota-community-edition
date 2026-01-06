package com.advancedtelematic.director.db.deviceregistry

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.slick.db.SlickExtensions._
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import com.advancedtelematic.director.http.deviceregistry.Errors.{
  ConflictingPackageListItem,
  MissingPackageListItem
}
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  PackageListItem,
  PackageListItemCount
}
import com.advancedtelematic.director.deviceregistry.data.PackageId
import Schema.devices
import InstalledPackages.installedPackages
import SlickMappings._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

object PackageListItemRepository {

  /** Each record in the table represents one element of a list of packages. The list of packages
    * are per namespace, i.e. if we group by namespace each group is a list of packages.
    */
  class PackageListItemTable(tag: Tag) extends Table[PackageListItem](tag, "PackageListItem") {
    def namespace = column[Namespace]("namespace")
    def packageName = column[PackageId.Name]("package_name")
    def packageVersion = column[PackageId.Version]("package_version")
    def comment = column[String]("comment")

    def * = (namespace, packageName, packageVersion, comment) <>
      (
        { case (ns, pkgName, pkgVersion, comment) =>
          PackageListItem(ns, PackageId(pkgName, pkgVersion), comment)
        },
        { (bp: PackageListItem) =>
          Some(bp.namespace, bp.packageId.name, bp.packageId.version, bp.comment)
        }
      )

    def pk = primaryKey("pk_PackageListItem", (namespace, packageName, packageVersion))
  }

  private[db] val packageListItems = TableQuery[PackageListItemTable]

  private def findQuery(namespace: Namespace, packageId: PackageId) =
    packageListItems
      .filter(_.namespace === namespace)
      .filter(_.packageName === packageId.name)
      .filter(_.packageVersion === packageId.version)

  def fetchPackageListItem(namespace: Namespace, packageId: PackageId)(
    implicit ec: ExecutionContext): DBIO[PackageListItem] =
    findQuery(namespace, packageId).resultHead(MissingPackageListItem)

  def fetchPackageListItemCounts(namespace: Namespace): DBIO[Seq[PackageListItemCount]] =
    devices
      .filter(_.namespace === namespace)
      .join(installedPackages)
      .on(_.id === _.device)
      .map(_._2)
      .groupBy(ip => (ip.name, ip.version))
      .map { case ((name, version), ips) => (name, version, ips.length) }
      .join(packageListItems.filter(_.namespace === namespace))
      .on { case ((name, version, _), bp) =>
        bp.packageName === name && bp.packageVersion === version
      }
      .map { case ((name, version, count), _) =>
        LiftedPackageListItemCount(LiftedPackageId(name, version), count)
      }
      .result

  def create(packageListItem: PackageListItem)(implicit ec: ExecutionContext): DBIO[Int] =
    (packageListItems += packageListItem).handleIntegrityErrors(ConflictingPackageListItem)

  def update(packageListItem: PackageListItem): DBIO[Int] =
    findQuery(packageListItem.namespace, packageListItem.packageId)
      .map(_.comment)
      .update(packageListItem.comment)

  def remove(namespace: Namespace, packageId: PackageId): DBIO[Int] =
    findQuery(namespace, packageId).delete

}
