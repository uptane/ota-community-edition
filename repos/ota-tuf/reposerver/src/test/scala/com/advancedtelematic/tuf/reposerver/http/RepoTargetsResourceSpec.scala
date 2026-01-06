package com.advancedtelematic.tuf.reposerver.http

import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.tuf.reposerver.data.RepoCodecs.*
import org.scalatest.LoneElement.*
import cats.implicits.*
import io.circe.syntax.*
import org.scalatest.OptionValues.*
import org.apache.pekko.http.scaladsl.model.{HttpEntity, StatusCodes}
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.tuf.reposerver.util.{RepoResourceDelegationsSpecUtil, ResourceSpec, TufReposerverSpec}
import com.advancedtelematic.tuf.reposerver.util.NamespaceSpecOps.*
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.*
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.Package.*
import com.advancedtelematic.libats.data.DataType.HashMethod
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, SignedPayload, TargetFilename, ValidTargetFilename}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import eu.timepit.refined.api.Refined
import io.circe.Json
import com.advancedtelematic.libats.codecs.CirceRefined.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import PaginationResult.*
import com.advancedtelematic.libtuf.data.PackageSearchParameters
import eu.timepit.refined.refineMV

class RepoTargetsResourceSpec
    extends TufReposerverSpec
    with ResourceSpec
    with RepoResourceDelegationsSpecUtil {

  val testEntity = HttpEntity(ByteString("""
       The library will endure; it is the universe. As for us, everything has not been written; we are not turning into phantoms. We walk the corridors, searching the shelves and rearranging them, looking for lines of meaning amid leagues of cacophony and incoherence, reading the history of the past and our future, collecting our thoughts and collecting the thoughts of others, and every so often glimpsing mirrors, in which we may recognize creatures of the information.”
      """.stripMargin))

  val testEntity2 = HttpEntity(ByteString("""
      If honor and wisdom and happiness are not for me, let them be for others. Let heaven exist, though my place be in hell
      """.stripMargin))

  testWithRepo("GET returns delegation items ") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Post(apiUriV2(s"user_repo/search")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val values = responseAs[PaginationResult[Package]].values

      values should have size 2

      val targetItem = values.find(_.name.value == "library").value

      targetItem.version.value shouldBe "0.0.1"
      targetItem.hardwareIds.map(_.value) should contain("myid001")

      val delegatedItem = values.find(_.name.value == "mytargetName").value

      delegatedItem.version.value shouldBe "0.0.2"
      delegatedItem.hardwareIds.map(_.value) should contain("delegated-hardware-id-001")

      responseAs[PaginationResult[Package]].total shouldBe 2
    }

  }

  testWithRepo("deleted items are no longer returned") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Post(apiUriV2(s"user_repo/search")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val signedDelegationRole1 = buildSignedDelegatedTargets(targets = Map.empty)

    pushSignedDelegatedMetadataOk(signedDelegationRole1)

    Post(apiUriV2(s"user_repo/search")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val values = responseAs[PaginationResult[Package]].values

      values should be(empty)
    }

  }

  testWithRepo("delegated items are updated when delegated metadata is updated") {
    implicit ns => implicit repoId =>
      addTargetToRepo(repoId)

      uploadOfflineSignedTargetsRole()

      val signedDelegationRole = buildSignedDelegatedTargets()

      pushSignedDelegatedMetadataOk(signedDelegationRole)

      Post(apiUriV2(s"user_repo/search")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val values = responseAs[PaginationResult[Package]].values
        values should have size 1
      }

      val newTargets: Map[TargetFilename, ClientTargetItem] = Map(
        refineMV[ValidTargetFilename]("mypath/othertarget") -> ClientTargetItem(
          Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
          2,
          Json
            .obj(
              "name" -> "customname".asJson,
              "version" -> "0.0.3".asJson,
              "hardwareIds" -> List("delegated-hardware-id-001").asJson
            )
            .some
        )
      )

      val signedDelegationRole1 = buildSignedDelegatedTargets(targets = newTargets)

      pushSignedDelegatedMetadataOk(signedDelegationRole1)

      Post(apiUriV2(s"user_repo/search")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK

        val value = responseAs[PaginationResult[Package]].values.loneElement
        value.name.value shouldBe "customname"
      }

  }

  testWithRepo("filters by NOT origin") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params = PackageSearchParameters.empty.copy(originNot = Option("123"))

    Post(apiUriV2(s"user_repo/search"), params).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 2
    }

    val params1 = PackageSearchParameters.empty.copy(originNot = Option("targets.json"))

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values.loneElement.origin.value shouldBe delegatedRoleName.value
    }

    val params2 = PackageSearchParameters.empty.copy(originNot = delegatedRoleName.value.some)

    Post(apiUriV2(s"user_repo/search"), params2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values.loneElement.origin.value shouldBe "targets.json"
    }
  }

  testWithRepo("filters by origin") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params = PackageSearchParameters.empty.copy(origin = Seq("targets.json"))

    Post(apiUriV2(s"user_repo/search"), params).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params1 = PackageSearchParameters.empty.copy(origin = Seq(delegatedRoleName.value))

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params2 =
      PackageSearchParameters.empty.copy(origin = Seq(delegatedRoleName.value, "targets.json"))

    Post(apiUriV2(s"user_repo/search"), params2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 2
    }

    val params3 =
      PackageSearchParameters.empty.copy(origin = Seq("doesnotexist"))

    Post(apiUriV2(s"user_repo/search"), params3).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }
  }

  testWithRepo("GET single pkg by filename") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(
      apiUriV2(s"user_repo/packages/mypkg?originNot=${delegatedRoleName.value}")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val value = responseAs[Package]
      value.name.value shouldBe "library"
    }

    Get(
      apiUriV2(s"user_repo/packages/mypath/mytargetName?originNot=targets.json")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val value = responseAs[Package]
      value.filename.value shouldBe "mypath/mytargetName"
    }
  }

  testWithRepo("filters by name, version") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params =
      PackageSearchParameters.empty.copy(name = "library".some)

    Post(apiUriV2(s"user_repo/search"), params).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params1 =
      PackageSearchParameters.empty.copy(name = "mytargetName".some)

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params2 =
      PackageSearchParameters.empty.copy(name = "doesnotexist".some)

    Post(apiUriV2(s"user_repo/search"), params2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }

    val params3 =
      PackageSearchParameters.empty.copy(version = "doesnotexist".some)

    Post(apiUriV2(s"user_repo/search"), params3).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }

    val params4 =
      PackageSearchParameters.empty.copy(version = "0.0.1".some)

    Post(apiUriV2(s"user_repo/search"), params4).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }
  }

  testWithRepo("sorts by filename") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/zotherpackage?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Post(
      apiUriV2(s"user_repo/search?sortBy=filename&sortDirection=asc")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val result = responseAs[PaginationResult[Package]]

      result.total shouldBe 2
      result.values.length shouldBe 2

      result.values.map(_.filename.value) shouldBe Seq("mypath/mytargetName", "zotherpackage")
    }

    Post(
      apiUriV2(s"user_repo/search?sortBy=filename&sortDirection=desc")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val result = responseAs[PaginationResult[Package]]

      result.total shouldBe 2
      result.values.length shouldBe 2

      result.values.map(_.filename.value) shouldBe Seq("zotherpackage", "mypath/mytargetName")
    }
  }

  testWithRepo("sorts by created_at DESC using custom createdAt") {
    implicit ns => implicit repoId =>
      addTargetToRepo(repoId)

      uploadOfflineSignedTargetsRole()

      val testTargets: Map[TargetFilename, ClientTargetItem] = Map(
        refineMV[ValidTargetFilename]("mypath/mytargetName") -> ClientTargetItem(
          Map(HashMethod.SHA256 -> Sha256Digest.digest("hi".getBytes).hash),
          2,
          Json
            .obj(
              "name" -> "mytargetName".asJson,
              "version" -> "0.0.2".asJson,
              "hardwareIds" -> List("delegated-hardware-id-001").asJson,
              "createdAt" -> Instant.now().plus(12, ChronoUnit.HOURS).asJson
            )
            .some
        )
      )

      val signedDelegationRole = buildSignedDelegatedTargets(targets = testTargets)

      pushSignedDelegatedMetadataOk(signedDelegationRole)

      Put(
        apiUri("user_repo/targets/zotherpackage?name=library&version=0.0.1&hardwareIds=myid001"),
        testEntity
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
      }

      Post(apiUriV2(s"user_repo/search?sortBy=createdAt")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK

      val result = responseAs[PaginationResult[Package]]

      result.total shouldBe 2
      result.values.length shouldBe 2

      result.values.map(_.filename.value) shouldBe Seq("mypath/mytargetName", "zotherpackage")
    }
  }

  testWithRepo("sorts by created_at DESC") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/zotherpackage?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Post(apiUriV2(s"user_repo/search?sortBy=createdAt")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val result = responseAs[PaginationResult[Package]]

      result.total shouldBe 2
      result.values.length shouldBe 2

      result.values.map(_.filename.value) shouldBe Seq("zotherpackage", "mypath/mytargetName")
    }
  }

  testWithRepo("filters by hash") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params =
      PackageSearchParameters.empty.copy(hashes =
        Seq(
          refineMV("352ce6b496cece167046d00d8a6431ffa43646b378cce4e3013d1d9aeef8dbb4"),
          refineMV("a1fb50e6c86fae1679ef3351296fd6713411a08cf8dd1790a4fd05fae8688161")
        )
      )

    Post(apiUriV2(s"user_repo/search"), params).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params1 =
      PackageSearchParameters.empty.copy(hashes =
        Seq(refineMV("0000000000000000000000000000000000000000000000000000000000000000"))
      )

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }
  }

  testWithRepo("filters by nameContains") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params = PackageSearchParameters.empty.copy(nameContains = "lib".some)

    Post(apiUriV2(s"user_repo/search"), params).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params1 = PackageSearchParameters.empty.copy(nameContains = "mytarget".some)

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params2 = PackageSearchParameters.empty.copy(nameContains = "doesnotexist".some)

    Post(apiUriV2(s"user_repo/search"), params2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }
  }

  testWithRepo("filters by hardwareIds") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params =
      PackageSearchParameters.empty.copy(hardwareIds = Seq(refineMV("myid001")))

    Post(apiUriV2(s"user_repo/search"), params).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params1 =
      PackageSearchParameters.empty.copy(hardwareIds =
        Seq(refineMV("delegated-hardware-id-001"))
      )

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params2 =
      PackageSearchParameters.empty.copy(hardwareIds = Seq(refineMV("somethingelse")))

    Post(apiUriV2(s"user_repo/search"), params2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }

    val params3 =
      PackageSearchParameters.empty.copy(hardwareIds =
        Seq(refineMV("delegated-hardware-id-001"), refineMV("myid001"))
      )

    Post(apiUriV2(s"user_repo/search")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 2
    }
  }

  testWithRepo("filters by filenames") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/library-0.0.1?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    val params =
      PackageSearchParameters.empty.copy(filenames = Seq(refineMV("library-0.0.1")))

    Post(
      apiUriV2(s"user_repo/search"),
      params
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params1 =
      PackageSearchParameters.empty.copy(filenames =
        Seq(refineMV("mypath/mytargetName"))
      )

    Post(apiUriV2(s"user_repo/search"), params1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 1
    }

    val params2 =
      PackageSearchParameters.empty.copy(filenames = Seq(refineMV("somethingelse")))

    Post(apiUriV2(s"user_repo/search"), params2).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values shouldBe empty
    }

    val params3 =
      PackageSearchParameters.empty.copy(filenames =
        Seq(refineMV("library-0.0.1"), refineMV("mypath/mytargetName"))
      )

    Post(apiUriV2(s"user_repo/search"), params3).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[Package]].values
      values should have size 2
    }
  }

  testWithRepo("grouped-search gets packages aggregated by version") {
    implicit ns => implicit repoId =>
      addTargetToRepo(repoId)

      // targets with no `custom` are ignored, no name/version
      Get(apiUriV2(s"user_repo/grouped-search")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[PaginationResult[AggregatedPackage]]

        response.total shouldBe 0
        response.values shouldBe empty
      }

      uploadOfflineSignedTargetsRole()

      val signedDelegationRole = buildSignedDelegatedTargets()

      pushSignedDelegatedMetadataOk(signedDelegationRole)

      Get(apiUriV2(s"user_repo/grouped-search")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[PaginationResult[AggregatedPackage]]

        response.total shouldBe 1
        val item = response.values.loneElement

        item.name.value shouldBe "mytargetName"
        item.versions.loneElement.value shouldBe "0.0.2"
        item.hardwareIds.loneElement.value shouldBe "delegated-hardware-id-001"
        item.origins.loneElement.value shouldBe "my-delegation.test.ok_"
      }

  }

  testWithRepo("filters grouped target items") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/mypkg?name=library&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUriV2(s"user_repo/grouped-search?hardwareIds=myid001")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?hardwareIds=delegated-hardware-id-001")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?hardwareIds=delegated-hardware-id-001,myid001")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 2
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?hardwareIds=somethingelse")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values shouldBe empty
    }

    Get(apiUriV2(s"user_repo/grouped-search?nameContains=lib")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?nameContains=doesnotexist")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values shouldBe empty
    }

    Get(apiUriV2(s"user_repo/grouped-search?origin=doesnotexist")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values shouldBe empty
    }

    Get(apiUriV2(s"user_repo/grouped-search?origin=targets.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(apiUriV2(s"user_repo/grouped-search?version=0.0.1")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(
      apiUriV2(
        s"user_repo/grouped-search?hashes=0000000000000000000000000000000000000000000000000000000000000000"
      )
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values shouldBe empty
    }

    Get(
      apiUriV2(
        s"user_repo/grouped-search?hashes=352ce6b496cece167046d00d8a6431ffa43646b378cce4e3013d1d9aeef8dbb4"
      )
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(apiUriV2(s"user_repo/grouped-search?filenames=mypkg")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?filenames=mypath/mytargetName")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 1
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?filenames=mypath/mytargetName,mypkg")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 2
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?filenames=somethingelse")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values shouldBe empty
    }

  }

  testWithRepo("sorts grouped target items") { implicit ns => implicit repoId =>
    addTargetToRepo(repoId)

    uploadOfflineSignedTargetsRole()

    val signedDelegationRole = buildSignedDelegatedTargets()

    pushSignedDelegatedMetadataOk(signedDelegationRole)

    Put(
      apiUri("user_repo/targets/zotherpackage?name=zlibrary&version=0.0.1&hardwareIds=myid001"),
      testEntity
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(
      apiUriV2(s"user_repo/grouped-search?sortBy=name&sortDirection=asc")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 2

      values.map(_.name.value) shouldBe List("mytargetName", "zlibrary")
    }

    Get(apiUriV2(s"user_repo/grouped-search?sortBy=lastVersionAt")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val values = responseAs[PaginationResult[AggregatedPackage]].values
      values should have size 2

      values.map(_.name.value) shouldBe List("zlibrary", "mytargetName")
    }
  }

  testWithRepo("GET hardwareids-packages returns hwids for which there are packages") {
    implicit ns => implicit repoId =>
      addTargetToRepo(repoId)

      Put(
        apiUri(
          "user_repo/targets/mypkg_file?name=library&version=0.0.1&hardwareIds=myid001,myid002"
        ),
        testEntity
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
      }

      Put(
        apiUri(
          "user_repo/targets/mypkg_file2?name=library&version=0.0.2&hardwareIds=myid002,myid003"
        ),
        testEntity2
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
      }

      Get(apiUriV2(s"user_repo/hardwareids-packages")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[PaginationResult[HardwareIdentifier]]
        result.total shouldBe 3L
        result.offset shouldBe 0L.toOffset
        result.limit shouldBe 3L.toLimit
        result.values.map(_.value) should contain theSameElementsAs List(
          "myid001",
          "myid002",
          "myid003"
        )
      }
  }

}
