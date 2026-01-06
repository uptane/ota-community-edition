package com.advancedtelematic.tuf.reposerver.util

import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes}
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libtuf.data.TufDataType.RepoId
import com.advancedtelematic.tuf.reposerver.util.NamespaceSpecOps.{
  withRandomNamepace,
  NamespaceTag,
  Namespaced
}
import io.circe.Json
import org.scalactic.source.Position
import org.scalatest.Tag

import java.util.UUID
import scala.util.Random

object NamespaceSpecOps {

  trait NamespaceTag {
    val value: String
  }

  def withNamespace[T](ns: String)(fn: NamespaceTag => T): T =
    fn.apply(new NamespaceTag {
      override val value = ns
    })

  def withRandomNamepace[T](fn: NamespaceTag => T) = withNamespace(genName)(fn)

  implicit class Namespaced(value: HttpRequest) {

    def namespaced(implicit namespaceTag: NamespaceTag): HttpRequest =
      value.addHeader(RawHeader("x-ats-namespace", namespaceTag.value))

  }

  def genName = Random.alphanumeric.take(10).mkString

  def genNs = Namespace(genName)
}

trait RepositoryTestOps {
  self: ResourceSpec =>

  def testWithRepo(testName: String, testArgs: Tag*)(testFun: NamespaceTag => RepoId => Any)(
    implicit pos: Position): Unit = {
    val testFn = (ns: NamespaceTag) => {

      val uuid = Post(apiUri(s"user_repo")).namespaced(ns) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
        responseAs[UUID]
      }

      testFun(ns)(RepoId(uuid))
    }

    test(testName, testArgs*)(withRandomNamepace(testFn))(pos = pos)
  }

}
