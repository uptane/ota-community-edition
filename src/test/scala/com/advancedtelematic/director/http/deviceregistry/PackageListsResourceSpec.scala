package com.advancedtelematic.director.http.deviceregistry

import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes.{Created, NoContent, NotFound, OK}
import cats.syntax.show.*
import com.advancedtelematic.director.deviceregistry.data.Codecs.{
  packageListItemCodec,
  packageListItemCountCodec
}
import com.advancedtelematic.director.deviceregistry.data.DataType.{
  DeviceT,
  PackageListItem,
  PackageListItemCount
}
import com.advancedtelematic.director.deviceregistry.data.GeneratorOps.*
import com.advancedtelematic.director.deviceregistry.data.PackageId
import com.advancedtelematic.director.http.deviceregistry.DeviceRegistryResourceUri.uri
import com.advancedtelematic.libats.data.{ErrorCodes, ErrorRepresentation}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import org.scalacheck.{Arbitrary, Gen}
import com.advancedtelematic.director.deviceregistry.data.DeviceGenerators.*
import com.advancedtelematic.director.deviceregistry.data.PackageIdGenerators.*
import com.advancedtelematic.director.util.DirectorSpec

class PackageListsResourceSpec
    extends DirectorSpec
    with ResourcePropSpec
    with RegistryDeviceRequests {

  private val genNonConflictingDeviceTs = Gen.choose(0, 20).flatMap(genConflictFreeDeviceTs)

  private val genListedPackage: Gen[PackageListItem] =
    for {
      packageId <- genPackageId
      comment <- Gen.alphaNumStr
    } yield PackageListItem(defaultNs, packageId, comment)

  private implicit val arbListedPackage: org.scalacheck.Arbitrary[
    com.advancedtelematic.director.deviceregistry.data.DataType.PackageListItem
  ] =
    Arbitrary(genListedPackage)

  private implicit val arbNonConflictingDeviceTs: org.scalacheck.Arbitrary[Seq[
    com.advancedtelematic.director.deviceregistry.data.DataType.DeviceT
  ]] =
    Arbitrary(genNonConflictingDeviceTs)

  private def createListedPackage(listedPackage: PackageListItem): HttpRequest =
    Post(uri("package_lists"), listedPackage)

  private def createListedPackageOk(listedPackage: PackageListItem): Unit =
    createListedPackage(listedPackage) ~> routes ~> check {
      status shouldBe Created
    }

  private def getListedPackage(packageId: PackageId): HttpRequest =
    Get(uri("package_lists", packageId.name, packageId.version))

  private def getListedPackageOk(packageId: PackageId): PackageListItem =
    getListedPackage(packageId) ~> routes ~> check {
      status shouldBe OK
      responseAs[PackageListItem]
    }

  private def deleteListedPackage(packageId: PackageId): HttpRequest =
    Delete(uri("package_lists", packageId.name, packageId.version))

  private def deleteListedPackageOk(packageId: PackageId): Unit =
    deleteListedPackage(packageId) ~> routes ~> check {
      status shouldBe NoContent
    }

  private def updateListedPackageOk(patchedListedPackage: PackageListItem): Unit =
    Put(uri("package_lists"), patchedListedPackage) ~> routes ~> check {
      status shouldBe NoContent
    }

  private def updateInstalledPackages(deviceId: DeviceId, packageIds: Seq[PackageId]): Unit =
    Put(uri("mydevice", deviceId.show, "packages"), packageIds) ~> routes ~> check {
      status shouldBe NoContent
    }

  test("can create a listed package") {
    forAll { (listedPackage: PackageListItem) =>
      createListedPackageOk(listedPackage)
    }
  }

  test("can get a listed package") {
    forAll { (listedPackage: PackageListItem) =>
      createListedPackageOk(listedPackage)
      val actual = getListedPackageOk(listedPackage.packageId)
      actual shouldBe listedPackage
    }
  }

  test("fails to get a non-existing listed package") {
    forAll { (listedPackage: PackageListItem) =>
      getListedPackage(listedPackage.packageId) ~> routes ~> check {
        status shouldBe NotFound
        responseAs[ErrorRepresentation].code shouldBe ErrorCodes.MissingEntity
      }
    }
  }

  test("can delete a listed package") {
    forAll { (listedPackage: PackageListItem) =>
      createListedPackageOk(listedPackage)
      deleteListedPackageOk(listedPackage.packageId)
    }
  }

  test("deleting a non-existing listed package succeeds") {
    forAll { (listedPackage: PackageListItem) =>
      deleteListedPackageOk(listedPackage.packageId)
    }
  }

  test("can update the comment of a listed package") {
    forAll { (listedPackage: PackageListItem, newComment: String) =>
      createListedPackageOk(listedPackage)
      val patchedListedPackage = listedPackage.copy(comment = newComment)
      updateListedPackageOk(patchedListedPackage)
      getListedPackageOk(listedPackage.packageId) shouldBe patchedListedPackage
    }
  }

  test("updating the comment of a listed package succeeds") {
    forAll { (listedPackage: PackageListItem, newComment: String) =>
      val patchedListedPackage = listedPackage.copy(comment = newComment)
      updateListedPackageOk(patchedListedPackage)
      getListedPackage(listedPackage.packageId) ~> routes ~> check {
        status shouldBe NotFound
      }
    }
  }

  test("can count how many devices have installed each of the listed packages") {
    forAll(SizeRange(20)) { (deviceTs: Seq[DeviceT], packageIds: Seq[PackageId], comment: String) =>
      val listedPackages =
        Gen.someOf(packageIds).generate.map(PackageListItem(defaultNs, _, comment))
      val deviceIds = deviceTs.map(createDeviceOk)
      val devicesWithPackages = deviceIds.map(did => did -> Gen.someOf(packageIds).generate)

      val expected = devicesWithPackages
        .flatMap { case (did, pids) => pids.map(pid => pid -> did) }
        .groupBy(_._1)
        .view
        .filterKeys(listedPackages.map(_.packageId).contains)
        .mapValues(_.map(_._2).length)
        .map((PackageListItemCount.apply _).tupled)
        .toSeq

      listedPackages.foreach(createListedPackageOk)
      devicesWithPackages.foreach { case (id, pkgs) => updateInstalledPackages(id, pkgs.toSeq) }

      val actual = Get(uri("package_lists")) ~> routes ~> check {
        status shouldBe OK
        responseAs[Seq[PackageListItemCount]]
      }

      val actualInThisRun = actual.filter(bp => packageIds.contains(bp.packageId))
      actualInThisRun should contain theSameElementsAs expected
    }
  }

}
