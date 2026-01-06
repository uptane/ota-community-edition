package com.advancedtelematic.tuf.reposerver.db

import java.time.Instant
import org.apache.pekko.http.scaladsl.model.Uri

import scala.util.Success
import scala.util.Failure
import org.apache.pekko.NotUsed
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.scaladsl.Source
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.{ErrorCode, PaginationResult}
import com.advancedtelematic.libats.http.Errors.{
  EntityAlreadyExists,
  MissingEntity,
  MissingEntityId,
  RawError
}
import com.advancedtelematic.libtuf.data.TufDataType.{
  validTargetFilename,
  JsonSignedPayload,
  RepoId,
  RoleType,
  TargetFilename
}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.*
import com.advancedtelematic.libtuf_server.repo.server.DataType.*
import com.advancedtelematic.libats.slick.db.SlickExtensions.*
import com.advancedtelematic.libats.slick.db.SlickPagination
import com.advancedtelematic.libats.slick.codecs.SlickRefined.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libtuf.data.ClientDataType.{
  ClientTargetItem,
  DelegatedRoleName,
  DelegationFriendlyName,
  SnapshotRole,
  TargetCustom,
  TimestampRole,
  TufRole
}
import com.advancedtelematic.libtuf_server.data.Requests.TargetComment
import com.advancedtelematic.libtuf_server.data.TufSlickMappings.*
import com.advancedtelematic.tuf.reposerver.db.DBDataType.{DbDelegation, DbSignedRole}
import com.advancedtelematic.tuf.reposerver.db.TargetItemRepositorySupport.MissingNamespaceException
import com.advancedtelematic.tuf.reposerver.http.Errors.*
import com.advancedtelematic.libtuf_server.repo.server.Errors.SignedRoleNotFound
import SlickMappings.{delegatedRoleNameMapper, delegationFriendlyNameMapper}
import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import shapeless.ops.function.FnToProduct
import shapeless.{Generic, HList, Succ}
import com.advancedtelematic.libtuf_server.repo.server.SignedRoleProvider
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.TargetItem
import com.advancedtelematic.tuf.reposerver.db.Schema.TargetItemTable
import com.advancedtelematic.tuf.reposerver.http.PaginationParamsOps.*
import slick.ast.Ordering

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace
import slick.jdbc.MySQLProfile.api.*
import slick.lifted.{AbstractTable, ColumnOrdered}

trait DatabaseSupport {
  val ec: ExecutionContext
  val db: Database
}

object TargetItemRepositorySupport {

  case class MissingNamespaceException(repoId: RepoId)
      extends Exception(s"Unknown namespace for repo $repoId")
      with NoStackTrace

}

trait TargetItemRepositorySupport extends DatabaseSupport {
  lazy val targetItemRepo = new TargetItemRepository()(db, ec)
}

protected[db] class TargetItemRepository()(implicit db: Database, ec: ExecutionContext) {
  import Schema.targetItems

  def persist(targetItem: TargetItem): Future[TargetItem] = db.run(persistAction(targetItem))

  def setCustom(repoId: RepoId,
                targetFilename: TargetFilename,
                custom: Option[TargetCustom]): Future[Unit] = db.run {
    targetItems
      .filter(_.repoId === repoId)
      .filter(_.filename === targetFilename)
      .map(_.custom)
      .update(custom)
      .handleSingleUpdateError(TargetNotFoundError)
  }

  def deleteItemAndComments(filenameComments: FilenameCommentRepository)(
    repoId: RepoId,
    filename: TargetFilename): Future[Unit] = db.run {
    filenameComments
      .deleteAction(repoId, filename)
      .andThen {
        targetItems
          .filter(_.repoId === repoId)
          .filter(_.filename === filename)
          .delete
      }
      .map(_ => ())
      .transactionally
  }

  protected[db] def resetAction(repoId: RepoId, ids: Set[TargetFilename]): DBIO[Unit] =
    targetItems.filter(_.repoId === repoId).filter(_.filename.inSet(ids)).delete.map(_ => ())

  protected[db] def createActionMany(items: Seq[TargetItem]): DBIO[Unit] =
    (targetItems ++= items).map(_ => ())

  protected[db] def createAction(targetItem: TargetItem): DBIO[TargetItem] =
    (targetItems += targetItem).map(_ => targetItem)

  protected[db] def persistAction(targetItem: TargetItem): DBIO[TargetItem] = {
    val findQ =
      targetItems.filter(_.repoId === targetItem.repoId).filter(_.filename === targetItem.filename)
    val now = Instant.now

    findQ.result.headOption.flatMap {
      case Some(existing) =>
        val targetCustom = targetItem.custom.map(
          _.copy(updatedAt = now, createdAt = existing.custom.map(_.createdAt).getOrElse(now))
        )
        val newTargetItem = targetItem.copy(custom = targetCustom)
        findQ.update(newTargetItem).map(_ => newTargetItem)
      case None =>
        createAction(
          targetItem.copy(custom = targetItem.custom.map(_.copy(updatedAt = now, createdAt = now)))
        )
    }.transactionally
  }

  def findForQuery(repoId: RepoId,
                   nameContains: Option[String] = None): Query[TargetItemTable, TargetItem, Seq] =
    nameContains match {
      case Some(substring) =>
        targetItems
          .filter(_.repoId === repoId)
          .filter(_.filename.mappedTo[String].like(s"%${substring}%"))
      case None =>
        targetItems.filter(_.repoId === repoId)
    }

  def findFor(repoId: RepoId, nameContains: Option[String] = None): Future[Seq[TargetItem]] =
    db.run {
      findForQuery(repoId, nameContains).result
    }

  def findForPaginated(repoId: RepoId,
                       nameContains: Option[String] = None,
                       offset: Offset,
                       limit: Limit): Future[PaginationResult[TargetItem]] = db.run {
    findForQuery(repoId, nameContains)
      .sortBy(t => ColumnOrdered(t.updatedAt, Ordering().asc))
      .paginateResult(offset, limit)
  }

  def exists(repoId: RepoId, filename: TargetFilename): Future[Boolean] =
    findByFilename(repoId, filename)
      .transform {
        case Success(_)                   => Success(true)
        case Failure(TargetNotFoundError) => Success(false)
        case Failure(err)                 => Failure(err)
      }

  def findByFilename(repoId: RepoId, filename: TargetFilename): Future[TargetItem] = db.run {
    targetItems
      .filter(_.repoId === repoId)
      .filter(_.filename === filename)
      .result
      .failIfNotSingle(TargetNotFoundError)
  }

  def usage(repoId: RepoId): Future[(Namespace, Long)] =
    db.run {
      val usage = targetItems
        .filter(_.repoId === repoId)
        .map(_.length)
        .sum
        .getOrElse(0L)
        .result

      val ns =
        Schema.repoNamespaces
          .filter(_.repoId === repoId)
          .map(_.namespace)
          .result
          .failIfNotSingle(MissingNamespaceException(repoId))

      ns.zip(usage)
    }

}

trait SignedRoleRepositorySupport extends DatabaseSupport {
  lazy val signedRoleRepository = new SignedRoleRepository()(db, ec)
}

protected[db] class SignedRoleRepository()(implicit val db: Database, val ec: ExecutionContext) {
  import DBDataType._

  private val signedRoleRepository = new DbSignedRoleRepository()

  def persistAll(repoId: RepoId, signedRoles: List[SignedRole[_]]): Future[Seq[DbSignedRole]] =
    signedRoleRepository.persistAll(signedRoles.map(_.asDbSignedRole(repoId)))

  def persist[T: TufRole](repoId: RepoId,
                          signedRole: SignedRole[T],
                          forceVersion: Boolean = false): Future[DbSignedRole] =
    signedRoleRepository.persist(signedRole.asDbSignedRole(repoId), forceVersion)

  def find[T](repoId: RepoId)(implicit tufRole: TufRole[T]): Future[SignedRole[T]] =
    signedRoleRepository.find(repoId, tufRole.roleType).map(_.asSignedRole)

  def storeAll(targetItemRepo: TargetItemRepository)(repoId: RepoId,
                                                     signedRoles: List[SignedRole[_]],
                                                     items: Seq[TargetItem],
                                                     toDelete: Set[TargetFilename]): Future[Unit] =
    signedRoleRepository.storeAll(targetItemRepo)(
      repoId,
      signedRoles.map(_.asDbSignedRole(repoId)),
      items,
      toDelete
    )

}

object DbSignedRoleRepository {

  def InvalidVersionBumpError(oldVersion: Int, newVersion: Int) =
    RawError(
      ErrorCode("invalid_version_bump"),
      StatusCodes.Conflict,
      s"Cannot bump version from $oldVersion to $newVersion"
    )

}

protected[db] class DbSignedRoleRepository()(implicit val db: Database, val ec: ExecutionContext) {

  import DbSignedRoleRepository.InvalidVersionBumpError
  import Schema.signedRoles

  import shapeless._

  import DBDataType._

  def persist(signedRole: DbSignedRole, forceVersion: Boolean = false): Future[DbSignedRole] =
    db.run(persistAction(signedRole, forceVersion).transactionally)

  protected[db] def persistAction(signedRole: DbSignedRole,
                                  forceVersion: Boolean): DBIO[DbSignedRole] =
    signedRoles
      .filter(_.repoId === signedRole.repoId)
      .filter(_.roleType === signedRole.roleType)
      .forUpdate
      .result
      .headOption
      .flatMap { old =>
        if (!forceVersion)
          ensureVersionBumpIsValid(signedRole)(old)
        else
          DBIO.successful(())
      }
      .flatMap(_ => signedRoles.insertOrUpdate(signedRole))
      .map(_ => signedRole)

  def persistAll(signedRoles: List[DbSignedRole]): Future[Seq[DbSignedRole]] = db.run {
    DBIO.sequence(signedRoles.map(sr => persistAction(sr, forceVersion = false))).transactionally
  }

  def find(repoId: RepoId, roleType: RoleType): Future[DbSignedRole] =
    db.run {
      signedRoles
        .filter(_.repoId === repoId)
        .filter(_.roleType === roleType)
        .result
        .headOption
        .failIfNone(SignedRoleNotFound(repoId, roleType))
    }

  def storeAll(targetItemRepo: TargetItemRepository)(repoId: RepoId,
                                                     signedRoles: List[DbSignedRole],
                                                     items: Seq[TargetItem],
                                                     toDelete: Set[TargetFilename]): Future[Unit] =
    db.run {
      targetItemRepo
        .resetAction(repoId, toDelete.toSet)
        .andThen(DBIO.sequence(signedRoles.map(sr => persistAction(sr, forceVersion = false))))
        .andThen(targetItemRepo.createActionMany(items))
        .map(_ => ())
        .transactionally
    }

  private def ensureVersionBumpIsValid(signedRole: DbSignedRole)(
    oldSignedRole: Option[DbSignedRole]): DBIO[Unit] =
    oldSignedRole match {
      case Some(sr)
          if signedRole.roleType != RoleType.ROOT && sr.version != signedRole.version - 1 =>
        DBIO.failed(InvalidVersionBumpError(sr.version, signedRole.version))
      case _ => DBIO.successful(())
    }

}

trait RepoNamespaceRepositorySupport extends DatabaseSupport {
  lazy val repoNamespaceRepo = new RepoNamespaceRepository()(db, ec)
}

protected[db] class RepoNamespaceRepository()(implicit db: Database, ec: ExecutionContext) {

  import com.advancedtelematic.libats.slick.db.SlickPipeToUnit.pipeToUnit
  import Schema.repoNamespaces

  val MissingRepoNamespace = MissingEntity[(RepoId, Namespace)]()

  val AlreadyExists = EntityAlreadyExists[(RepoId, Namespace)]()

  def persist(repoId: RepoId, namespace: Namespace): Future[Unit] = db.run {
    (repoNamespaces += (repoId, namespace, None)).handleIntegrityErrors(AlreadyExists)
  }

  def getExpiresNotBefore(repoId: RepoId): Future[Option[Instant]] = db
    .run {
      repoNamespaces
        .filter(_.repoId === repoId)
        .map(_.expiresNotBefore)
        .result
        .headOption
    }
    .map(_.flatten)

  def setExpiresNotBefore(repoId: RepoId, expiresNotBefore: Option[Instant]): Future[Unit] =
    db.run {
      repoNamespaces
        .filter(_.repoId === repoId)
        .map(_.expiresNotBefore)
        .update(expiresNotBefore)
        .handleSingleUpdateError(MissingEntity[RepoId]())
    }

  def ensureNotExists(namespace: Namespace): Future[Unit] =
    findFor(namespace)
      .flatMap(_ => FastFuture.failed(AlreadyExists))
      .recover { case MissingRepoNamespace => () }

  def findFor(namespace: Namespace): Future[RepoId] = db.run {
    repoNamespaces
      .filter(_.namespace === namespace)
      .map(_.repoId)
      .result
      .headOption
      .failIfNone(MissingRepoNamespace)
  }

  def belongsTo(repoId: RepoId, namespace: Namespace): Future[Boolean] = db.run {
    repoNamespaces
      .filter(_.repoId === repoId)
      .filter(_.namespace === namespace)
      .size
      .result
      .map(_ > 0)
  }

}

object FilenameCommentRepository {

  trait Support extends DatabaseSupport {
    lazy val filenameCommentRepo = new FilenameCommentRepository()(db, ec)
  }

  val CommentNotFound = MissingEntity[TargetComment]()

  val PackageMissing = MissingEntity[TargetFilename]()
}

protected[db] class FilenameCommentRepository()(implicit db: Database, ec: ExecutionContext) {

  import FilenameCommentRepository._
  import Schema.filenameComments

  def persist(repoId: RepoId, filename: TargetFilename, comment: TargetComment): Future[Int] =
    db.run {
      filenameComments
        .insertOrUpdate((repoId, filename, comment))
        .handleIntegrityErrors(PackageMissing)
    }

  def find(repoId: RepoId, filename: TargetFilename): Future[TargetComment] = db.run {
    filenameComments
      .filter(_.repoId === repoId)
      .filter(_.filename === filename)
      .map(_.comment)
      .result
      .headOption
      .failIfNone(CommentNotFound)
  }

  def find(repoId: RepoId,
           nameContains: Option[String] = None,
           offset: Offset,
           limit: Limit): Future[PaginationResult[(TargetFilename, TargetComment)]] =
    db.run {
      val allFileNameComments = filenameComments.filter(_.repoId === repoId)
      val comments = if (nameContains.isDefined) {
        allFileNameComments.filter(_.filename.mappedTo[String].like(s"%${nameContains.get}%"))
      } else allFileNameComments

      comments
        .sortBy(_.filename)
        .map(filenameComment => (filenameComment.filename, filenameComment.comment))
        .paginateResult(offset, limit)
    }

  def findForFilenames(
    repoId: RepoId,
    filenames: Seq[TargetFilename]): Future[Seq[(TargetFilename, TargetComment)]] = {
    val trueRep: Rep[Boolean] = true
    val result = db.run {
      filenameComments.filter(_.repoId === repoId).filter(_.filename.inSet(filenames)).result
    }
    result.map(_.map { case (_, filename, comment) => (filename, comment) })
  }

  def deleteAction(repoId: RepoId, filename: TargetFilename) =
    filenameComments.filter(_.repoId === repoId).filter(_.filename === filename).delete

}

trait DelegationRepositorySupport extends DatabaseSupport {
  lazy val delegationsRepo = new DelegationRepository()(db, ec)
}

protected[db] class DelegationRepository()(implicit db: Database, ec: ExecutionContext) {

  def find(repoId: RepoId, roleNames: DelegatedRoleName*): Future[DbDelegation] = db.run {
    Schema.delegations
      .filter(_.repoId === repoId)
      .filter(_.roleName.inSet(roleNames))
      .result
      .failIfNotSingle(DelegationNotFound)
  }

  def findAll(repoId: RepoId): Future[Seq[DbDelegation]] = db.run {
    Schema.delegations.filter(_.repoId === repoId).result
  }

  def persistAll(repoId: RepoId,
                 roleName: DelegatedRoleName,
                 content: JsonSignedPayload,
                 remoteUri: Option[Uri],
                 lastFetch: Option[Instant],
                 remoteHeaders: Map[String, String],
                 friendlyName: Option[DelegationFriendlyName],
                 items: Seq[DelegatedTargetItem]): Future[Unit] = db.run {
    val delegation =
      DbDelegation(repoId, roleName, content, remoteUri, lastFetch, remoteHeaders, friendlyName)

    DBIO
      .seq(
        deleteMissing(repoId, roleName, keep = items.map(_.filename)),
        Schema.delegations.insertOrUpdate(delegation),
        DBIO.sequence(items.map(Schema.delegatedTargetItems.insertOrUpdate))
      )
      .map(_ => ())
      .transactionally
  }

  private def deleteMissing(repoId: RepoId,
                            roleName: DelegatedRoleName,
                            keep: Seq[TargetFilename]): DBIO[Int] =
    Schema.delegatedTargetItems
      .filter(_.repoId === repoId)
      .filter(_.roleName === roleName)
      .filterNot(_.filename.inSet(keep))
      .delete

  def setDelegationFriendlyName(repoId: RepoId,
                                roleName: DelegatedRoleName,
                                friendlyName: DelegationFriendlyName): Future[Unit] = db.run {
    Schema.delegations
      .filter(d => d.repoId === repoId && d.roleName === roleName)
      .map(_.friendlyName)
      .update(Option(friendlyName))
      .handleSingleUpdateError(MissingEntity[RepoId]())
  }

}
