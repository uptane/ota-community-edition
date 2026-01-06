package com.advancedtelematic.libats.debug

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.JavaUUID
import akka.http.scaladsl.server.{PathMatcher1, Route}
import cats.Show
import cats.syntax.show.*
import com.advancedtelematic.libats.data.DataType.Namespace
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.{Codec, Json}

import scala.concurrent.Future

object DebugDatatype {
  case class IndexItem(name: String, id: String, href: String, paramName: Option[String])

  case class Index(title: String, links: List[IndexItem])

  type Row = List[Json]

  case class Table(name: String, columns: Vector[String], data: Vector[Row])

  case class ResourceGroup[T](pathPrefix: String,
                              pathKey: String,
                              resources: List[TableResource[T]])

  case class TableResource[T](pathPrefix: String, fetch: T => Future[Table])

  implicit val indexItemCodec: Codec[IndexItem] = io.circe.generic.semiauto.deriveCodec[IndexItem]

  implicit val indexCodec: Codec[Index] = io.circe.generic.semiauto.deriveCodec[Index]

  implicit val tableCodec: Codec[Table] = io.circe.generic.semiauto.deriveCodec[Table]

  val NamespacePath: PathMatcher1[Namespace] =
    JavaUUID.flatMap(uuid => Option(Namespace(uuid.toString)))

  implicit val showNamespace: Show[Namespace] = Show(ns => ns.get)
}

object DebugRoutes {

  import DebugDatatype.*
  import akka.http.scaladsl.server.Directives.*

  def buildNavigation(resourceGroup: ResourceGroup[?]*): Route =
    path("navigation.json") {
      val items = resourceGroup.map { d =>
        IndexItem(
          toHumanReadable(d.pathPrefix),
          d.pathPrefix,
          s"/debug/${d.pathPrefix}/",
          Some(d.pathKey)
        )
      }

      complete(Index("debug", items.toList))
    }

  def routes(resourceRoutes: Route): Route = pathPrefix("debug") {
    concat(
      path("debug.js") {
        getFromResource("debug.js")
      },
      (redirectToNoTrailingSlashIfPresent(StatusCodes.Found) & pathEnd) {
        getFromResource("debug.html")
      },
      resourceRoutes
    )
  }

  def buildGroupRoutes[T: Show](extractor: PathMatcher1[T], resources: ResourceGroup[T]): Route = {
    def resourcesRoutes(v: T) = resources.resources.foldLeft[Route](reject) {
      case (acc, resource) =>
        acc ~ path(resource.pathPrefix) {
          complete(resource.fetch(v))
        }
    }

    pathPrefix(resources.pathPrefix / extractor) { v =>
      concat(
        pathEnd {
          getFromResource("debug.html")
        },
        path("index.json") {
          val items = resources.resources.map { d =>
            IndexItem(
              toHumanReadable(d.pathPrefix),
              d.pathPrefix,
              s"${v.show}/${d.pathPrefix}",
              paramName = None
            )
          }

          complete(Index(toHumanReadable(resources.pathPrefix), items))
        },
        resourcesRoutes(v)
      )
    }
  }

  private def toHumanReadable(str: String): String =
    str.split("[\\s_+]").map(_.capitalize).mkString(" ")

}
