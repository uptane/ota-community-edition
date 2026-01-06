package com.advancedtelematic.director.http

import akka.http.scaladsl.model.StatusCodes
import cats.syntax.show.*
import com.advancedtelematic.director.data.AdminDataType.{MultiTargetUpdate, TargetUpdateRequest}
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.GeneratorOps.GenSample
import com.advancedtelematic.director.data.Generators
import com.advancedtelematic.director.util.{DefaultPatience, DirectorSpec, ResourceSpec}
import com.advancedtelematic.libats.codecs.CirceCodecs.*
import com.advancedtelematic.libats.data.ErrorCodes.MissingEntity
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateId
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.Json
import org.scalatest.OptionValues.*

class MultiTargetUpdatesResourceSpec
    extends DirectorSpec
    with Generators
    with DefaultPatience
    with ResourceSpec
    with AdminResources {

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
      responseAs[
        Map[HardwareIdentifier, TargetUpdateRequest]
      ] // This should be responseAs[MultiTargetUpdate], see comments on resource
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

  testWithNamespace("does not accept empty mtu") { implicit ns =>
    val mtu = MultiTargetUpdate(Map.empty)

    Post(apiUri("multi_target_updates"), mtu).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.InvalidMtu
    }
  }

  testWithNamespace("accepts user defined json") { implicit ns =>
    val userDefinedCustom = Json.obj("some" -> Json.fromString("val"))
    val toUpdate = GenTargetUpdate.generate.copy(userDefinedCustom = Some(userDefinedCustom))
    val toUpdateReq = TargetUpdateRequest(None, toUpdate)
    val hwId = GenHardwareIdentifier.generate
    val mtu = MultiTargetUpdate(Map(hwId -> toUpdateReq))

    val id = Post(apiUri("multi_target_updates"), mtu).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Created
      responseAs[UpdateId]
    }

    Get(apiUri(s"multi_target_updates/${id.show}")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val r =
        responseAs[
          Map[HardwareIdentifier, TargetUpdateRequest]
        ] // This should be responseAs[MultiTargetUpdate], see comments on resource

      val _, update = r.head._2
      update.to.userDefinedCustom.value shouldBe userDefinedCustom
    }
  }

}
