package com.advancedtelematic.director.http
import akka.http.scaladsl.model.StatusCodes
import cats.syntax.show._
import com.advancedtelematic.director.data.AdminDataType.{MultiTargetUpdate, TargetUpdateRequest}
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.Generators
import com.advancedtelematic.director.util.{DefaultPatience, DirectorSpec, RouteResourceSpec}
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libats.data.ErrorCodes.MissingEntity
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateId
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

class MultiTargetUpdatesResourceSpec extends DirectorSpec
  with Generators with DefaultPatience with RouteResourceSpec with AdminResources {

  test("fetching non-existent target info returns 404") {
    val id = UpdateId.generate()

    Get(apiUri(s"multi_target_updates/${id.uuid.toString}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
      responseAs[ErrorRepresentation].code shouldBe MissingEntity
    }
  }

  testWithNamespace("Legacy API: can GET multi-target updates") { implicit ns =>
    val mtu = createMtuOk()

    Get(apiUri(s"multi_target_updates/${mtu.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Map[HardwareIdentifier, TargetUpdateRequest]] // This should be responseAs[MultiTargetUpdate], see comments on resource
    }
  }

  testWithNamespace("can GET multi-target updates") { implicit ns =>
    pending // due to legacy api support

    val mtu = createMtuOk()

    Get(apiUri(s"multi_target_updates/${mtu.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[MultiTargetUpdate]
    }
  }


  testWithNamespace("accepts mtu with an update") { implicit ns =>
    createMtuOk()
  }
}
