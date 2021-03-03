package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.model.{StatusCodes, Uri}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, RsaKeyType, ValidTargetFilename}
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import com.advancedtelematic.libtuf_server.data.Requests.{CommentRequest, FilenameComment, TargetComment}
import com.advancedtelematic.libtuf_server.repo.client.ReposerverClient.RequestTargetItem
import com.advancedtelematic.tuf.reposerver.util.{RepoResourceSpecUtil, ResourceSpec, TufReposerverSpec}
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import cats.syntax.show._
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.advancedtelematic.libats.data.RefinedUtils._

class RepoResourceCommentSpec extends TufReposerverSpec with ResourceSpec with PatienceConfiguration with RepoResourceSpecUtil
{

  override implicit def patienceConfig: PatienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  test("set comment for existing repo id and package") {
    val repoId = addTargetToRepo()

    Put(apiUri(s"repo/${repoId.show}/comments/myfile01"), CommentRequest(TargetComment("comment"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("set comment for existing repo id and non-existing package") {
    val repoId = RepoId.generate()
    fakeKeyserverClient.createRoot(repoId).futureValue

    Put(apiUri(s"repo/${repoId.show}/comments/myfile01"), CommentRequest(TargetComment("comment"))) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("set comment for non-existing repo id and non-existing package") {
    val repoId = RepoId.generate()

    Put(apiUri(s"repo/${repoId.show}/comments/myfile01"), CommentRequest(TargetComment("comment"))) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("get existing comment") {
    val repoId = addTargetToRepo()

    Put(apiUri(s"repo/${repoId.show}/comments/myfile01"), CommentRequest(TargetComment("ಠ_ಠ"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/comments/myfile01")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("ಠ_ಠ"))
    }
  }

  test("getting comment of deleted package fails") {
    val repoId = addTargetToRepo()

    Put(apiUri(s"repo/${repoId.show}/comments/myfile01"), CommentRequest(TargetComment("ಠ_ಠ"))) ~> routes ~> check {
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
      responseAs[Seq[FilenameComment]] shouldBe Nil
    }
  }

  test("trying to get comment list for non-existing repo id") {
    val repoId = RepoId.generate()

    Get(apiUri(s"repo/${repoId.show}/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Seq[FilenameComment]] shouldBe Nil
    }
  }

  test("get existing comment list") {
    val repoId = addTargetToRepo()

    Put(apiUri(s"repo/${repoId.show}/comments/myfile01"), CommentRequest(TargetComment("comment"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/${repoId.show}/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Seq[FilenameComment]] shouldBe List(FilenameComment("myfile01".refineTry[ValidTargetFilename].get,
        TargetComment("comment")))
    }
  }

  test("get existing comment list for different versions") {
    val repoId = RepoId.generate()
    fakeKeyserverClient.createRoot(repoId, RsaKeyType).futureValue
    val repoIdS = repoId.show

    Post(apiUri(s"repo/$repoIdS/targets/raspberrypi_rocko-ce15f3986223be401205d13dda6e8d7aefeae1c02a769043ba11d1268ccd77dd"), testFile) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(apiUri(s"repo/$repoIdS/comments/raspberrypi_rocko-ce15f3986223be401205d13dda6e8d7aefeae1c02a769043ba11d1268ccd77dd"),
                        CommentRequest(TargetComment("comment1"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val testFile2 = {
      val checksum = Sha256Digest.digest("lo".getBytes)
      RequestTargetItem(Uri("https://ats.com/testfile"), checksum, targetFormat = None, name = None, version = None,
                            hardwareIds = Seq.empty, length = "lo".getBytes.length)
    }

    Post(apiUri(s"repo/$repoIdS/targets/raspberrypi_rocko-d359911e6fb67476e379a55870d1a180acc3a78d6d463b5281ccd9ca861519dc"), testFile2) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Put(apiUri(s"repo/$repoIdS/comments/raspberrypi_rocko-d359911e6fb67476e379a55870d1a180acc3a78d6d463b5281ccd9ca861519dc"),
                          CommentRequest(TargetComment("comment2"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/$repoIdS/comments")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Seq[FilenameComment]].length shouldBe 2
    }
  }

  keyTypeTest("updating targets.json doesn't kill comments") { keyType =>
    val repoId = addTargetToRepo(keyType = keyType)
    val repoIdS = repoId.show

    val signedPayload = buildSignedTargetsRole(repoId, offlineTargets, version = 2)

    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload).withHeaders(makeRoleChecksumHeader(repoId)) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
      header("x-ats-role-checksum").map(_.value) should contain(makeRoleChecksumHeader(repoId).value)
    }

    Put(apiUri(s"repo/$repoIdS/comments/$offlineTargetFilename"),
      CommentRequest(TargetComment("comment1"))) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"repo/$repoIdS/comments/$offlineTargetFilename")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("comment1"))
    }

    val signedPayload2 = buildSignedTargetsRole(repoId, offlineTargets, version = 3)

    Put(apiUri(s"repo/${repoId.show}/targets"), signedPayload2).withHeaders(makeRoleChecksumHeader(repoId)) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
      header("x-ats-role-checksum").map(_.value) should contain(makeRoleChecksumHeader(repoId).value)
    }

    Get(apiUri(s"repo/$repoIdS/comments/$offlineTargetFilename")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      entityAs[CommentRequest] shouldBe CommentRequest(TargetComment("comment1"))
    }

  }

}
