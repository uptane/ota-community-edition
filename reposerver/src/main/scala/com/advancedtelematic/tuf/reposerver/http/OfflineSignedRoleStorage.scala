package com.advancedtelematic.tuf.reposerver.http

import akka.http.scaladsl.util.FastFuture
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, TargetCustom, TargetsRole, TufRole}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, SignedPayload, TargetFilename}
import com.advancedtelematic.libtuf_server.repo.server.DataType._
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType._
import com.advancedtelematic.tuf.reposerver.db.{SignedRoleRepositorySupport, TargetItemRepositorySupport}
import io.circe.Encoder
import cats.implicits._
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libats.http.Errors.MissingEntityId
import com.advancedtelematic.libtuf.data.ClientCodecs._
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole._
import com.advancedtelematic.libtuf_server.repo.server.Errors.SignedRoleNotFound

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.TargetItem
import com.advancedtelematic.tuf.reposerver.http.RoleChecksumHeader.RoleChecksum
import com.advancedtelematic.tuf.reposerver.target_store.TargetStore
import org.slf4j.LoggerFactory

class OfflineSignedRoleStorage(keyserverClient: KeyserverClient)
                                        (implicit val db: Database, val ec: ExecutionContext)
  extends SignedRoleRepositorySupport with TargetItemRepositorySupport{

  private val _log = LoggerFactory.getLogger(this.getClass)

  private val signedRoleGeneration = TufRepoSignedRoleGeneration(keyserverClient)

  def store(repoId: RepoId, signedPayload: SignedPayload[TargetsRole]): Future[ValidatedNel[String, (Seq[TargetItem], SignedRole[TargetsRole])]] =
    for {
      validatedPayloadSig <- payloadSignatureIsValid(repoId, signedPayload)
      existingTargets <- targetItemRepo.findFor(repoId)
      targetItemsValidated = validatedPayloadSig.andThen(_ => validatedPayloadTargets(repoId, signedPayload, existingTargets))
      signedRoleValidated <- targetItemsValidated match {
        case Valid(items) =>
          val signedTargetRole = SignedRole.withChecksum[TargetsRole](repoId, signedPayload.asJsonSignedPayload, signedPayload.signed.version, signedPayload.signed.expires)
          for {
            (targets, timestamps) <- signedRoleGeneration.freshSignedDependent(repoId, signedTargetRole, signedPayload.signed.expires)
            _ <- signedRoleRepository.storeAll(targetItemRepo)(repoId: RepoId, List(signedTargetRole, targets, timestamps), items)
          } yield (existingTargets, signedTargetRole).validNel
        case i @ Invalid(_) =>
          FastFuture.successful(i)
      }
    } yield signedRoleValidated

  private def validatedPayloadTargets(repoId: RepoId, payload: SignedPayload[TargetsRole], existingTargets: Seq[TargetItem]): ValidatedNel[String, List[TargetItem]] = {
    def errorMsg(filename: TargetFilename, msg: Any): String = s"target item error ${filename.value}: $msg"

    def validateNewChecksum(filename: TargetFilename, newItem: ClientTargetItem): Either[String, Checksum] = {
      newItem.hashes
        .headOption
        .map { case (method, hash) => Checksum(method, hash) }
        .toRight(errorMsg(filename, "Invalid/Missing Checksum"))
    }

    def validateExistingTarget(filename: TargetFilename, oldItem: TargetItem, newItem: ClientTargetItem): Either[String, TargetItem] =
      for {
        newTargetCustom <- newItem.custom match {
          case Some(customJson) => customJson.as[TargetCustom].leftMap(errorMsg(filename, _)).map(_.some)
          case None => Right(None)
        }
        checksum <- validateNewChecksum(filename, newItem)
      } yield TargetItem(repoId, filename, oldItem.uri, checksum, newItem.length, newTargetCustom, oldItem.storageMethod)

    def validateNewTarget(filename: TargetFilename, item: ClientTargetItem): Either[String, TargetItem] =
      for {
        json <- item.custom.toRight(errorMsg(filename, "new offline signed target items must contain custom metadata"))
        targetCustom <- json.as[TargetCustom].leftMap(errorMsg(filename, _))
        checksum <- validateNewChecksum(filename, item)
        storageMethod = if(targetCustom.cliUploaded.contains(true)) StorageMethod.CliManaged else StorageMethod.Unmanaged
      } yield TargetItem(repoId, filename, targetCustom.uri.map(_.toUri), checksum, item.length, Some(targetCustom), storageMethod)

    val existingTargetsAsMap = existingTargets.map { ti => ti.filename -> ti }.toMap

    val newTargetItems = payload.signed.targets.map { case (filename, item) =>
      existingTargetsAsMap.get(filename) match {
        case Some(ti) => validateExistingTarget(filename, ti, item)
        case None => validateNewTarget(filename, item)
      }
    }

    newTargetItems.map(_.toValidatedNel).toList.sequence
  }

  private def payloadSignatureIsValid[T : TufRole : Encoder](repoId: RepoId, signedPayload: SignedPayload[T]): Future[ValidatedNel[String, SignedPayload[T]]] = async {
    val rootRole = await(keyserverClient.fetchRootRole(repoId)).signed

    TufCrypto.payloadSignatureIsValid(rootRole, signedPayload)
  }

  def saveTargetRole(targetStore: TargetStore)
                    (repoId: RepoId, signedTargetPayload: SignedPayload[TargetsRole],
                    checksum: Option[RoleChecksum]): Future[(Seq[TargetItem], SignedRole[TargetsRole])] = async {
    await(ensureChecksumIsValidForSave(repoId: RepoId, checksum))

    // get the items before they get removed from the DB
    val previousTargetItems = await(targetItemRepo.findFor(repoId))
    val saveResult = await(store(repoId, signedTargetPayload))

    saveResult match {
      case Valid((items, signedPayload)) =>
        await(deleteOutdatedTargets(targetStore)(previousTargetItems, signedTargetPayload.signed.targets.keys))
        items -> signedPayload
      case Invalid(errors) =>
        throw Errors.InvalidOfflineTargets(errors)
    }
  }

  private def deleteOutdatedTargets(targetStore: TargetStore)
                                   (previousTargetItems: Seq[TargetItem], newFilenames: Iterable[TargetFilename]): Future[Unit] = {
    val previousMap = previousTargetItems.map(targetItem => (targetItem.filename, targetItem)).toMap
    val outdated = (previousMap -- newFilenames).values
    val results = outdated.map(targetStore.delete)

    Future.sequence(results)
      .map(_ => ())
      .recover { case ex =>
        _log.warn("Could not delete outdated targets", ex)
        ()
      }
  }

  private def ensureChecksumIsValidForSave(repoId: RepoId, checksumOpt: Option[RoleChecksum]): Future[Unit] = {
    val existingRoleF = signedRoleRepository.find[TargetsRole](repoId)
      .map(_.some)
      .recover {  case _: MissingEntityId[_] => None }

    existingRoleF.zip(FastFuture.successful(checksumOpt)).flatMap {
      case (None, _) =>
        FastFuture.successful(())
      case (Some(existing), Some(checksum)) if existing.checksum.hash == checksum =>
        FastFuture.successful(())
      case (Some(_), Some(_)) =>
        FastFuture.failed(Errors.RoleChecksumMismatch) // TODO: Should be 412?
      case (Some(_), None) =>
        FastFuture.failed(Errors.RoleChecksumNotProvided)
    }
  }
}
