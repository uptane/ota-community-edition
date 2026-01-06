package com.advancedtelematic.director.http.deviceregistry

import org.apache.pekko.http.scaladsl.model.StatusCodes.{Created, NoContent}
import org.apache.pekko.http.scaladsl.server.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import com.advancedtelematic.director.db.deviceregistry.PackageListItemRepository
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  PackageListItem,
  PackageListItemCount
}
import com.advancedtelematic.director.deviceregistry.data.PackageId
import com.advancedtelematic.libats.data.DataType.Namespace
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.generic.auto.*
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

/** This "package lists" feature has been migrated from the deprecated ota-core service, where it
  * used to be a "blacklisting" feature. It's been migrated here to terminate ota-core.
  *
  * The feature was not actually blacklisting anything. Instead, it was used to count the number of
  * devices that have a particular package installed. This are the installed packages reported by
  * aktualizr, e.g. 'nano' for a linux distribution, not to be confused with the software images we
  * can install through our system. The count of the packages is displayed in the "Impact" tab of
  * the web app.
  *
  * While moving it here, we've chosen to rename this to "package lists" instead of "blacklisted
  * packages", for lack of a better description of what the feature was being used for.
  */
class PackageListsResource(namespaceExtractor: Directive1[Namespace])(
  implicit db: Database,
  ec: ExecutionContext) {

  private val extractPackageId: Directive1[PackageId] =
    pathPrefix(Segment / Segment).as(PackageId.apply)

  private def getPackageListItem(ns: Namespace, packageId: PackageId): Future[PackageListItem] =
    db.run(PackageListItemRepository.fetchPackageListItem(ns, packageId))

  private def getPackageListItemCounts(ns: Namespace): Future[Seq[PackageListItemCount]] =
    db.run(PackageListItemRepository.fetchPackageListItemCounts(ns))

  private def createPackageListItem(packageListItem: PackageListItem): Future[Unit] =
    db.run(PackageListItemRepository.create(packageListItem).map(_ => ()))

  private def updatePackageListItem(patchedPackageListItem: PackageListItem): Future[Unit] =
    db.run(PackageListItemRepository.update(patchedPackageListItem).map(_ => ()))

  private def deletePackageListItem(ns: Namespace, packageId: PackageId): Future[Unit] =
    db.run(PackageListItemRepository.remove(ns, packageId).map(_ => ()))

  val route: Route = namespaceExtractor { namespace =>
    pathPrefix("package_lists") {
      pathEnd {
        get {
          complete(getPackageListItemCounts(namespace))
        } ~
          (post & entity(as[Namespace => PackageListItem])) { fn =>
            complete(Created -> createPackageListItem(fn(namespace)))
          } ~
          // This would better be as a PATCH /package_lists/package-name/package-version, but the UI is already sending this request.
          (put & entity(as[Namespace => PackageListItem])) { fn =>
            complete(NoContent -> updatePackageListItem(fn(namespace)))
          }
      } ~
        extractPackageId { packageId =>
          get {
            complete(getPackageListItem(namespace, packageId))
          } ~
            delete {
              complete(NoContent -> deletePackageListItem(namespace, packageId))
            }
        }
    }
  }

}
