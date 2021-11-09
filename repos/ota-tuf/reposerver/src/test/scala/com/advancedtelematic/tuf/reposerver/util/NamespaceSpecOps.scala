package com.advancedtelematic.tuf.reposerver.util

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.advancedtelematic.libats.data.DataType.Namespace

import scala.util.Random

object NamespaceSpecOps {
  trait NamespaceTag {
    val value: String
  }

  def withNamespace[T](ns: String)(fn: NamespaceTag => T): T = {
    fn.apply(new NamespaceTag {
      override val value = ns
    })
  }

  def withRandomNamepace[T](fn: NamespaceTag => T) = withNamespace(genName)(fn)

  implicit class Namespaced(value: HttpRequest) {
    def namespaced(implicit namespaceTag: NamespaceTag): HttpRequest =
      value.addHeader(RawHeader("x-ats-namespace", namespaceTag.value))
  }

  def genName = Random.alphanumeric.take(10).mkString

  def genNs = Namespace(genName)
}
