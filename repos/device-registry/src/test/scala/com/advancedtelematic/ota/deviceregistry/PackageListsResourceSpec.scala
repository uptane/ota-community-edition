package com.advancedtelematic.ota.deviceregistry

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes.{Created, NoContent, NotFound, OK}
import cats.syntax.show._
import com.advancedtelematic.libats.data.{ErrorCodes, ErrorRepresentation}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.ota.deviceregistry.Resource.uri
import com.advancedtelematic.ota.deviceregistry.data.Codecs.{packageListItemCodec, packageListItemCountCodec}
import com.advancedtelematic.ota.deviceregistry.data.DataType.{PackageListItem, PackageListItemCount, DeviceT}
import com.advancedtelematic.ota.deviceregistry.data.GeneratorOps._
import com.advancedtelematic.ota.deviceregistry.data.PackageId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext

class PackageListsResourceSpec extends ResourcePropSpec with ScalaFutures {

  private val genNonConflictingDeviceTs = Gen.choose(0, 20).flatMap(genConflictFreeDeviceTs)
  private val genListedPackage: Gen[PackageListItem] =
    for {
      packageId <- genPackageId
      comment   <- Gen.alphaNumStr
    } yield PackageListItem(defaultNs, packageId, comment)

  private implicit val arbListedPackage = Arbitrary(genListedPackage)
  private implicit val arbNonConflictingDeviceTs = Arbitrary(genNonConflictingDeviceTs)

  private def createListedPackage(listedPackage: PackageListItem): HttpRequest =
    Post(uri("package_lists"), listedPackage)

  private def createListedPackageOk(listedPackage: PackageListItem)(implicit ec: ExecutionContext): Unit =
    createListedPackage(listedPackage) ~> route ~> check {
      status shouldBe Created
    }

  private def getListedPackage(packageId: PackageId): HttpRequest =
    Get(uri("package_lists", packageId.name, packageId.version))

  private def getListedPackageOk(packageId: PackageId)(implicit ec: ExecutionContext): PackageListItem =
    getListedPackage(packageId) ~> route ~> check {
      status shouldBe OK
      responseAs[PackageListItem]
    }

  private def deleteListedPackage(packageId: PackageId): HttpRequest =
    Delete(uri("package_lists", packageId.name, packageId.version))

  private def deleteListedPackageOk(packageId: PackageId)(implicit ec: ExecutionContext): Unit =
    deleteListedPackage(packageId) ~> route ~> check {
      status shouldBe NoContent
    }

  private def updateListedPackageOk(patchedListedPackage: PackageListItem): Unit =
    Put(uri("package_lists"), patchedListedPackage) ~> route ~> check {
      status shouldBe NoContent
    }

  private def updateInstalledPackages(deviceId: DeviceId, packageIds: Seq[PackageId]): Unit =
    Put(uri("mydevice", deviceId.show, "packages"), packageIds) ~> route ~> check {
      status shouldBe NoContent
    }

  property("can create a listed package") {
    forAll { listedPackage: PackageListItem =>
      createListedPackageOk(listedPackage)
    }
  }

  property("can get a listed package") {
    forAll { listedPackage: PackageListItem =>
      createListedPackageOk(listedPackage)
      val actual = getListedPackageOk(listedPackage.packageId)
      actual shouldBe listedPackage
    }
  }

  property("fails to get a non-existing listed package") {
    forAll { listedPackage: PackageListItem =>
      getListedPackage(listedPackage.packageId) ~> route ~> check {
        status shouldBe NotFound
        responseAs[ErrorRepresentation].code shouldBe ErrorCodes.MissingEntity
      }
    }
  }

  property("can delete a listed package") {
    forAll { listedPackage: PackageListItem =>
      createListedPackageOk(listedPackage)
      deleteListedPackageOk(listedPackage.packageId)
    }
  }

  property("deleting a non-existing listed package succeeds") {
    forAll { listedPackage: PackageListItem =>
      deleteListedPackageOk(listedPackage.packageId)
    }
  }

  property("can update the comment of a listed package") {
    forAll { (listedPackage: PackageListItem, newComment: String) =>
      createListedPackageOk(listedPackage)
      val patchedListedPackage = listedPackage.copy(comment = newComment)
      updateListedPackageOk(patchedListedPackage)
      getListedPackageOk(listedPackage.packageId) shouldBe patchedListedPackage
    }
  }

  property("updating the comment of a listed package succeeds") {
    forAll { (listedPackage: PackageListItem, newComment: String) =>
      val patchedListedPackage = listedPackage.copy(comment = newComment)
      updateListedPackageOk(patchedListedPackage)
      getListedPackage(listedPackage.packageId) ~> route ~> check {
        status shouldBe NotFound
      }
    }
  }

  property("can count how many devices have installed each of the listed packages") {
    forAll(SizeRange(20)) { (deviceTs: Seq[DeviceT], packageIds: Seq[PackageId], comment: String) =>

      val listedPackages = Gen.someOf(packageIds).generate.map(PackageListItem(defaultNs, _, comment))
      val deviceIds = deviceTs.map(createDeviceOk)
      val devicesWithPackages = deviceIds.map(did => did -> Gen.someOf(packageIds).generate)

      val expected = devicesWithPackages
        .flatMap { case (did, pids) => pids.map(pid => pid -> did) }
        .groupBy(_._1)
        .filterKeys(listedPackages.map(_.packageId).contains)
        .mapValues(_.map(_._2).length)
        .map((PackageListItemCount.apply _).tupled)
        .toSeq

      listedPackages.foreach(createListedPackageOk)
      devicesWithPackages.foreach((updateInstalledPackages _).tupled)

      val actual = Get(uri("package_lists")) ~> route ~> check {
        status shouldBe OK
        responseAs[Seq[PackageListItemCount]]
      }

      val actualInThisRun = actual.filter(bp => packageIds.contains(bp.packageId))
      actualInThisRun should contain theSameElementsAs expected
    }
  }

}
