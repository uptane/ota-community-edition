package com.advancedtelematic.tuf.keyserver.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import cats.data.Validated.{Invalid, Valid}
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.http.UUIDKeyAkka._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ErrorCodes
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, RoleType, _}
import com.advancedtelematic.libtuf_server.data.Marshalling._
import com.advancedtelematic.tuf.keyserver.daemon.DefaultKeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus
import com.advancedtelematic.tuf.keyserver.db.{KeyGenRequestSupport, SignedRootRoleRepository}
import com.advancedtelematic.tuf.keyserver.roles._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe._
import io.circe.syntax._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class RootRoleResource()
                      (implicit val db: Database, val ec: ExecutionContext, mat: Materializer)
  extends KeyGenRequestSupport {
  import ClientRootGenRequest._
  import akka.http.scaladsl.server.Directives._

  val keyGenerationRequests = new KeyGenerationRequests()
  val signedRootRoles = new SignedRootRoles()
  val rootRoleKeyEdit = new RootRoleKeyEdit()
  val roleSigning = new RoleSigning()

  private def createRootNow(repoId: RepoId, genRequest: ClientRootGenRequest) = {
    require(genRequest.threshold > 0, "threshold must be greater than 0")

    val keyGenerationOp = DefaultKeyGenerationOp()

    val f = for {
      reqs <- keyGenerationRequests.createDefaultGenRequest(repoId, genRequest.threshold, genRequest.keyType, KeyGenRequestStatus.ERROR)
      _ <- Future.traverse(reqs)(keyGenerationOp)
    } yield StatusCodes.Created -> reqs.map(_.id)

    complete(f)
  }

  private def createRootLater(repoId: RepoId, genRequest: ClientRootGenRequest) = {
    require(genRequest.threshold > 0, "threshold must be greater than 0")

    val f = keyGenerationRequests
      .createDefaultGenRequest(repoId, genRequest.threshold, genRequest.keyType, KeyGenRequestStatus.REQUESTED)
      .map(StatusCodes.Accepted -> _.map(_.id))

    complete(f)
  }

  val route =
    pathPrefix("root" / RepoId.Path) { repoId =>
      pathEnd {
        put {
          val f = keyGenerationRequests.forceRetry(repoId).map(_ => StatusCodes.OK)
          complete(f)
        } ~
        (post & entity(as[ClientRootGenRequest]) & optionalHeaderValueByName("x-ats-tuf-force-sync")) {
          case (genRequest, Some(_)) =>
            createRootNow(repoId, genRequest)
          case (genRequest, None) =>
            createRootLater(repoId, genRequest)
        } ~
        get {
          val f = signedRootRoles.findFreshAndPersist(repoId)
          complete(f)
        }
      } ~
      path("1") {
        val f = signedRootRoles
          .findByVersion(repoId, version = 1)
          .recoverWith {
            case SignedRootRoleRepository.MissingSignedRole =>
              signedRootRoles.findFreshAndPersist(repoId)
          }

        complete(f)
      } ~
      path(IntNumber) { version =>
        complete(signedRootRoles.findByVersion(repoId, version))
      } ~
      pathPrefix("private_keys") {
        path(KeyIdPath) { keyId =>
          delete {
            val f = signedRootRoles
              .findFreshAndPersist(repoId)
              .flatMap(_ => rootRoleKeyEdit.deletePrivateKey(repoId, keyId))
              .map(_ ⇒ StatusCodes.NoContent)

            complete(f)
          }
        }
      } ~
      path(RoleTypePath) { roleType =>
        (post & entity(as[Json])) { payload =>
          val f = roleSigning.signWithRole(repoId, roleType, payload)
          complete(f)
        }
      } ~
      path("unsigned") {
        get {
          val f = signedRootRoles.findForSign(repoId)
          complete(f)
        } ~
        (post & entity(as[JsonSignedPayload])) { signedPayload =>
          val f: Future[ToResponseMarshallable] =
            signedRootRoles.persistUserSigned(repoId, signedPayload).map {
              case Valid(_) =>
                StatusCodes.NoContent
              case Invalid(errors) =>
                val err = ErrorRepresentation(ErrorCodes.KeyServer.InvalidRootRole, "Invalid user signed root", Option(errors.asJson))
                StatusCodes.BadRequest -> err
            }
          complete(f)
        }
      } ~
      pathPrefix("keys") {
        (get & path(KeyIdPath)) { keyId ⇒
          complete(rootRoleKeyEdit.findKeyPair(repoId, keyId))
        } ~
        pathPrefix("targets") { // TODO: This should be param roleType=targets
          path("pairs") { // TODO: This should be pathEnd
            get {
              onSuccess(rootRoleKeyEdit.findAllKeyPairs(repoId, RoleType.TARGETS)) {
                case pairs if pairs.nonEmpty => complete(pairs)
                case _ => complete(StatusCodes.NotFound)
              }
            }
          }
        }
      }
    }
}

object ClientRootGenRequest {
  implicit val encoder: Encoder[ClientRootGenRequest] = io.circe.generic.semiauto.deriveEncoder
  implicit val decoder: Decoder[ClientRootGenRequest] = io.circe.generic.semiauto.deriveDecoder
}

case class ClientRootGenRequest(threshold: Int = 1, keyType: KeyType = KeyType.default)
