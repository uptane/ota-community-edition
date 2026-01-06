package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.model.Multipart.FormData.BodyPart
import org.apache.pekko.http.scaladsl.model.{HttpEntity, Multipart, StatusCodes, Uri}
import org.apache.pekko.util.ByteString
import com.advancedtelematic.libtuf.data.TufDataType.{
  RepoId,
  RsaKeyType,
  SignedPayload,
  ValidTargetFilename
}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.data.Requests.{
  CommentRequest,
  FilenameComment,
  TargetComment
}
import com.advancedtelematic.libtuf_server.repo.client.ReposerverClient.RequestTargetItem
import com.advancedtelematic.tuf.reposerver.util.{
  RepoResourceSpecUtil,
  ResourceSpec,
  TufReposerverSpec
}
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import cats.syntax.show._
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libtuf.data.ClientDataType.TargetsRole
import com.advancedtelematic.tuf.reposerver.util.NamespaceSpecOps.{
  withRandomNamepace,
  NamespaceTag,
  Namespaced
}
import eu.timepit.refined.api.Refined
import PaginationResult.*

class RepoResourceCommentSpec
    extends TufReposerverSpec
    with ResourceSpec
    with PatienceConfiguration
    with RepoResourceSpecUtil {

  val testEntity = HttpEntity(ByteString("""
      |Like all the men of the Library, in my younger days I traveled;
      |I have journeyed in quest of a book, perhaps the catalog of catalogs.
      |""".stripMargin))

  val fileBodyPart = BodyPart("file", testEntity, Map("filename" -> "babel.txt"))

  val form = Multipart.FormData(fileBodyPart)

  def createRepo()(implicit ns: NamespaceTag): Unit =
    Post(apiUri(s"user_repo")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig().copy(timeout = Span(5, Seconds))

  test("set comment for existing repo id and package") {
    val repoId = addTargetToRepo()

    Put(
      apiUri(s"repo/${repoId.show}/comments/myfile01"),
      CommentRequest(TargetComment("comment"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("set comment for existing repo id and non-existing package") {
    val repoId = RepoId.generate()
    fakeKeyserverClient.createRoot(repoId).futureValue

    Put(
      apiUri(s"repo/${repoId.show}/comments/myfile01"),
      CommentRequest(TargetComment("comment"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("set comment for non-existing repo id and non-existing package") {
    val repoId = RepoId.generate()

    Put(
      apiUri(s"repo/${repoId.show}/comments/myfile01"),
      CommentRequest(TargetComment("comment"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("get existing comment") {
    val repoId = addTargetToRepo()

    Put(
      apiUri(s"repo/${repoId.show}/comments/myfile01"),
      CommentRequest(TargetComment("ಠ_ಠ"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/comments/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("ಠ_ಠ"))
    }
  }

  test("getting comment of deleted package fails") {
    val repoId = addTargetToRepo()

    Put(
      apiUri(s"repo/${repoId.show}/comments/myfile01"),
      CommentRequest(TargetComment("ಠ_ಠ"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/comments/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("ಠ_ಠ"))
    }

    Delete(apiUri(s"repo/${repoId.show}/targets/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"repo/${repoId.show}/comments/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("trying to get missing comment for existing repo id and package returns 404") {
    val repoId = addTargetToRepo()

    Get(apiUri(s"repo/${repoId.show}/comments/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("trying to get non-existing comment list for existing repo id") {
    val repoId = addTargetToRepo()

    Get(apiUri(s"repo/${repoId.show}/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[PaginationResult[FilenameComment]].values shouldBe empty
      responseAs[PaginationResult[FilenameComment]].total shouldBe 0
    }
  }

  test("trying to get comment list for non-existing repo id") {
    val repoId = RepoId.generate()

    Get(apiUri(s"repo/${repoId.show}/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[PaginationResult[FilenameComment]].values shouldBe empty
      responseAs[PaginationResult[FilenameComment]].total shouldBe 0
    }
  }

  test("get existing comment list") {
    val repoId = addTargetToRepo()

    Put(
      apiUri(s"repo/${repoId.show}/comments/myfile01"),
      CommentRequest(TargetComment("comment"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[PaginationResult[FilenameComment]].total shouldBe 1
      entityAs[PaginationResult[FilenameComment]].values shouldBe Seq(
        FilenameComment("myfile01".refineTry[ValidTargetFilename].get, TargetComment("comment"))
      )
    }
  }

  test("get existing comment list for different versions") {
    val repoId = RepoId.generate()
    fakeKeyserverClient.createRoot(repoId, RsaKeyType).futureValue
    val repoIdS = repoId.show

    Post(
      apiUri(
        s"repo/$repoIdS/targets/raspberrypi_rocko-ce15f3986223be401205d13dda6e8d7aefeae1c02a769043ba11d1268ccd77dd"
      ),
      testFile
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(
      apiUri(
        s"repo/$repoIdS/comments/raspberrypi_rocko-ce15f3986223be401205d13dda6e8d7aefeae1c02a769043ba11d1268ccd77dd"
      ),
      CommentRequest(TargetComment("comment1"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val testFile2 = {
      val checksum = Sha256Digest.digest("lo".getBytes)
      RequestTargetItem(
        Uri("https://ats.com/testfile"),
        checksum,
        targetFormat = None,
        name = None,
        version = None,
        hardwareIds = Seq.empty,
        length = "lo".getBytes.length
      )
    }

    Post(
      apiUri(
        s"repo/$repoIdS/targets/raspberrypi_rocko-d359911e6fb67476e379a55870d1a180acc3a78d6d463b5281ccd9ca861519dc"
      ),
      testFile2
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(
      apiUri(
        s"repo/$repoIdS/comments/raspberrypi_rocko-d359911e6fb67476e379a55870d1a180acc3a78d6d463b5281ccd9ca861519dc"
      ),
      CommentRequest(TargetComment("comment2"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/$repoIdS/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[PaginationResult[FilenameComment]].values.length shouldBe 2
      entityAs[PaginationResult[FilenameComment]].total shouldBe 2
      entityAs[PaginationResult[FilenameComment]].limit shouldBe 50.toLimit
    }
  }

  keyTypeTest("updating targets.json doesn't kill comments") { keyType =>
    val repoId = addTargetToRepo(keyType = keyType)
    val repoIdS = repoId.show

    val signedPayload = buildSignedTargetsRole(repoId, offlineTargets, version = 2)

    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload)
      .withHeaders(makeRoleChecksumHeader(repoId)) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
      header("x-ats-role-checksum").map(_.value) should contain(
        makeRoleChecksumHeader(repoId).value
      )
    }

    Put(
      apiUri(s"repo/$repoIdS/comments/$offlineTargetFilename"),
      CommentRequest(TargetComment("comment1"))
    ) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/$repoIdS/comments/$offlineTargetFilename")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("comment1"))
    }

    val signedPayload2 = buildSignedTargetsRole(repoId, offlineTargets, version = 3)

    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload2)
      .withHeaders(makeRoleChecksumHeader(repoId)) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
      header("x-ats-role-checksum").map(_.value) should contain(
        makeRoleChecksumHeader(repoId).value
      )
    }

    Get(apiUri(s"repo/$repoIdS/comments/$offlineTargetFilename")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("comment1"))
    }

  }

  test("can update the comments on a package") {
    withRandomNamepace { implicit ns =>
      createRepo()
      // Create package
      Put(
        apiUri("user_repo/targets/cheerios-0.0.5?name=cheerios&version=0.0.5"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      // update comments
      val testComment = "this is a sweet comment"
      Put(
        apiUri("user_repo/comments/cheerios-0.0.5"),
        CommentRequest(TargetComment(testComment))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      // verify
      Get(apiUri("user_repo/comments/cheerios-0.0.5")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[CommentRequest] shouldEqual CommentRequest(TargetComment(testComment))
      }
    }
  }

  test("can fetch all comments for all packages") {
    withRandomNamepace { implicit ns =>
      createRepo()
      // Create packages
      Put(
        apiUri("user_repo/targets/cheerios-0.0.5?name=cheerios&version=0.0.5"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/cheerios-0.0.6?name=cheerios&version=0.0.6"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      // update comments
      val testComment = "this is a sweet comment"
      val testComment2 = "this is just an ok comment"
      Put(
        apiUri("user_repo/comments/cheerios-0.0.5"),
        CommentRequest(TargetComment(testComment))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/cheerios-0.0.6"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Get(apiUri("user_repo/comments")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[PaginationResult[FilenameComment]].total shouldBe 2
        val comments = responseAs[PaginationResult[FilenameComment]].values
        comments should contain(
          FilenameComment(Refined.unsafeApply("cheerios-0.0.5"), TargetComment(testComment))
        )
        comments should contain(
          FilenameComment(Refined.unsafeApply("cheerios-0.0.6"), TargetComment(testComment2))
        )
      }
    }
  }

  test("can search comments for targetnames matching query pattern") {
    withRandomNamepace { implicit ns =>
      createRepo()
      // Create packages
      Put(
        apiUri("user_repo/targets/cheerios-0.0.5?name=cheerios&version=0.0.5"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/cheerios-0.0.6?name=cheerios&version=0.0.6"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/riceKrispies-0.0.1?name=riceKrispies&version=0.0.1"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      // update comments
      val testComment = "this is a sweet comment"
      val testComment2 = "this is just an ok comment"
      Put(
        apiUri("user_repo/comments/cheerios-0.0.5"),
        CommentRequest(TargetComment(testComment))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/cheerios-0.0.6"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/riceKrispies-0.0.1"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      // Fetch with search query
      Get(apiUri("user_repo/comments?nameContains=cheer")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val comments = responseAs[PaginationResult[FilenameComment]].values
        comments should contain(
          FilenameComment(Refined.unsafeApply("cheerios-0.0.5"), TargetComment(testComment))
        )
        comments should contain(
          FilenameComment(Refined.unsafeApply("cheerios-0.0.6"), TargetComment(testComment2))
        )
        comments should not contain (FilenameComment(
          Refined.unsafeApply("riceKrispies-0.0.1"),
          TargetComment(testComment2)
        ))
      }
    }
  }

  test("can fetch comments with pagination query params") {
    withRandomNamepace { implicit ns =>
      createRepo()
      // Create packages
      Put(
        apiUri("user_repo/targets/cheerios-0.0.5?name=cheerios&version=0.0.5"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/cheerios-0.0.6?name=cheerios&version=0.0.6"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/riceKrispies-0.0.1?name=riceKrispies&version=0.0.1"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      // update comments
      val testComment = "this is a sweet comment"
      val testComment2 = "this is just an ok comment"
      Put(
        apiUri("user_repo/comments/cheerios-0.0.5"),
        CommentRequest(TargetComment(testComment))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/cheerios-0.0.6"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/riceKrispies-0.0.1"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      // Fetch with search query
      Get(apiUri("user_repo/comments?offset=2&limit=2")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[PaginationResult[FilenameComment]].offset shouldBe 2.toOffset
        responseAs[PaginationResult[FilenameComment]].limit shouldBe 2.toLimit
        responseAs[PaginationResult[FilenameComment]].total shouldBe 3L
        responseAs[PaginationResult[FilenameComment]].values.length shouldBe 1

        val comments = responseAs[PaginationResult[FilenameComment]].values
        comments should not contain (FilenameComment(
          Refined.unsafeApply("cheerios-0.0.5"),
          TargetComment(testComment)
        ))
        comments should not contain (FilenameComment(
          Refined.unsafeApply("cheerios-0.0.6"),
          TargetComment(testComment2)
        ))
        comments should contain(
          FilenameComment(Refined.unsafeApply("riceKrispies-0.0.1"), TargetComment(testComment2))
        )
      }
    }
  }

  test("can fetch comments by specifying filenames") {
    withRandomNamepace { implicit ns =>
      createRepo()
      // Create packages
      Put(
        apiUri("user_repo/targets/cheerios-0.0.5?name=cheerios&version=0.0.5"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/cheerios-0.0.6?name=cheerios&version=0.0.6"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      Put(
        apiUri("user_repo/targets/riceKrispies-0.0.1?name=riceKrispies&version=0.0.1"),
        form
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[TargetsRole]]
      }
      // update comments
      val testComment = "this is a sweet comment"
      val testComment2 = "this is just an ok comment"
      Put(
        apiUri("user_repo/comments/cheerios-0.0.5"),
        CommentRequest(TargetComment(testComment))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/cheerios-0.0.6"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Put(
        apiUri("user_repo/comments/riceKrispies-0.0.1"),
        CommentRequest(TargetComment(testComment2))
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
      // Fetch with search query
      Post(
        apiUri("user_repo/list-target-comments"),
        Seq[String]("cheerios-0.0.5", "riceKrispies-0.0.1")
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val comments = responseAs[Seq[FilenameComment]]
        println(comments)
        comments should contain(
          FilenameComment(Refined.unsafeApply("cheerios-0.0.5"), TargetComment(testComment))
        )
        comments should not contain (FilenameComment(
          Refined.unsafeApply("cheerios-0.0.6"),
          TargetComment(testComment2)
        ))
        comments should contain(
          FilenameComment(Refined.unsafeApply("riceKrispies-0.0.1"), TargetComment(testComment2))
        )
      }
      Post(
        apiUri("user_repo/list-target-comments"),
        Seq[String]("cheerios-0.0.6")
      ).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val comments = responseAs[Seq[FilenameComment]]
        println(comments)
        comments should not contain (FilenameComment(
          Refined.unsafeApply("cheerios-0.0.5"),
          TargetComment(testComment)
        ))
        comments should contain(
          FilenameComment(Refined.unsafeApply("cheerios-0.0.6"), TargetComment(testComment2))
        )
        comments should not contain (FilenameComment(
          Refined.unsafeApply("riceKrispies-0.0.1"),
          TargetComment(testComment2)
        ))
      }
    }
  }

}
