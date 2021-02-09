package com.advancedtelematic.campaigner.data

import akka.http.scaladsl.unmarshalling.Unmarshaller
import com.advancedtelematic.campaigner.data.DataType.CampaignStatus.CampaignStatus
import com.advancedtelematic.campaigner.data.DataType.{CampaignStatus, GroupId, SortBy}
import com.advancedtelematic.campaigner.data.DataType.SortBy.SortBy
import com.advancedtelematic.libats.http.UUIDKeyAkka._

object AkkaSupport {
  implicit val groupIdUnmarshaller = GroupId.unmarshaller
  implicit val campaignStatusUnmarshaller = Unmarshaller.strict[String, CampaignStatus](CampaignStatus.withName)

  implicit val sortByUnmarshaller: Unmarshaller[String, SortBy] = Unmarshaller.strict {
    _.toLowerCase match {
      case "name"      => SortBy.Name
      case "createdat" => SortBy.CreatedAt
      case s           => throw new IllegalArgumentException(s"Invalid value for sorting parameter: '$s'.")
    }
  }
}
