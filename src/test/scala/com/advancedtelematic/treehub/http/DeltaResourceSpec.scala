package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.model.headers.{Location, RawHeader}
import com.advancedtelematic.data.DataType.{CommitTupleOps, DeltaIndexId, StaticDeltaMeta, SuperBlockHash, ValidDeltaId, ValidDeltaIndexId}
import com.advancedtelematic.treehub.db.{ObjectRepositorySupport, StaticDeltaMetaRepositorySupport}
import com.advancedtelematic.util.{ResourceSpec, TreeHubSpec}
import com.advancedtelematic.libats.data.RefinedUtils.RefineTry
import com.advancedtelematic.util.FakeUsageUpdate.CurrentBandwith
import akka.pattern.ask

import scala.concurrent.duration.*
import com.advancedtelematic.common.DigestCalculator
import com.advancedtelematic.data.ClientDataType.{CommitInfo, CommitInfoRequest, CommitSize, StaticDelta}
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libats.messaging_datatype.DataType.Commit
import eu.timepit.refined.api.{RefType, Refined}
import io.circe.KeyDecoder
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterEach

import scala.util.Random
import slick.jdbc.MySQLProfile.api.*

class DeltaResourceSpec extends TreeHubSpec with ResourceSpec with ObjectRepositorySupport with StaticDeltaMetaRepositorySupport with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    super.afterEach()

    db.run(sqlu"delete from `static_deltas`").futureValue
  }

  implicit class SuperblockHashHeaderExt(value: HttpRequest) {
    def withSuperblockHash()(implicit hash: SuperBlockHash): HttpRequest = {
      value ~> RawHeader("x-trx-superblock-hash", hash.value)
    }
  }

  implicit val commitKeyDecoder: KeyDecoder[Commit] = KeyDecoder[Commit] { str => RefType.applyRef[Commit](str).toOption }

  test("GET summary returns 404 Not Found if summary is not in the delta store") {
    Get(apiUri(s"summary")) ~> RawHeader("x-ats-namespace", "notfound") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("GET on delta superblock returns superblock") {
    val deltaId = "FWnaj5O7BH+K7XnIPyhQFYj9m_0jbRnw7fetZJtfmXE-TLFhE9DIGjBpFS7UbPLBpAPyfTF_nHbmNImcP63xoDY".refineTry[ValidDeltaId].get
    val blob = "superblock data".getBytes()

    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe blob
    }
  }

  test("GET on deltas using from/to redirects to url with deltaId") {
    val from = RefType.applyRef[Commit]("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824").toOption.get
    val to = RefType.applyRef[Commit]("82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9").toOption.get
    val deltaId = (from, to).toDeltaId

    Get(apiUri(s"deltas?from=${from.value}&to=${to.value}")) ~> routes ~> check {
      status shouldBe StatusCodes.Found
      header[Location].map(_.uri.toString()) should contain(s"/deltas/${deltaId.asPrefixedPath}")
    }
  }

  test("GET on deltas returns a PaginationResult with a list of deltas") {
    val deltaId = "_9zK5AbC3B1x0h3yVI8TFWkMFnLdiLwq+46iDf8YFF0-EvMVW0uxJySf4Wwgv6r32Qq4XEJPNxjYCgx0smxHtoo".refineTry[ValidDeltaId].get
    val blob = "superblock data".getBytes()

    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val expectedDelta = StaticDelta(deltaId.fromCommit, deltaId.toCommit, 15)

    Get(apiUri("deltas")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val deltas = parse(responseAs[String])
        .getOrElse(null)
        .as[PaginationResult[StaticDelta]]
        .toOption.get
      deltas.values should have size 1
      deltas.values shouldBe Seq(expectedDelta)

    }
  }

  test("GET on deltas can list deltas with custom pagination limit and offset") {
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    val deltaId1 = "w+Y9B_ekii24eq3Q82ZvxphDfXxr8VzDmqPziDg5d7Q-cJUJMFVGmog4PkN9VT7hq3ufD9IAfb0oYgzqNpsr2n0".refineTry[ValidDeltaId].get
    val blob1 = "superblock data 1".getBytes()

    Post(apiUri(s"deltas/${deltaId1.asPrefixedPath}/superblock"), blob1).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val deltaId2 = "_QTXv6POPMrlct21tZY0HErJFPDIbspxrOmq+OGjldY-O_yKIJQRSxQWbqKZKHUQ0PlRcbCMfT9PUP1vHGg+Qj0".refineTry[ValidDeltaId].get
    val blob2 = "superblock data 2".getBytes()

    Post(apiUri(s"deltas/${deltaId2.asPrefixedPath}/superblock"), blob2).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val expectedDelta1 = StaticDelta(deltaId1.fromCommit, deltaId1.toCommit, 17)
    val expectedDelta2 = StaticDelta(deltaId2.fromCommit, deltaId2.toCommit, 17)

    Get(apiUri("deltas")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val deltas = parse(responseAs[String])
        .getOrElse(null)
        .as[PaginationResult[StaticDelta]]
        .toOption.get
      deltas.values should have size 2
      deltas.values shouldBe Seq(expectedDelta2, expectedDelta1)

    }

    Get(apiUri("deltas?limit=1")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val deltas = parse(responseAs[String])
        .getOrElse(null)
        .as[PaginationResult[StaticDelta]]
        .toOption.get
      deltas.values should have size 1
      deltas.values.head shouldBe expectedDelta2

    }

    Get(apiUri("deltas?limit=1&offset=1")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val deltas = parse(responseAs[String])
        .getOrElse(null)
        .as[PaginationResult[StaticDelta]]
        .toOption.get
      deltas.values should have size 1
      deltas.values.head shouldBe expectedDelta1

    }
  }

  test("GET on deltas with negative limit and offset returns 400 Bad Request") {
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    val deltaId = "w+Y9B_ekii24eq3Q82ZvxphDfXxr8VzDmqPziDg5d7Q-cJUJMFVGmog4PkN9VT7hq3ufD9IAfb0oYgzqNpsr2n0".refineTry[ValidDeltaId].get
    val blob = "superblock data".getBytes()

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("deltas?limit=-1&offset=-1")) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST on deltas with commits returns a correct map of commit infos") {
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    val commit1 = RefType.applyRef[Commit]("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef").toOption.get
    val commit2 = RefType.applyRef[Commit]("234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1").toOption.get
    val deltaId1 = (commit1, commit2).toDeltaId

    val blob1 = "superblock data 1".getBytes()

    Post(apiUri(s"deltas/${deltaId1.asPrefixedPath}/superblock"), blob1).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val commit3 = RefType.applyRef[Commit]("34567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12").toOption.get
    val deltaId2 = (commit3, commit2).toDeltaId

    val blob2 = "superblock data 2".getBytes()

    Post(apiUri(s"deltas/${deltaId2.asPrefixedPath}/superblock"), blob2).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val commit4 = RefType.applyRef[Commit]("4567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef123").toOption.get
    val deltaId3 = (commit1, commit4).toDeltaId

    val blob3 = "superblock data 3".getBytes()
    Post(apiUri(s"deltas/${deltaId3.asPrefixedPath}/superblock"), blob3).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val deltaId4 = (commit2, commit1).toDeltaId

    val blob4 = "superblock data 4".getBytes()
    Post(apiUri(s"deltas/${deltaId4.asPrefixedPath}/superblock"), blob4).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val expectedResponse = Map[Commit, CommitInfo](
      (commit1, CommitInfo(Seq(CommitSize(commit2, 17)), Seq(CommitSize(commit2, 17), CommitSize(commit4, 17)))),
      (commit2, CommitInfo(Seq(CommitSize(commit1, 17), CommitSize(commit3, 17)), Seq(CommitSize(commit1, 17)))),
      (commit3, CommitInfo(Seq(), Seq(CommitSize(commit2, 17)))),
      (commit4, CommitInfo(Seq(CommitSize(commit1, 17)), Seq()))
    )

    val request = CommitInfoRequest(Seq(commit1, commit2, commit3, commit4))

    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

    Post(apiUri("deltas"), request) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Map[Commit, CommitInfo]]

      response shouldBe expectedResponse
    }
  }

  test("POST on deltas with commits not in the database returns empty map") {
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    val commit1 = RefType.applyRef[Commit]("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef").toOption.get
    val commit2 = RefType.applyRef[Commit]("234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1").toOption.get
    val deltaId1 = (commit1, commit2).toDeltaId

    val blob1 = "superblock data 1".getBytes()

    Post(apiUri(s"deltas/${deltaId1.asPrefixedPath}/superblock"), blob1).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val commit3 = RefType.applyRef[Commit]("34567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12").toOption.get

    val request = CommitInfoRequest(Seq(commit3))

    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

    Post(apiUri("deltas"), request) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Map[Commit, CommitInfo]]

      response.isEmpty shouldBe true
    }
  }

  test("DELETE on deltas makes the static delta unavailable") {
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    val commit1 = RefType.applyRef[Commit]("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef").toOption.get
    val commit2 = RefType.applyRef[Commit]("234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1").toOption.get
    val deltaId1 = (commit1, commit2).toDeltaId

    val blob1 = "superblock data 1".getBytes()

    Post(apiUri(s"deltas/${deltaId1.asPrefixedPath}/superblock"), blob1).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId1.asPrefixedPath}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Delete(apiUri(s"deltas/${deltaId1.asPrefixedPath}")) ~> routes ~> check {
      status shouldBe StatusCodes.Accepted
    }

    Get(apiUri(s"deltas/${deltaId1.asPrefixedPath}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("publishes usage to bus") {
    val deltaId = "6qdddbmoaLtYLmZg2Q8OZm7syoxvAOFs0fAXbavojKY-k_F8QpdRP9KlPD6wiS2HNF7WuL1sgfu1tLaJXV6GjIU".refineTry[ValidDeltaId].get
    val blob = "some other data".getBytes

    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentBandwith(deltaId.toCommitObjectId))(1.second).mapTo[Long].futureValue
    usage should be >= blob.length.toLong
  }

  test("can store objects") {
    val deltaId = "1c65CmlTrNj0G8VKwvrHcZ7cdi6nhROSD4v1XJHuQs8-TLFhE9DIGjBpFS7UbPLBpAPyfTF_nHbmNImcP63xoDY".refineTry[ValidDeltaId].get
    val to = deltaId.toCommit
    val blob = "some static delta data".getBytes

    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/0"), blob).withSuperblockHash()(superBlockHash) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/0")).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.RetryWith
    }

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/1"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash()(superBlockHash) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/1")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val size1 = staticDeltaMetaRepository.findByTo(defaultNs, to, StaticDeltaMeta.Status.Available).futureValue.headOption.map(_.size)
    size1 should contain(blob.size * 3)
  }

  test("can store superblock") {
    val deltaId = "SyJ3d9TdH8Ycb4hPSGQdArTRIdP9Moywi1Ux_Kzav4o-TLFhE9DIGjBpFS7UbPLBpAPyfTF_nHbmNImcP63xoDY".refineTry[ValidDeltaId].get
    val blob = "some other data".getBytes
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("storing superblock makes the static delta available") {
    val deltaId = "JHCp7ENNjvWRcHDmWVi9IeD5NRoqrcdM6swXSC2o7TQ-TLFhE9DIGjBpFS7UbPLBpAPyfTF_nHbmNImcP63xoDY".refineTry[ValidDeltaId].get
    val blob = "some data".getBytes
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash](DigestCalculator.digest()(new Random().nextString(10))).toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/0"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/0")) ~> routes ~> check {
      status shouldBe StatusCodes.RetryWith
    }

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"deltas/${deltaId.asPrefixedPath}/0")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("retrieves index in a gvariant containing relevant static deltas and superblocks") {
    val deltaId = "472PluJ+96MzzZM4nTfIzMIqCLaV7XOWikSgajJubqM-419vppKTWOqy0f9_br2d8SOrj3tALC7vig+fytavOPg".refineTry[ValidDeltaId].get
    val blob = "some data".getBytes
    implicit val superBlockHash = RefType.applyRef[SuperBlockHash]("6cb4598306785bdaac4186d4063d04b54a27d4d6ed463520d0a7df2f932887ee").toOption.get

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/0"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val deltaIndex = RefType.applyRef[DeltaIndexId]("419vppKTWOqy0f9_br2d8SOrj3tALC7vig+fytavOPg.index").toOption.get

    Get(apiUri(s"delta-indexes/${deltaIndex.asPrefixedPath}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }

    Post(apiUri(s"deltas/${deltaId.asPrefixedPath}/superblock"), blob).withSuperblockHash() ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"delta-indexes/${deltaIndex.asPrefixedPath}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }
}
