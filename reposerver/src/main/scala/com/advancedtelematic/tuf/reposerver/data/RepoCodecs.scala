package com.advancedtelematic.tuf.reposerver.data

import com.advancedtelematic.tuf.reposerver.data.RepoDataType.{
  AddDelegationFromRemoteRequest,
  AggregatedPackage,
  Package
}
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libats.codecs.CirceRefined.*
import com.advancedtelematic.libats.http.HttpCodecs.*
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libats.codecs.CirceAts.*
import com.advancedtelematic.libats.http.HttpCodecs.*
import io.circe.Codec
import io.circe.generic.semiauto.*

object RepoCodecs {

  implicit val addDelegationFromRemoteRequestCodec: io.circe.Codec[AddDelegationFromRemoteRequest] =
    deriveCodec[AddDelegationFromRemoteRequest]

  implicit val packageCodec: Codec[Package] =
    deriveCodec[Package]

  implicit val aggregatedPackageCodec: Codec[AggregatedPackage] =
    deriveCodec[AggregatedPackage]

}
