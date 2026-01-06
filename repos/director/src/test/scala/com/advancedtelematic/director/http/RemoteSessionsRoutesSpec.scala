package com.advancedtelematic.director.http

import eu.timepit.refined.auto.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import cats.syntax.show.*
import com.advancedtelematic.director.data.Codecs.*
import com.advancedtelematic.director.data.Generators
import com.advancedtelematic.director.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.director.util.{DirectorSpec, RepositorySpec, ResourceSpec}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.ClientDataType.CommandName.{Reboot, RestartService}
import com.advancedtelematic.libtuf.data.ClientDataType.{
  CommandParameters,
  RemoteCommandsPayload,
  RemoteSessionsPayload,
  RemoteSessionsRole,
  RootRole,
  SshSessionProperties
}
import com.advancedtelematic.libtuf.data.TufCodecs.*
import com.advancedtelematic.libtuf.data.TufDataType.{RoleType, SignedPayload}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import io.circe.syntax.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.LoneElement.*
import org.scalatest.OptionValues.*

class RemoteSessionsRoutesSpec
    extends DirectorSpec
    with ResourceSpec
    with RepoNamespaceRepositorySupport
    with AdminResources
    with RepositorySpec
    with Generators
    with ProvisionedDevicesRequests {

  testWithRepo("can set remote commands for a device") { implicit ns =>
    val deviceId = DeviceId.generate()

    val body =
      RemoteCommandRequest(
        RemoteCommandsPayload(
          Map(RestartService -> CommandParameters(List("aktualizr"))),
          "v1alpha"
        )
      )

    Post(apiUri(s"admin/remote-commands"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Accepted
    }

    Get(apiUri(s"device/${deviceId.show}/remote-sessions.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val signedRole = responseAs[SignedPayload[RemoteSessionsRole]].signed
      val payload = signedRole.remote_commands.value

      payload shouldBe body.remoteCommands
    }

    Get(apiUri(s"admin/remote-sessions.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val signedRole = responseAs[SignedPayload[RemoteSessionsRole]].signed
      val payload = signedRole.remote_commands.value

      payload shouldBe body.remoteCommands
    }
  }

  testWithRepo("can set a remote session for a device") { implicit ns =>
    val deviceId = DeviceId.generate()

    val session = RemoteSessionsPayload(
      SshSessionProperties("someapiversion", Map.empty, Vector.empty, Vector.empty),
      "someapiversion"
    )

    val body = RemoteSessionRequest(session)

    Post(apiUri(s"admin/remote-sessions"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signedRole = responseAs[SignedPayload[RemoteSessionsRole]].signed
      signedRole.remote_sessions shouldBe session
    }

    Get(
      apiUri(s"device/${deviceId.show}/remote-sessions.json"),
      body
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val signedRole = responseAs[SignedPayload[RemoteSessionsRole]].signed
      signedRole.remote_sessions shouldBe session
    }

    Get(apiUri(s"admin/remote-sessions.json"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val signedRole = responseAs[SignedPayload[RemoteSessionsRole]].signed
      signedRole.remote_sessions shouldBe session
    }
  }

  testWithRepo("accepts an offline signed remote sessions payload") { implicit ns =>
    val deviceId = DeviceId.generate()

    val beforeSession = RemoteSessionsPayload(
      SshSessionProperties("someapiversion", Map.empty, Vector.empty, Vector.empty),
      "someapiversion"
    )

    Post(
      apiUri(s"admin/remote-sessions"),
      RemoteSessionRequest(beforeSession)
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val keyId = Get(apiUri(s"device/${deviceId.show}/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val rootRole = responseAs[SignedPayload[RootRole]].signed
      rootRole.roles.get(RoleType.REMOTE_SESSIONS).value.keyids.loneElement
    }

    val keyPair = keyserverClient.fetchKeypairByKeyId(keyId).value

    val session = RemoteSessionsPayload(
      SshSessionProperties(
        "someapiversion",
        Map.empty,
        Vector("ra-server-host"),
        Vector("ra-server-key")
      ),
      "someapiversion"
    )
    val remoteSessionsRole =
      RemoteSessionsRole(session, None, Instant.now().plus(365, ChronoUnit.DAYS), 2)
    val signature =
      TufCrypto.signPayload(keyPair.privkey, remoteSessionsRole.asJson).toClient(keyPair.pubkey.id)
    val signedPayload =
      SignedPayload(List(signature), remoteSessionsRole, remoteSessionsRole.asJson)

    Post(apiUri(s"admin/remote-sessions.json"), signedPayload).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"device/${deviceId.show}/remote-sessions.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signedPayload = responseAs[SignedPayload[RemoteSessionsRole]].signed
      signedPayload.version shouldBe 2
      signedPayload.remote_sessions shouldBe session
    }
  }

  testWithRepo("accepts an offline signed remote commands payload") { implicit ns =>
    val deviceId = DeviceId.generate()

    val body =
      RemoteCommandRequest(
        RemoteCommandsPayload(
          Map(RestartService -> CommandParameters(List("aktualizr"))),
          "v1alpha"
        )
      )

    Post(apiUri(s"admin/remote-commands"), body).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.Accepted
    }

    val keyId = Get(apiUri(s"device/${deviceId.show}/root.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val rootRole = responseAs[SignedPayload[RootRole]].signed
      rootRole.roles.get(RoleType.REMOTE_SESSIONS).value.keyids.loneElement
    }

    val keyPair = keyserverClient.fetchKeypairByKeyId(keyId).value

    val currentRole = Get(apiUri(s"admin/remote-sessions.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[SignedPayload[RemoteSessionsRole]].signed
    }

    val payload02 = RemoteCommandsPayload(
      Map(Reboot -> CommandParameters(List.empty)),
      "v1alpha"
    )

    val remoteSessionsRole = RemoteSessionsRole(
      currentRole.remote_sessions,
      Some(payload02),
      Instant.now().plus(365, ChronoUnit.DAYS),
      currentRole.version + 1
    )

    val signature =
      TufCrypto.signPayload(keyPair.privkey, remoteSessionsRole.asJson).toClient(keyPair.pubkey.id)

    val signedPayload =
      SignedPayload(List(signature), remoteSessionsRole, remoteSessionsRole.asJson)

    Post(apiUri(s"admin/remote-sessions.json"), signedPayload).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"device/${deviceId.show}/remote-sessions.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signedPayload = responseAs[SignedPayload[RemoteSessionsRole]].signed
      signedPayload.version shouldBe currentRole.version + 1

      signedPayload.remote_commands.value.allowed_commands shouldBe payload02.allowed_commands
    }
  }

}
