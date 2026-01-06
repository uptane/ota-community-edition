package com.advancedtelematic.tuf.reposerver.http

import com.advancedtelematic.libats.data.PaginationResult.*
import io.circe.syntax.*
import com.advancedtelematic.libats.data.ErrorRepresentation.*
import org.apache.pekko.http.scaladsl.model.headers.{`Content-Length`, RawHeader}
import org.apache.pekko.http.scaladsl.model.{
  EntityStreamException,
  HttpEntity,
  HttpHeader,
  HttpRequest,
  HttpResponse,
  MediaTypes,
  ParsingException,
  StatusCode,
  StatusCodes,
  Uri
}
import org.apache.pekko.http.scaladsl.server.*
import org.apache.pekko.http.scaladsl.unmarshalling.*
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.Validated.{Invalid, Valid}
import com.advancedtelematic.libats.codecs.CirceRefined.*
import com.advancedtelematic.libats.codecs.CirceValidatedGeneric.validatedGenericDecoder
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import com.advancedtelematic.libats.data.RefinedUtils.*
import com.advancedtelematic.libats.http.Errors.{EntityAlreadyExists, MissingEntity}
import com.advancedtelematic.libats.http.RefinedMarshallingSupport.*
import com.advancedtelematic.libats.http.UUIDKeyPekko.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientHashes,
  ClientTargetItem,
  DelegatedRoleName,
  Delegation,
  DelegationClientTargetItem,
  DelegationInfo,
  RootRole,
  TargetCustom,
  TargetsRole
}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libats.http.AnyvalMarshallingSupport.*
import com.advancedtelematic.libats.data.DataType.{Namespace, ValidChecksum}
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import com.advancedtelematic.libats.data.{ErrorRepresentation, PaginationResult}
import com.advancedtelematic.libtuf.data.{ClientCodecs, TufCodecs}
import com.advancedtelematic.libtuf.data.TufDataType.*
import com.advancedtelematic.libtuf_server.data.Marshalling.*
import com.advancedtelematic.libtuf_server.data.Requests.*
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient.RootRoleNotFound
import com.advancedtelematic.libtuf_server.repo.client.ReposerverClient.{
  EditTargetItem,
  RequestTargetItem
}
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.tuf.reposerver.Settings
import com.advancedtelematic.libtuf_server.repo.server.DataType.*
import com.advancedtelematic.libtuf_server.repo.server.RepoRoleRefresh
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.*
import com.advancedtelematic.tuf.reposerver.db.*
import com.advancedtelematic.tuf.reposerver.delegations.{
  DelegationsManagement,
  RemoteDelegationClient
}
import com.advancedtelematic.tuf.reposerver.http.Errors.{
  DelegationNotFound,
  NoRepoForNamespace,
  RequestCanceledByUpstream,
  TargetNotFoundError
}
import com.advancedtelematic.tuf.reposerver.http.RoleChecksumHeader.*
import com.advancedtelematic.tuf.reposerver.target_store.TargetStore
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.Json
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api.*

import scala.async.Async.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.advancedtelematic.tuf.reposerver.data.RepoCodecs.*
import com.advancedtelematic.tuf.reposerver.http.PaginationParamsOps.*
import eu.timepit.refined.api.Refined
import com.advancedtelematic.libtuf_server.data.Marshalling.jsonSignedPayloadMarshaller
import com.advancedtelematic.tuf.reposerver.http.ReposerverPekkoPaths.TargetFilenamePath

class RepoResource(keyserverClient: KeyserverClient,
                   namespaceValidation: NamespaceValidation,
                   targetStore: TargetStore,
                   tufTargetsPublisher: TufTargetsPublisher,
                   remoteDelegationsClient: RemoteDelegationClient)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends Directives
    with TargetItemRepositorySupport
    with RepoNamespaceRepositorySupport
    with FilenameCommentRepository.Support
    with SignedRoleRepositorySupport
    with Settings {

  private implicit val signedRoleGeneration
    : com.advancedtelematic.libtuf_server.repo.server.SignedRoleGeneration =
    TufRepoSignedRoleGeneration(keyserverClient)

  private implicit val _client
    : com.advancedtelematic.tuf.reposerver.delegations.RemoteDelegationClient =
    remoteDelegationsClient

  private val offlineSignedRoleStorage = new OfflineSignedRoleStorage(keyserverClient)

  private val roleRefresher = new RepoRoleRefresh(
    keyserverClient,
    new TufRepoSignedRoleProvider(),
    new TufRepoTargetItemsProvider()
  )

  private val targetRoleEdit = new TargetRoleEdit(signedRoleGeneration)
  private val delegations = new DelegationsManagement()
  private val trustedDelegations = new TrustedDelegations

  private val timeoutResponseHandler: HttpRequest => HttpResponse = _ => {
    log.warn(
      s"Request timed out from client. Returning ${StatusCodes.NetworkReadTimeout}. timeouts: pekko-idle-timeout=$pekkoIdleTimeout,pekko-request-timeout=$pekkoRequestTimeout,reposerver-request-timeout=$userRepoUploadRequestTimeout"
    )
    HttpResponse(
      StatusCodes.NetworkReadTimeout,
      entity = HttpEntity("Timed out receiving request from client")
    )
  }

  /*
    extractRequestEntity is needed for tests only. We should get this value from the `Content-Length` header.
    Http clients send this as a header, but pekko-http-testkit takes the header and makes it available only
    through this method.
   */
  private val withContentLengthCheck: Directive1[Long] =
    (optionalHeaderValueByType(`Content-Length`) & extractRequestEntity)
      .tmap { case (clHeader, entity) => clHeader.map(_.length).orElse(entity.contentLengthOption) }
      .flatMap {
        case Some(cl) if cl <= outOfBandUploadLimit => provide(cl)
        case Some(cl) => failWith(Errors.PayloadTooLarge(cl, outOfBandUploadLimit))
        case None     => reject(MissingHeaderRejection("Content-Length"))
      }

  private val withMultipartUploadFileSizeCheck: Directive1[Long] =
    parameter(Symbol("fileSize").as[Long]).flatMap { fileSize =>
      if (fileSize > outOfBandUploadLimit)
        failWith(Errors.PayloadTooLarge(fileSize, outOfBandUploadLimit))
      else provide(fileSize)
    }

  private val withMultipartUploadPartSizeCheck: Directive1[Long] =
    parameters(Symbol("contentLength").as[Int], Symbol("part").as[Int])
      .tmap { case (partContentLength, partNumber) =>
        val totalFileSize = multipartUploadPartSize * (partNumber - 1) + partContentLength
        (partContentLength, totalFileSize)
      }
      .tflatMap {
        case (partContentLength, _) if partContentLength > multipartUploadPartSize =>
          failWith(Errors.FilePartTooLarge(partContentLength, multipartUploadPartSize))
        case (_, totalFileSize) if totalFileSize > outOfBandUploadLimit =>
          failWith(Errors.PayloadTooLarge(totalFileSize, outOfBandUploadLimit))
        case (_, totalFileSize) =>
          provide(totalFileSize)
      }

  val log = LoggerFactory.getLogger(this.getClass)

  private def createRepo(namespace: Namespace, repoId: RepoId, keyType: KeyType): Route =
    complete {
      repoNamespaceRepo
        .ensureNotExists(namespace)
        .flatMap(_ => keyserverClient.createRoot(repoId, keyType))
        .flatMap(_ => repoNamespaceRepo.persist(repoId, namespace))
        .map(_ => repoId)
    }

  private val malformedRequestContentRejectionHandler = RejectionHandler
    .newBuilder()
    .handle { case MalformedRequestContentRejection(msg, _) =>
      complete((StatusCodes.BadRequest, msg))
    }
    .result()

  private def createRepo(namespace: Namespace, repoId: RepoId): Route =
    handleRejections(malformedRequestContentRejectionHandler) {
      entity(as[CreateRepositoryRequest]) { request =>
        createRepo(namespace, repoId, request.keyType)
      }
    } ~ createRepo(namespace, repoId, KeyType.default)

  private def addTargetItem(namespace: Namespace, item: TargetItem): Future[JsonSignedPayload] =
    for {
      result <- targetRoleEdit.addTargetItem(item)
      _ <- tufTargetsPublisher.targetAdded(namespace, item)
    } yield result

  private def addTarget(namespace: Namespace,
                        filename: TargetFilename,
                        repoId: RepoId,
                        clientItem: RequestTargetItem): Route =
    complete {
      val targetFormat = clientItem.targetFormat.orElse(Some(TargetFormat.BINARY))
      val custom = for {
        name <- clientItem.name
        version <- clientItem.version
      } yield TargetCustom(
        name,
        version,
        clientItem.hardwareIds,
        targetFormat,
        uri = Option(clientItem.uri.toURI)
      )

      addTargetItem(
        namespace,
        TargetItem(
          repoId,
          filename,
          Option(clientItem.uri),
          clientItem.checksum,
          clientItem.length,
          custom,
          StorageMethod.Unmanaged
        )
      )
    }

  private def storeTarget(ns: Namespace,
                          repoId: RepoId,
                          filename: TargetFilename,
                          custom: TargetCustom,
                          file: Source[ByteString, Any],
                          size: Option[Long]): Future[JsonSignedPayload] =
    for {
      item <- targetStore.store(repoId, filename, file, custom, size)
      result <- addTargetItem(ns, item)
    } yield result

  private def addTargetFromContent(namespace: Namespace,
                                   filename: TargetFilename,
                                   repoId: RepoId): Route =
    TargetCustomParameterExtractors.all { custom =>
      concat(
        withSizeLimit(userRepoSizeLimit) {
          withRequestTimeout(userRepoUploadRequestTimeout, timeoutResponseHandler) {
            fileUpload("file") { case (_, file) =>
              complete(storeTarget(namespace, repoId, filename, custom, file, size = None))
            }
          }
        },
        (withSizeLimit(userRepoSizeLimit) & withRequestTimeout(
          userRepoUploadRequestTimeout,
          timeoutResponseHandler
        ) & extractRequestEntity) { entity =>
          entity.contentLengthOption match {
            case Some(size) if size > 0 =>
              complete(
                storeTarget(namespace, repoId, filename, custom, entity.dataBytes, Option(size))
                  .map(_ => StatusCodes.NoContent)
              )
            case _ => reject(MissingHeaderRejection("Content-Length"))
          }
        },
        parameter(Symbol("fileUri")) { fileUri =>
          complete {
            for {
              item <- targetStore.storeFromUri(repoId, filename, Uri(fileUri), custom)
              result <- addTargetItem(namespace, item)
            } yield result
          }
        }
      )
    }

  private def withRepoIdHeader(repoId: RepoId) = respondWithHeader(
    RawHeader("x-ats-tuf-repo-id", repoId.uuid.toString)
  )

  private def findRootByVersion(repoId: RepoId, version: Int): Route =
    onComplete(keyserverClient.fetchRootRole(repoId, version)) {
      case Success(root)                           => complete(JsonSignedPayload(root.signatures, root.signed.asJson))
      case Failure(err) if err == RootRoleNotFound =>
        // Devices always request current version + 1 to see if it exists, so this is a normal response, do not log an error and instead just log a debug message
        log.debug(s"Root role not found in keyserver for $repoId, version=$version")
        val err = ErrorRepresentation(
          RootRoleNotFound.code,
          RootRoleNotFound.desc,
          None,
          Option(RootRoleNotFound.errorId)
        )
        complete(StatusCodes.NotFound -> err.asJson)
      case Failure(err) => failWith(err)
    }

  private def findRole(repoId: RepoId, roleType: RoleType): Route =
    encodeResponse {
      onSuccess(signedRoleGeneration.findRole(repoId, roleType, roleRefresher)) { signedRole =>
        respondWithCheckSum(signedRole.checksum.hash) {
          complete(signedRole.content)
        }
      }
    }

  def findComment(repoId: RepoId, filename: TargetFilename): Route =
    complete {
      filenameCommentRepo.find(repoId, filename).map(CommentRequest.apply)
    }

  def findCommentsForFiles(repoId: RepoId, filenames: Seq[TargetFilename]): Route =
    complete {
      filenameCommentRepo
        .findForFilenames(repoId, filenames)
        .map(_.map { case (name, comment) => FilenameComment(name, comment) })
    }

  def findComments(repoId: RepoId,
                   nameContains: Option[String] = None,
                   offset: Offset,
                   limit: Limit): Route =
    complete {
      filenameCommentRepo.find(repoId, nameContains, offset, limit).map {
        _.map { case (filename, comment) => FilenameComment(filename, comment) }
      }
    }

  def addComment(repoId: RepoId, filename: TargetFilename, commentRequest: CommentRequest): Route =
    complete {
      targetItemRepo.findByFilename(repoId, filename).flatMap { _ =>
        filenameCommentRepo.persist(repoId, filename, commentRequest.comment)
      }
    }

  def deleteTargetItem(namespace: Namespace, repoId: RepoId, filename: TargetFilename): Route =
    complete {
      for {
        targetItem <- targetStore.targetItemRepo.findByFilename(repoId, filename)
        _ <- targetRoleEdit.deleteTargetItem(repoId, filename)
        _ <- targetStore.delete(targetItem)
        _ <- tufTargetsPublisher.targetsMetaModified(namespace)
      } yield StatusCodes.NoContent
    }

  def editTargetItem(namespace: Namespace,
                     repoId: RepoId,
                     filename: TargetFilename,
                     targetEdit: EditTargetItem): Future[ClientTargetItem] =
    for {
      _ <- targetRoleEdit.editTargetItemCustom(repoId, filename, targetEdit)
      targetItem <- targetItemRepo.findByFilename(repoId, filename)
    } yield ClientTargetItem(
      Seq(targetItem.checksum.method -> targetItem.checksum.hash).toMap,
      targetItem.length,
      Some(targetItem.custom.asJson)
    )

  def saveOfflineTargetsRole(repoId: RepoId,
                             namespace: Namespace,
                             signedPayload: SignedPayload[TargetsRole],
                             checksum: Option[RoleChecksum]): Future[SignedRole[TargetsRole]] =
    for {
      (newItems, newSignedRole) <- offlineSignedRoleStorage
        .saveTargetRole(targetStore)(repoId, signedPayload, checksum)
      _ <- tufTargetsPublisher.newTargetsAdded(namespace, signedPayload.signed.targets, newItems)
    } yield newSignedRole

  private def rotateRoot(repoId: RepoId): Future[StatusCode] = async {
    await(keyserverClient.rotateRoot(repoId))
    await {
      signedRoleGeneration
        .regenerateAllSignedRoles(repoId)
        .recoverWith { case err =>
          log.error("root.json was rotated, but signing targets.json failed", err)
          FastFuture.successful(())
        }
    }

    StatusCodes.OK
  }

  private def modifyRepoRoutes(repoId: RepoId): Route =
    (namespaceValidation(repoId) & withRepoIdHeader(repoId)) { namespace =>
      pathPrefix("root") {
        (path("rotate") & put) {
          complete(rotateRoot(repoId))
        } ~
          pathEnd {
            get {
              complete(keyserverClient.fetchUnsignedRoot(repoId))
            } ~
              (post & entity(as[SignedPayload[RootRole]])) { signedPayload =>
                complete(keyserverClient.updateRoot(repoId, signedPayload))
              }
          } ~
          path("private_keys" / KeyIdPath) { keyId =>
            delete {
              complete(keyserverClient.deletePrivateKey(repoId, keyId))
            } ~
              get {
                val f = keyserverClient.fetchKeyPair(repoId, keyId)
                complete(f)
              }
          }
      } ~
        (get & path(IntNumber ~ ".root.json")) { version =>
          findRootByVersion(repoId, version)
        } ~
        (get & path(JsonRoleTypeMetaPath)) { roleType =>
          findRole(repoId, roleType)
        } ~
        /*
      Welp, delegations and their respective API endpoints (and the surrounding vernacular) are a bit confusing.
        Trusted-Delegations refer to the references that must exist in a user's targets metadata in order to 'delegate' signing authority to a third party
        Delegations refer to the actual delegated metadata, that is, the json files signed with a third party key containing delegated targets
        We also made Trusted-Delegations the toplevel api endpoint for a collection of "api actions" that interact with delegations.
        These actions are:
         - creation of delegations by fetching the delegated metadata via a remote-uri (known as a remote delegation)
         - refreshing a remote-delegation using the saved remote-uri
         - setting and retrieving delegation info. Info being things like friendlyName, remoteUri, lastFetched, etc.
         */
        pathPrefix("trusted-delegations") {
          (pathEnd & put & entity(as[List[Delegation]])) { payload =>
            val f = trustedDelegations
              .add(repoId, payload)(signedRoleGeneration)
              .map(_ => StatusCodes.NoContent)
            f.foreach { _ =>
              // Launch and forget. We don't care about kafka msg errors in the api response, we will log any errors if sending fails
              tufTargetsPublisher.targetsMetaModified(namespace)
            }
            complete(f)
          } ~
            (pathEnd & get) {
              complete(trustedDelegations.get(repoId))
            } ~
            (path(DelegatedRoleNamePath) & delete) { delegatedRoleName =>
              val f = trustedDelegations
                .remove(repoId, delegatedRoleName)(signedRoleGeneration)
                .map(_ => StatusCodes.NoContent)
              f.foreach { _ =>
                // Launch and forget. We don't care about kafka msg errors in the api response, we will log any errors if sending fails
                tufTargetsPublisher.targetsMetaModified(namespace)
              }
              complete(f)
            } ~
            (put & path(DelegatedRoleNamePath / "remote") & pathEnd & entity(
              as[AddDelegationFromRemoteRequest]
            )) { (delegatedRoleName, req) =>
              onSuccess(
                delegations.createFromRemote(
                  repoId,
                  req.uri,
                  delegatedRoleName,
                  req.remoteHeaders.getOrElse(Map.empty),
                  req.friendlyName
                )
              ) {
                complete(StatusCodes.Created)
              }
            } ~
            (put & path(DelegatedRoleNamePath / "remote" / "refresh")) { delegatedRoleName =>
              complete(delegations.updateFromRemote(repoId, delegatedRoleName))
            } ~
            (get & path(DelegatedRoleNamePath / "info")) { delegatedRoleName =>
              onSuccess(delegations.find(repoId, delegatedRoleName)) { (_, delegationInfo) =>
                complete(StatusCodes.OK, delegationInfo)
              }
            } ~
            (patch & path(DelegatedRoleNamePath / "info") & entity(as[DelegationInfo])) {
              (delegatedRoleName, delegationInfo) =>
                onSuccess(delegations.setDelegationInfo(repoId, delegatedRoleName, delegationInfo)) {
                  complete(StatusCodes.OK)
                }
            } ~
            (get & path("info") & pathEnd) {
              val infos: Future[Map[String, DelegationInfo]] = for {
                trustedDelegations <- trustedDelegations.get(repoId)
                delegationInfos <- Future.sequence(
                  trustedDelegations.map(td =>
                    delegations
                      .find(repoId, td.name)
                      .map(d => td.name.value -> d._2)
                      .recover { case DelegationNotFound =>
                        td.name.value -> DelegationInfo(None, None, None, None)
                      }
                  )
                )
              } yield delegationInfos.toMap
              complete(StatusCodes.OK, infos)
            } ~
            path("keys") {
              (put & entity(as[List[TufKey]])) { keys =>
                val f = trustedDelegations
                  .setKeys(repoId, keys)(signedRoleGeneration)
                  .map(_ => StatusCodes.NoContent)
                f.foreach { _ =>
                  // Launch and forget. We don't care about kafka msg errors in the api response, we will log any errors if sending fails
                  tufTargetsPublisher.targetsMetaModified(namespace)
                }
                complete(f)
              } ~
                get {
                  complete(trustedDelegations.getKeys(repoId))
                }
            }
        } ~
        path("delegations" / DelegatedRoleUriPath) { roleName =>
          DelegatedRoleName.delegatedRoleNameValidation(roleName) match {
            case Valid(delegatedRoleName) =>
              (put & entity(as[SignedPayload[TargetsRole]]) & pathEnd) { payload =>
                complete(
                  delegations
                    .create(repoId, delegatedRoleName, payload)
                    .map(_ => StatusCodes.NoContent)
                )
              } ~
                get {
                  onSuccess(delegations.find(repoId, delegatedRoleName)) {
                    (delegation, delegationInfo) =>
                      delegationInfo match {
                        case DelegationInfo(Some(lastFetched), _, _, _) =>
                          complete(
                            StatusCodes.OK,
                            List(
                              RawHeader("x-ats-delegation-last-fetched-at", lastFetched.toString)
                            ),
                            delegation
                          )
                        case DelegationInfo(_, _, _, _) =>
                          complete(StatusCodes.OK -> delegation)
                      }
                  }
                }
            case Invalid(errList) =>
              complete(
                Errors.InvalidDelegationName(errList)
              ) // TODO: Unmarshaller should do this, just throw/reject with the exception will be handled and converted to response
          }
        } ~
        pathPrefix("delegations_items") {
          (get & pathPrefix(TargetFilenamePath)) { filename =>
            complete(delegations.findTargetByFilename(repoId, filename))
          } ~
            (pathEnd & PaginationParams & parameter("nameContains".?)) { (_, _, nameContains) =>
              // so currently the backend is parsing the json metadata file directly so we
              // dont do pagination here. Just return everything
              val targets = delegations.findTargets(repoId, nameContains).map { t =>
                PaginationResult(t, t.length, 0L.toOffset, t.length.toLimit)
              }
              complete(targets)
            }
        } ~
        pathPrefix("uploads") {
          (put & path(TargetFilenamePath) & withContentLengthCheck) { (filename, cl) =>
            val f = async {
              if (await(targetItemRepo.exists(repoId, filename)))
                throw new EntityAlreadyExists[TargetItem]()
              await(targetStore.buildStorageUrl(repoId, filename, cl))
            }
            onSuccess(f) { uri =>
              redirect(uri, StatusCodes.Found)
            }
          }
        } ~
        pathPrefix("multipart") {
          (post & path("initiate" / TargetFilenamePath) & withMultipartUploadFileSizeCheck) {
            (fileName, _) =>
              val rs = for {
                exists <- targetItemRepo.exists(repoId, fileName)
                result <-
                  if (exists) Future.failed(new EntityAlreadyExists[TargetItem]())
                  else targetStore.initiateMultipartUpload(repoId, fileName)
              } yield result
              complete(rs)
          } ~
            (get & path("url" / TargetFilenamePath) & parameters(
              Symbol("part"),
              Symbol("uploadId").as[MultipartUploadId],
              Symbol("md5"),
              Symbol("contentLength").as[Int]
            )) { (filename, partId, uploadId, md5, contentLength) =>
              withMultipartUploadPartSizeCheck { _ =>
                complete(
                  targetStore.buildSignedURL(repoId, filename, uploadId, partId, md5, contentLength)
                )
              }
            } ~
            (put & path("complete" / TargetFilenamePath)) { filename =>
              entity(as[CompleteUploadRequest]) { body =>
                complete(
                  targetStore
                    .completeMultipartUpload(repoId, filename, body.uploadId, body.partETags)
                )
              }
            }
        } ~
        // Ben thinks we should just move this under the patch endpoint below at PATCH:user_repo/targets/{targetFileName}
        pathPrefix("proprietary-custom" / TargetFilenamePath) { filename =>
          (patch & entity(as[Json])) { proprietaryCustom =>
            val f =
              targetRoleEdit.updateTargetProprietaryCustom(repoId, filename, proprietaryCustom)
            complete(f.map(_ => StatusCodes.NoContent))
          }
        } ~
        pathPrefix("targets") {
          path("expire" / "not-before") {
            (put & entity(as[ExpireNotBeforeRequest])) { req =>
              onComplete(repoNamespaceRepo.setExpiresNotBefore(repoId, Option(req.expireAt))) { _ =>
                val f = keyserverClient
                  .fetchRootRole(repoId, Option(req.expireAt))
                  .map { _ =>
                    StatusCodes.NoContent
                  }
                  .recoverWith { case err =>
                    FastFuture.failed(Errors.SetRootExpire(err))
                  }

                complete(f)
              }
            }
          } ~
            path(TargetFilenamePath) { filename =>
              post {
                entity(as[RequestTargetItem]) { clientItem =>
                  addTarget(namespace, filename, repoId, clientItem)
                }
              } ~
                put {
                  handleExceptions {
                    ExceptionHandler {
                      case e: EntityStreamException
                          if e.getMessage.contains(
                            "The HTTP parser was receiving an entity when the underlying connection was closed unexpectedly"
                          ) =>
                        failWith(RequestCanceledByUpstream(e))
                      case e: ParsingException
                          if e.getMessage.contains("Unexpected end of multipart entity") =>
                        failWith(RequestCanceledByUpstream(e))
                    }
                  }(addTargetFromContent(namespace, filename, repoId))
                } ~
                head {
                  onComplete(targetStore.find(repoId, filename)) {
                    case Success(_) => complete((StatusCodes.OK, HttpEntity.Empty))
                    case Failure(e) if e == TargetNotFoundError =>
                      complete((TargetNotFoundError.responseCode, HttpEntity.Empty))
                    case Failure(e) => failWith(e)
                  }
                } ~
                get {
                  complete(targetStore.retrieve(repoId, filename))
                } ~
                delete {
                  deleteTargetItem(namespace, repoId, filename)
                } ~
                (patch & entity(as[EditTargetItem])) { item =>
                  complete(editTargetItem(namespace, repoId, filename, item))
                }
            } ~
            withRequestTimeout(
              userRepoUploadRequestTimeout,
              timeoutResponseHandler
            ) { // For when SignedPayload[TargetsRole] is too big and takes a long time to upload
              decodeRequest {
                (pathEnd & put & entity(as[SignedPayload[TargetsRole]])) { signedPayload =>
                  extractRoleChecksumHeader { checksum =>
                    onSuccess(saveOfflineTargetsRole(repoId, namespace, signedPayload, checksum)) {
                      newSignedRole =>
                        respondWithCheckSum(newSignedRole.checksum.hash) {
                          complete(StatusCodes.NoContent)
                        }
                    }
                  }
                }
              }
            }
        } ~
        pathPrefix("comments") {

          (pathEnd & PaginationParams & parameter("nameContains".?)) {
            (offset, limit, nameContains) =>
              findComments(repoId, nameContains, offset, limit)
          } ~
            pathPrefix(TargetFilenamePath) { filename =>
              put {
                entity(as[CommentRequest]) { commentRequest =>
                  addComment(repoId, filename, commentRequest)
                }
              } ~
                get {
                  findComment(repoId, filename)
                }
            }
        } ~
        (post & pathPrefix("list-target-comments")) {
          (pathEnd & entity(as[Seq[TargetFilename]])) { filenames =>
            findCommentsForFiles(repoId, filenames)
          }
        } ~
        pathPrefix("target_items") {
          (get & pathPrefix(TargetFilenamePath)) { filename =>
            val targetItem = targetItemRepo
              .findByFilename(repoId, filename)
              .map { targetItem =>
                val someClientHashes: ClientHashes =
                  Map(targetItem.checksum.method -> targetItem.checksum.hash)
                ClientTargetItem(
                  someClientHashes,
                  targetItem.length,
                  Some(targetItem.custom.asJson)
                )
              }
            complete(targetItem)
          } ~
            (get & pathEnd & PaginationParams & parameter("nameContains".?)) {
              (offset, limit, nameContains) =>
                println(s"offset=$offset limit=$limit")

                val targetItems =
                  targetItemRepo.findForPaginated(repoId, nameContains, offset, limit)
                val clientTargetItems = targetItems.map(_.map { targetItem =>
                  val clientHashes: ClientHashes = Map[HashMethod, Refined[String, ValidChecksum]](
                    targetItem.checksum.method -> targetItem.checksum.hash
                  )
                  ClientTargetItem(clientHashes, targetItem.length, Some(targetItem.custom.asJson))
                })
                complete(clientTargetItems)
            }
        }
    }

  val route =
    (pathPrefix("user_repo") & namespaceValidation.extractor) { namespace =>
      (post & pathEnd) {
        val repoId = RepoId.generate()
        createRepo(namespace, repoId)
      } ~
        UserRepoId(namespace, repoNamespaceRepo.findFor) { repoId =>
          modifyRepoRoutes(repoId)
        }
    } ~
      (pathPrefix("repo" / RepoId.Path) & namespaceValidation.extractor) { (repoId, namespace) =>
        (pathEnd & post) {
          createRepo(namespace, repoId)
        } ~
          modifyRepoRoutes(repoId)
      }

}
