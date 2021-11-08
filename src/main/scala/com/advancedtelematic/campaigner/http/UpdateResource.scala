package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.advancedtelematic.campaigner.Settings
import com.advancedtelematic.campaigner.client.{DeviceRegistryClient, ResolverClient, UserProfileClient}
import com.advancedtelematic.campaigner.data.AkkaSupport._
import com.advancedtelematic.campaigner.data.Codecs._
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.campaigner.db.UpdateRepository
import com.advancedtelematic.campaigner.http.Errors.ConflictingUpdate
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.{ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import io.circe.{Encoder, Json}

import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object HypermediaResource {
  final case class Link(rel: String, uri: Uri)

  object Link {
    def self(uri: Uri): Link = Link("self", uri)
  }
  private[this] implicit val LinkEncoder = io.circe.generic.semiauto.deriveEncoder[Link]

  implicit def createEncoder[T](implicit tEnc: Encoder[T]): Encoder[HypermediaResource[T]] = {
    import io.circe.syntax._
    Encoder.instance { resource =>
      tEnc.apply(resource.value).deepMerge(Json.obj("_links" -> resource.links.asJson))
    }
  }
}

final case class HypermediaResource[T](links: Seq[HypermediaResource.Link], value: T)

class UpdateResource(extractNamespace: Directive1[Namespace],
                     deviceRegistry: DeviceRegistryClient,
                     resolver: ResolverClient,
                     userProfile: UserProfileClient,
                     updateRepo: UpdateRepository)(implicit ec: ExecutionContext) extends Settings {

  private[this] val pathToUpdates = Path.Empty / "api" / "v2" / "updates"

  private[this] def linkToSelf(update: Update): HypermediaResource[Update] = {
    val links = HypermediaResource.Link.self(
      Uri.Empty.withPath(pathToUpdates / update.uuid.uuid.toString)
    ) :: Nil
    HypermediaResource(links, update)
  }

  private def createUpdate(ns: Namespace, createUpdateRequest: CreateUpdate): Route = (extractLog & extractRequest) { (log, req) =>
    onComplete(updateRepo.persist(createUpdateRequest.mkUpdate(ns))) {
      case Success(uuid) =>
        val resourceUri = req.uri.withPath(req.uri.path / uuid.uuid.toString)
        complete((StatusCodes.Created, List(Location(resourceUri)), uuid))
      case Failure(ConflictingUpdate) =>
        onSuccess(updateRepo.findByExternalIds(ns, Seq(createUpdateRequest.updateSource.id))) {
          case x +: _ =>
            complete(StatusCodes.Conflict -> ErrorRepresentation(ConflictingUpdate.code, ConflictingUpdate.desc, Some(x.asJson), Some(ConflictingUpdate.errorId)))
          case _ =>
            log.error("Unable to find conflicting update {}:{}", ns.get, createUpdateRequest.updateSource.id)
            complete(StatusCodes.InternalServerError)
        }
      case Failure(ex) => failWith(ex)
    }
  }

  private def availableUpdatesForGroups(ns: Namespace, groups: Set[GroupId]): Route = {
    onComplete(getGroupUpdates(ns, groups)) {
      case Success(r) => complete(r)
      case Failure(_: TimeoutException) => complete(Errors.TimeoutFetchingUpdates)
      case _ => complete(StatusCodes.InternalServerError)
    }
  }

  private def getAllUpdates(ns: Namespace, offset: Long, limit: Long, sortBy: Option[SortBy], nameContains: Option[String]) =
    updateRepo.allPaginated(ns, sortBy.getOrElse(SortBy.Name), offset, limit, nameContains).map(_.map(linkToSelf))

  private def getGroupUpdates(ns: Namespace, groups: Set[GroupId]): Future[PaginationResult[HypermediaResource[Update]]] =
    userProfile
      .externalResolverUri(ns)
      .flatMap {
        case Some(uri) => new GroupUpdateResolver(deviceRegistry, resolver, uri, updateRepo).groupUpdates(ns, groups)
        case None => updateRepo.all(ns, updateType = Some(UpdateType.multi_target)) // TODO use the internal resolver from director once it's implemented
      }
      .map(updates => PaginationResult(updates, updates.size.toLong, 0, updates.size.toLong).map(linkToSelf))

  val route: Route =
    extractNamespace { ns =>
      pathPrefix("updates") {
        (path(UpdateId.Path) & pathEnd) { updateUuid =>
          complete(updateRepo.findById(updateUuid))
        } ~
        pathEnd {
          (post & entity(as[CreateUpdate])) { request =>
            createUpdate(ns, request)
          } ~
          (get & parameters('groupId.as[GroupId].*, 'nameContains.as[String].?, 'sortBy.as[SortBy].?, 'offset.as(nonNegativeLongUnmarshaller) ? 0L, 'limit.as(nonNegativeLongUnmarshaller) ? 50L)) {
            (groupId, nameContains, sortBy, offset, limit) => (groupId, nameContains) match {
              case (Nil, _) => complete(getAllUpdates(ns, offset, limit, sortBy, nameContains))
              case (groups, None) => availableUpdatesForGroups(ns, groups.toSet)
              case _ => complete((StatusCodes.BadRequest, "Cannot filter updates by group and name at the same time."))
            }
          }
        }
      }
    }
}
