package com.advancedtelematic.tuf.reposerver.http

import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}
import io.scalaland.chimney.dsl.*
import org.apache.pekko.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import com.advancedtelematic.libats.data.PaginationResult
import com.advancedtelematic.libtuf.data.TufDataType.{
  HardwareIdentifier,
  TargetFilename,
  ValidHardwareIdentifier,
  ValidTargetFilename
}
import com.advancedtelematic.tuf.reposerver.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.tuf.reposerver.http.PaginationParamsOps.PaginationParams
import com.advancedtelematic.tuf.reposerver.data.RepoCodecs.*
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.{ClientDataType, PackageSearchParameters}
import com.advancedtelematic.libtuf.data.ClientDataType.{
  AggregatedTargetItemsSort,
  ClientAggregatedPackage,
  ClientPackage,
  TargetItemsSort
}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.libats.http.RefinedMarshallingSupport.*
import PaginationResult.*
import scala.concurrent.ExecutionContext
import com.advancedtelematic.libats.codecs.CirceRefined.*
import com.advancedtelematic.tuf.reposerver.http.ReposerverPekkoPaths.TargetFilenamePath
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport

class RepoTargetsResource(namespaceValidation: NamespaceValidation)(
  implicit val db: Database,
  val ec: ExecutionContext)
    extends RepoNamespaceRepositorySupport {

  import org.apache.pekko.http.scaladsl.server.Directives.*
  import cats.syntax.either.*
  import TargetItemsSort.*
  import com.advancedtelematic.libtuf.data.ClientDataType.SortDirection
  import FailFastCirceSupport.*

  private implicit val hardwareIdentifierUnmarshaller
    : Unmarshaller[String, Refined[String, ValidHardwareIdentifier]] = Unmarshaller.strict { str =>
    refineV[ValidHardwareIdentifier](str).valueOr(err =>
      throw new IllegalArgumentException(s"invalid hardware id: $err")
    )
  }

  val SortByTargetItemsParam: Directive[(TargetItemsSort, ClientDataType.SortDirection)] =
    parameters(
      "sortBy".as[TargetItemsSort].?[TargetItemsSort](TargetItemsSort.CreatedAt),
      "sortDirection".as[SortDirection].?[SortDirection](SortDirection.Desc)
    )

  val SortByAggregatedTargetItemsParam: Directive[(AggregatedTargetItemsSort, SortDirection)] =
    parameters(
      "sortBy"
        .as[AggregatedTargetItemsSort]
        .?[AggregatedTargetItemsSort](AggregatedTargetItemsSort.LastVersionAt),
      "sortDirection".as[SortDirection].?[SortDirection](SortDirection.Desc)
    )

  val SearchParams: Directive1[PackageSearchParameters] = parameters(
    "origin".as(CsvSeq[String]).?,
    "originNot".as[String].?,
    "nameContains".as[String].?,
    "name".as[String].?,
    "version".as[String].?,
    "hardwareIds".as(CsvSeq[HardwareIdentifier]).?,
    "hashes".as(CsvSeq[Refined[String, ValidChecksum]]).?,
    "filenames".as(CsvSeq[TargetFilename]).?
  ).tflatMap {
    case (origin, originNot, nameContains, name, version, hardwareIds, hashes, filenames) =>
      provide(
        PackageSearchParameters(
          origin.getOrElse(Seq.empty),
          originNot,
          nameContains,
          name,
          version,
          hardwareIds.getOrElse(Seq.empty),
          hashes.getOrElse(Seq.empty),
          filenames.getOrElse(Seq.empty)
        )
      )
  }

  private var packageSearch = new PackageSearch()
  
  // format: off

  val route =
    (pathPrefix("user_repo")  & NamespaceRepoId(namespaceValidation, repoNamespaceRepo.findFor)) { repoId =>
      packageSearch = new PackageSearch()
      concat(
        path("packages" / TargetFilenamePath) { filename =>
          parameter("originNot".as[String].?) { originNot =>
            get {
              val f = packageSearch.findSingle(repoId, originNot, filename)
              complete(f)
            }
          }
        },
        path("hardwareids-packages") {
          get {
            val f = packageSearch.hardwareIdsWithPackages(repoId).map { values =>
              PaginationResult(values, values.length, 0L.toOffset, values.length.toLimit)
            }
            complete(f)
          }
        },
        path("search") {
          import FailFastCirceSupport.*

          (post & PaginationParams & SortByTargetItemsParam) { case (offset, limit, sortBy, sortDirection) =>
            entity(as[Option[PackageSearchParameters]]) { searchParams =>
              val f = for {
                count <- packageSearch.count(repoId, searchParams.getOrElse(PackageSearchParameters.empty))
                values <- packageSearch.find(repoId, offset, limit, searchParams.getOrElse(PackageSearchParameters.empty), sortBy, sortDirection)
              } yield {
                PaginationResult(values.map(_.transformInto[ClientPackage]), count, offset, limit)
              }

              complete(f)
            }
          }
        },
        path("grouped-search") {
          (get & PaginationParams & SearchParams & SortByAggregatedTargetItemsParam) { case (offset, limit, searchParams, sortBy, sortDirection) =>
            val f = for {
              count <- packageSearch.findAggregatedCount(repoId, searchParams)
              values <- packageSearch.findAggregated(repoId, offset, limit, searchParams, sortBy, sortDirection)
            } yield
              PaginationResult(values.map(_.transformInto[ClientAggregatedPackage]), count, offset, limit)

            complete(f)
          }
        }
      )

    }

  // format: on
}
