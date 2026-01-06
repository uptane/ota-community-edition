package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.util.FastFuture
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  TargetCustom,
  TargetsRole,
  TufRole
}
import com.advancedtelematic.libtuf.data.TufDataType.{RepoId, SignedPayload, TargetFilename}
import com.advancedtelematic.libtuf_server.repo.server.DataType._
import com.advancedtelematic.tuf.reposerver.data.RepoDataType._
import com.advancedtelematic.tuf.reposerver.db.{
  SignedRoleRepositorySupport,
  TargetItemRepositorySupport
}
import io.circe.Encoder
import cats.implicits._
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libats.http.Errors.MissingEntityId
import com.advancedtelematic.libtuf.data.ClientCodecs._
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole._
import cats.implicits._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import com.advancedtelematic.libtuf_server.repo.server.DataType.SignedRole
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.TargetItem
import com.advancedtelematic.tuf.reposerver.http.RoleChecksumHeader.RoleChecksum
import com.advancedtelematic.tuf.reposerver.target_store.TargetStore
import org.slf4j.LoggerFactory

class OfflineSignedRoleStorage(keyserverClient: KeyserverClient)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends SignedRoleRepositorySupport
    with TargetItemRepositorySupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  private val signedRoleGeneration = TufRepoSignedRoleGeneration(keyserverClient)
  private val trustedDelegations = new TrustedDelegations

  def store(repoId: RepoId, signedPayload: SignedPayload[TargetsRole])
    : Future[ValidatedNel[String, (Seq[TargetItem], SignedRole[TargetsRole])]] = async {
    val validatedPayloadSig = await(payloadSignatureIsValid(repoId, signedPayload))
    val existingTargets = await(targetItemRepo.findFor(repoId))
    val delegationsValidated = trustedDelegations.validate(signedPayload.signed.delegations)
    val targetItemsValidated = validatedPayloadTargets(repoId, signedPayload, existingTargets)
    val signedRoleValidated = (validatedPayloadSig, delegationsValidated, targetItemsValidated)
      .mapN { case (_, _, targetItems) =>
        async {
          val signedTargetRole = await(
            SignedRole.withChecksum[TargetsRole](
              signedPayload.asJsonSignedPayload,
              signedPayload.signed.version,
              signedPayload.signed.expires
            )
          )
          val (targets, timestamps) = await(
            signedRoleGeneration
              .freshSignedDependent(repoId, signedTargetRole, signedPayload.signed.expires)
          )
          val (toDelete, toInsert) = groupTargetsByOperation(existingTargets, targetItems)
          await(
            signedRoleRepository.storeAll(targetItemRepo)(
              repoId: RepoId,
              List(signedTargetRole, targets, timestamps),
              toInsert,
              toDelete
            )
          )
          (existingTargets, signedTargetRole).validNel[String]
        }
      }
      .fold(err => FastFuture.successful(err.invalid), identity)
    await(signedRoleValidated)
  }

  // group `newTargets` into (deleted, newOrUpdated)
  private def groupTargetsByOperation(
    existing: Seq[TargetItem],
    newTargets: Seq[TargetItem]): (Set[TargetFilename], Seq[TargetItem]) = {
    val existingFilenames = existing.map(_.filename).toSet
    val newTargetMap = newTargets.map(i => i.filename -> i).toMap

    val deleted = existingFilenames -- newTargetMap.keySet
    val updated =
      existing.filter(i => newTargetMap.get(i.filename).exists(_ != i)).map(_.filename)

    val toDelete = deleted ++ updated // updated items need to be deleted and then inserted again
    val toInsert = newTargets.filter { i =>
      updated.contains(i.filename) || !existingFilenames.contains(i.filename)
    } // Updated or newly inserted

    _log.debug(s"deleted = $deleted, updated = $updated, insert = $toInsert")

    _log.info(
      s"storing offline signed: delete=${deleted.size}, updated = ${updated.size}, insert = ${toInsert.size}"
    )

    toDelete -> toInsert
  }

  private def validatedPayloadTargets(
    repoId: RepoId,
    payload: SignedPayload[TargetsRole],
    existingTargets: Seq[TargetItem]): ValidatedNel[String, List[TargetItem]] = {
    def errorMsg(filename: TargetFilename, msg: Any): String =
      s"target item error ${filename.value}: $msg"

    def validateNewChecksum(filename: TargetFilename,
                            newItem: ClientTargetItem): Either[String, Checksum] =
      newItem.hashes.headOption
        .map { case (method, hash) => Checksum(method, hash) }
        .toRight(errorMsg(filename, "Invalid/Missing Checksum"))

    // This is needed to convert from ClientTargetItem to TargetItem
    def validateExistingTarget(filename: TargetFilename,
                               oldItem: TargetItem,
                               newItem: ClientTargetItem): Either[String, TargetItem] =
      for {
        newTargetCustom <- newItem.custom match {
          case Some(customJson) =>
            customJson.as[TargetCustom].leftMap(errorMsg(filename, _)).map(_.some)
          case None => Right(None)
        }
        checksum <- validateNewChecksum(filename, newItem)
      } yield TargetItem(
        repoId,
        filename,
        oldItem.uri,
        checksum,
        newItem.length,
        newTargetCustom,
        oldItem.storageMethod
      )

    def validateNewTarget(filename: TargetFilename,
                          item: ClientTargetItem): Either[String, TargetItem] =
      for {
        json <- item.custom.toRight(
          errorMsg(filename, "new offline signed target items must contain custom metadata")
        )
        targetCustom <- json.as[TargetCustom].leftMap(errorMsg(filename, _))
        checksum <- validateNewChecksum(filename, item)
        storageMethod =
          if (targetCustom.cliUploaded.contains(true)) StorageMethod.CliManaged
          else StorageMethod.Unmanaged
      } yield TargetItem(
        repoId,
        filename,
        targetCustom.uri.map(_.toUri),
        checksum,
        item.length,
        Some(targetCustom),
        storageMethod
      )

    val existingTargetsAsMap = existingTargets.map(ti => ti.filename -> ti).toMap

    val newTargetItems = payload.signed.targets.map { case (filename, item) =>
      existingTargetsAsMap.get(filename) match {
        case Some(ti) => validateExistingTarget(filename, ti, item)
        case None     => validateNewTarget(filename, item)
      }
    }

    newTargetItems.map(_.toValidatedNel).toList.sequence
  }

  private def payloadSignatureIsValid[T: TufRole: Encoder](
    repoId: RepoId,
    signedPayload: SignedPayload[T]): Future[ValidatedNel[String, SignedPayload[T]]] = async {
    val rootRole = await(keyserverClient.fetchRootRole(repoId)).signed
    TufCrypto.payloadSignatureIsValid(rootRole, signedPayload)
  }

  def saveTargetRole(targetStore: TargetStore)(
    repoId: RepoId,
    signedTargetPayload: SignedPayload[TargetsRole],
    checksum: Option[RoleChecksum]): Future[(Seq[TargetItem], SignedRole[TargetsRole])] = async {
    await(ensureChecksumIsValidForSave(repoId: RepoId, checksum))
    val saveResult = await(store(repoId, signedTargetPayload))

    saveResult match {
      case Valid((oldItems, signedPayload)) =>
        // Delete from the target store (s3, local)
        await(deleteOutdatedTargets(targetStore)(oldItems, signedTargetPayload.signed.targets.keys))
        oldItems -> signedPayload
      case Invalid(errors) =>
        throw Errors.InvalidOfflineTargets(errors)
    }
  }

  private def deleteOutdatedTargets(targetStore: TargetStore)(
    previousTargetItems: Seq[TargetItem],
    newFilenames: Iterable[TargetFilename]): Future[Unit] = {
    val previousMap = previousTargetItems.map(targetItem => (targetItem.filename, targetItem)).toMap
    val outdated = (previousMap -- newFilenames).values
    val results = outdated.map(targetStore.delete)

    Future
      .sequence(results)
      .map(_ => ())
      .recover { case ex =>
        _log.warn("Could not delete outdated targets", ex)
        ()
      }
  }

  private def ensureChecksumIsValidForSave(repoId: RepoId,
                                           checksumOpt: Option[RoleChecksum]): Future[Unit] = {
    val existingRoleF = signedRoleRepository
      .find[TargetsRole](repoId)
      .map(_.some)
      .recover { case _: MissingEntityId[_] => None }

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
