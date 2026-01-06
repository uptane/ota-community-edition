package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.server.{Directive0, Directive1, Directives}
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV

object RoleChecksumHeader {
  import Directives._

  type RoleChecksum = Refined[String, ValidChecksum]

  private val HEADER_NAME: String = "x-ats-role-checksum"

  def apply(checksum: RoleChecksum): RawHeader = RawHeader(HEADER_NAME, checksum.value)

  def extractRoleChecksumHeader: Directive1[Option[RoleChecksum]] =
    optionalHeaderValueByName(HEADER_NAME).map { value =>
      value.flatMap(v => refineV[ValidChecksum](v).toOption)
    }

  def respondWithCheckSum(checksum: RoleChecksum): Directive0 =
    mapResponse(_.addHeader(apply(checksum)))

}
