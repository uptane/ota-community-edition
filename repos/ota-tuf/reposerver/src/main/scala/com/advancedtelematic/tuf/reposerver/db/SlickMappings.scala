package com.advancedtelematic.tuf.reposerver.db

import com.advancedtelematic.libats.slick.db.SlickCirceMapper
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedRoleName, DelegationFriendlyName}
import com.advancedtelematic.libtuf.data.ValidatedString
import com.advancedtelematic.libtuf.data.ValidatedString.{
  ValidatedString,
  ValidatedStringValidation
}
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.StorageMethod
import com.advancedtelematic.tuf.reposerver.data.RepoDataType.StorageMethod.StorageMethod
import slick.jdbc.MySQLProfile.api._

import scala.annotation.nowarn
import scala.reflect.ClassTag

object SlickMappings {

  implicit val storageMethodMapping: slick.jdbc.MySQLProfile.BaseColumnType[
    com.advancedtelematic.tuf.reposerver.data.RepoDataType.StorageMethod.StorageMethod
  ] = MappedColumnType.base[StorageMethod, String](
    {
      case StorageMethod.CliManaged => "CliManaged"
      case StorageMethod.Managed    => "Managed"
      case StorageMethod.Unmanaged  => "Unmanaged"
    },
    {
      case "CliManaged" => StorageMethod.CliManaged
      case "Managed"    => StorageMethod.Managed
      case "Unmanaged"  => StorageMethod.Unmanaged
    }
  )

  // TODO: We need to migrate existing data from json to string so we can stop using this mapper
  @deprecated(
    "Use com.advancedtelematic.director.db.validatedStringMapper. This codec encode/decodes as json, which is not needed"
  )
  private def validatedStringMapper[W <: ValidatedString: ClassTag](
    implicit validation: ValidatedStringValidation[W]) = {
    implicit val decoder = ValidatedString.validatedStringDecoder[W]
    implicit val encoder = ValidatedString.validatedStringEncoder[W]
    SlickCirceMapper.circeMapper[W]
  }

  @nowarn
  implicit val delegatedRoleNameMapper = validatedStringMapper[DelegatedRoleName]

  @nowarn
  implicit val delegationFriendlyNameMapper = validatedStringMapper[DelegationFriendlyName]

  val remoteHeadersMapper = SlickCirceMapper.circeMapper[Map[String, String]]
}
