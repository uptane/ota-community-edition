package com.advancedtelematic.tuf.keyserver.http

import org.apache.pekko.http.scaladsl.marshalling.ToResponseMarshallable
import org.apache.pekko.http.scaladsl.model.StatusCodes
import cats.data.Validated.{Invalid, Valid}
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.http.UUIDKeyPekko.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ErrorCodes
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, RoleType, *}
import com.advancedtelematic.libtuf_server.data.Marshalling.*
import com.advancedtelematic.tuf.keyserver.Settings
import com.advancedtelematic.tuf.keyserver.daemon.DefaultKeyGenerationOp
import com.advancedtelematic.tuf.keyserver.data.KeyServerDataType.KeyGenRequestStatus
import com.advancedtelematic.tuf.keyserver.db.SignedRootRoleRepository.MissingSignedRole
import com.advancedtelematic.tuf.keyserver.db.{KeyGenRequestSupport, SignedRootRoleRepository}
import com.advancedtelematic.tuf.keyserver.roles.*
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.*
import io.circe.syntax.*
import slick.jdbc.MySQLProfile.api.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RootRoleResource()(implicit val db: Database, val ec: ExecutionContext)
    extends KeyGenRequestSupport
    with Settings {

  import ClientRootGenRequest.*
  import org.apache.pekko.http.scaladsl.server.Directives.*

  val keyGenerationRequests = new KeyGenerationRequests()
  val signedRootRoles = new SignedRootRoles()
  val rootRoleKeyEdit = new RootRoleKeyEdit()
  val roleSigning = new RoleSigning()

  private def createRootNow(repoId: RepoId, genRequest: ClientRootGenRequest) = {
    require(genRequest.threshold > 0, "threshold must be greater than 0")

    val keyGenerationOp = DefaultKeyGenerationOp()

    val f = for { // use ERROR so the daemon doesn't pickup this request
      reqs <- keyGenerationRequests.createDefaultGenRequest(
        repoId,
        genRequest.threshold,
        genRequest.keyType,
        KeyGenRequestStatus.ERROR
      )
      _ <- Future.traverse(reqs)(keyGenerationOp)
      _ <- signedRootRoles.findFreshAndPersist(repoId) // Force generation of root.json role now
    } yield StatusCodes.Created -> reqs.map(_.id)

    complete(f)
  }

  private def createRootLater(repoId: RepoId, genRequest: ClientRootGenRequest) = {
    require(genRequest.threshold > 0, "threshold must be greater than 0")

    val f = keyGenerationRequests
      .createDefaultGenRequest(
        repoId,
        genRequest.threshold,
        genRequest.keyType,
        KeyGenRequestStatus.REQUESTED
      )
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
          (post & entity(as[ClientRootGenRequest])) {
            case genRequest
                if genRequest.forceSync.contains(true) || genRequest.forceSync.isEmpty =>
              createRootNow(repoId, genRequest)
            case genRequest =>
              createRootLater(repoId, genRequest)
          } ~
          (get & optionalHeaderValueByName("x-trx-expire-not-before").map(_.map(Instant.parse))) {
            expireNotBefore =>
              val f = signedRootRoles.findFreshAndPersist(repoId, expireNotBefore)
              complete(f)
          }
      } ~
        (path("rotate") & put) {
          onSuccess(rootRoleKeyEdit.rotate(repoId)) {
            complete(StatusCodes.OK)
          }
        } ~
        path("1") {
          val f = signedRootRoles
            .findByVersion(repoId, version = 1)
            .recoverWith { case SignedRootRoleRepository.MissingSignedRole =>
              signedRootRoles.findFreshAndPersist(repoId)
            }

          complete(f)
        } ~
        path(IntNumber) { version =>
          onComplete(signedRootRoles.findByVersion(repoId, version)) {
            case Success(role) =>
              complete(role)

            case Failure(err) if err == MissingSignedRole =>
              onSuccess(signedRootRoles.findFreshAndPersist(repoId)) { latestRoot =>
                if (latestRoot.signed.version == version)
                  complete(latestRoot)
                else {
                  val notFoundErrRepr = ErrorRepresentation(
                    MissingSignedRole.code,
                    MissingSignedRole.msg,
                    None,
                    Option(MissingSignedRole.errorId)
                  ).asJson
                  val refreshErr = ErrorRepresentation(
                    MissingSignedRole.code,
                    "Root role not found and role not refreshed",
                    Option(notFoundErrRepr),
                    Option(MissingSignedRole.errorId)
                  )
                  complete(
                    StatusCodes.NotFound -> refreshErr.asJson
                  ) // Avoids logging error by returning ready respond instead of using failWith
                }
              }

            case Failure(ex) =>
              failWith(ex)
          }
        } ~
        pathPrefix("private_keys") {
          path(KeyIdPath) { keyId =>
            delete {
              val f = signedRootRoles
                .findFreshAndPersist(repoId)
                .flatMap(_ => rootRoleKeyEdit.deletePrivateKey(repoId, keyId))
                .map(_ => StatusCodes.NoContent)

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
        (path("roles" / "offline-updates") & put) {
          val f = for {
            _ <- signedRootRoles.addRolesIfNotPresent(
              repoId,
              RoleType.OFFLINE_UPDATES,
              RoleType.OFFLINE_SNAPSHOT
            )
          } yield StatusCodes.OK

          complete(f)
        } ~
        (path("roles" / "remote-sessions") & put) {
          val f = for {
            _ <- signedRootRoles.addRolesIfNotPresent(repoId, RoleType.REMOTE_SESSIONS)
          } yield StatusCodes.OK

          complete(f)
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
                    val err = ErrorRepresentation(
                      ErrorCodes.KeyServer.InvalidRootRole,
                      "Invalid user signed root",
                      Option(errors.asJson)
                    )
                    StatusCodes.BadRequest -> err
                }
              complete(f)
            }
        } ~
        pathPrefix("keys") {
          (get & path(KeyIdPath)) { keyId =>
            complete(rootRoleKeyEdit.findKeyPair(repoId, keyId))
          } ~
            pathPrefix("targets") { // TODO: This should be param roleType=targets
              path("pairs") { // TODO: This should be pathEnd
                get {
                  onSuccess(rootRoleKeyEdit.findAllKeyPairs(repoId, RoleType.TARGETS)) {
                    case pairs if pairs.nonEmpty => complete(pairs)
                    case _                       => complete(StatusCodes.NotFound)
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

case class ClientRootGenRequest(threshold: Int = 1,
                                keyType: KeyType = KeyType.default,
                                forceSync: Option[Boolean] = Some(true))
