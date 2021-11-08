package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.campaigner.data.DataType.CreateCampaign
import com.advancedtelematic.campaigner.data.Generators._
import com.advancedtelematic.campaigner.util.{CampaignerSpec, ResourceSpec, UpdateResourceSpecUtil}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import org.scalacheck.Arbitrary._

class DeviceResourceSpec
  extends CampaignerSpec
  with ResourceSpec
  with UpdateResourceSpecUtil {

  it should "returns scheduled campaigns for device" in {
    val (campaignId,request) = createCampaignWithUpdateOk(arbitrary[CreateCampaign].retryUntil(_.metadata.nonEmpty))
    val device = DeviceId.generate()

    campaigns.scheduleDevices(campaignId, Seq(device)).futureValue

    Get(apiUri(s"device/${device.uuid}/campaigns")).withHeaders(header) ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val res = responseAs[GetDeviceCampaigns]

      res.deviceId shouldBe device
      res.campaigns.map(_.id) shouldBe Seq(campaignId)
      res.campaigns.flatMap(_.metadata) shouldBe request.metadata.get
    }
  }
}
