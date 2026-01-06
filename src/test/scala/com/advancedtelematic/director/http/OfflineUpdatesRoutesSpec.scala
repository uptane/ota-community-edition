package com.advancedtelematic.director.http

import io.circe.syntax.*
import com.advancedtelematic.libtuf.crypt.CanonicalJson.*
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.data.Generators
import com.advancedtelematic.director.db.RepoNamespaceRepositorySupport
import com.advancedtelematic.director.util.{DirectorSpec, RepositorySpec, ResourceSpec}
import com.advancedtelematic.libtuf.data.ClientCodecs.*
import com.advancedtelematic.libtuf.data.TufCodecs.*
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import com.advancedtelematic.director.data.GeneratorOps.*
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.data.ErrorRepresentation
import com.advancedtelematic.libtuf.data.ClientDataType.{
  OfflineSnapshotRole,
  OfflineUpdatesRole,
  TufRole,
  ValidMetaPath
}
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import com.advancedtelematic.libtuf_server.crypto.Sha256Digest
import eu.timepit.refined.api.Refined
import slick.jdbc.MySQLProfile.api.*
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId.*
import cats.syntax.show.*
import com.advancedtelematic.director.data.Codecs.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import cats.syntax.option.*
import org.scalacheck.Gen
import org.scalatest.Inspectors.*

class OfflineUpdatesRoutesSpec
    extends DirectorSpec
    with ResourceSpec
    with RepoNamespaceRepositorySupport
    with AdminResources
    with RepositorySpec
    with Generators
    with ProvisionedDevicesRequests {

  def forceRoleExpire[T](ns: Namespace)(implicit tufRole: TufRole[T]): Unit = {
    val sql =
      sql"update admin_roles set expires_at = '1970-01-01 00:00:00' where repo_id = (select repo_id from repo_namespaces where namespace = '#${ns.get}') and role = '#${tufRole.roleType.toString}'"
    db.run(sql.asUpdate).futureValue
  }

  val GenOfflineUpdateRequest: Gen[OfflineUpdateRequest] = GenTarget.map { case (filename, t) =>
    OfflineUpdateRequest(Map(filename -> t), None)
  }

  testWithRepo("can add + retrieve an offline update") { implicit ns =>
    val req = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val resp = responseAs[SignedPayload[OfflineUpdatesRole]]

      resp.signed.targets shouldBe req.values
    }
  }

  testWithRepo("can add + retrieve an offline update by version") { implicit ns =>
    val req = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-updates/1.emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineUpdatesRole]]

      resp.signed.targets shouldBe req.values
      resp.signed.version shouldBe 1
    }
  }

  testWithRepo("can retrieve an offline snapshot by version") { implicit ns =>
    val req = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/1.offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      resp.signed.version shouldBe 1
    }
  }

  testWithRepo("adding a new target overwrites old targets") { implicit ns =>
    val req0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val req1 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req1).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val resp = responseAs[SignedPayload[OfflineUpdatesRole]]

      resp.signed.targets shouldBe req1.values
    }
  }

  testWithRepo("404 if offline targets do not exist") { implicit ns =>
    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.MissingAdminRole
    }
  }

  testWithRepo("offline-snapshot.json keeps old offline updates") { implicit ns =>
    val clientTarget0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), clientTarget0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri(s"admin/repo/offline-updates/au"), clientTarget0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]

      val metaPathEmea = Refined.unsafeApply[String, ValidMetaPath]("emea.json")
      val metaPathAu = Refined.unsafeApply[String, ValidMetaPath]("au.json")

      val metaItem = resp.signed.meta(metaPathEmea)
      metaItem.length should be > 0L

      val metaItemAu = resp.signed.meta(metaPathAu)
      metaItemAu.length should be > 0L
    }
  }

  testWithRepo("offline-snapshot.json is updated when targets change") { implicit ns =>
    val clientTarget0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), clientTarget0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val offlineUpdate =
      Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val resp = responseAs[SignedPayload[OfflineUpdatesRole]]
        resp.signed.targets shouldBe clientTarget0.values

        resp
      }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]

      val canonical = offlineUpdate.asJson.canonical
      val checksum = Sha256Digest.digest(canonical.getBytes)

      val metaPath = Refined.unsafeApply[String, ValidMetaPath]("emea.json")

      val metaItem = resp.signed.meta(metaPath)
      metaItem.length shouldBe canonical.length
      metaItem.version shouldBe offlineUpdate.signed.version
      metaItem.hashes.head._2 shouldBe checksum.hash
    }
  }

  testWithRepo("using an empty map deletes all targets") { implicit ns =>
    val req0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val emptyReq = OfflineUpdateRequest(Map.empty, None)

    Post(apiUri(s"admin/repo/offline-updates/emea"), emptyReq).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK

      val resp = responseAs[SignedPayload[OfflineUpdatesRole]]
      resp.signed.targets shouldBe empty
    }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      resp.signed.version shouldBe 2
    }
  }

  testWithRepo("device can get offline updates metadata") { implicit ns =>
    val deviceId = registerDeviceOk()

    val req0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(
      apiUri(s"device/${deviceId.show}/offline-updates/emea.json")
    ).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signedPayload = responseAs[SignedPayload[OfflineUpdatesRole]].signed
      signedPayload.targets shouldNot be(empty)
    }

    Get(apiUri(s"device/${deviceId.show}/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val signedPayload = responseAs[SignedPayload[OfflineSnapshotRole]].signed
      val metaPath = Refined.unsafeApply[String, ValidMetaPath]("emea.json")
      signedPayload.meta.get(metaPath) should be(defined)
    }
  }

  testWithRepo("offline-snapshot.json is refreshed if expires") { implicit ns =>
    val req = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val expiresAt = Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      resp.signed.version shouldBe 1
      resp.signed.expires
    }

    forceRoleExpire[OfflineSnapshotRole](ns)
    Thread.sleep(1000) // Needed because times are truncated to seconds in json

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      resp.signed.version shouldBe 2
      resp.signed.expires.isAfter(expiresAt) shouldBe true
    }
  }

  testWithRepo("Can set expire date when creating offline update") { implicit ns =>
    val expiresAt = Instant.now().plus(2 * 365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
    val req = GenOfflineUpdateRequest.generate.copy(expiresAt = expiresAt.some)

    Post(apiUri(s"admin/repo/offline-updates/emea"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      resp.signed.version shouldBe 1
      resp.signed.expires shouldBe expiresAt
    }
  }

  testWithRepo("Expire date is set to latest of offline updates expire dates") { implicit ns =>
    val expiresAt = Instant.now().plus(2 * 365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
    val req = GenOfflineUpdateRequest.generate.copy(expiresAt = expiresAt.some)

    Post(apiUri(s"admin/repo/offline-updates/emea"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val expiresAtAus = Instant.now().plus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
    val reqAus = GenOfflineUpdateRequest.generate.copy(expiresAt = expiresAtAus.some)

    Post(apiUri(s"admin/repo/offline-updates/aus"), reqAus).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      resp.signed.version shouldBe 2
      resp.signed.expires shouldBe expiresAt
    }
  }

  testWithRepo("returns 4xx when user has too many offline roles") { implicit ns =>
    val expiresAt = Instant.now().plus(1 * 365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)

    forAll(1 to 15) { idx =>
      val req = GenOfflineUpdateRequest.generate.copy(expiresAt = expiresAt.some)

      Post(apiUri(s"admin/repo/offline-updates/lockbox$idx"), req).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    val req = GenOfflineUpdateRequest.generate.copy(expiresAt = expiresAt.some)

    Post(apiUri(s"admin/repo/offline-updates/lockbox16"), req).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[ErrorRepresentation].code shouldBe ErrorCodes.TooManyOfflineRoles
    }
  }

  testWithRepo("DELETE removes the role from offline-snapshots.json") { implicit ns =>
    val clientTarget0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), clientTarget0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Delete(apiUri("admin/repo/offline-updates/emea")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      val metaPath = Refined.unsafeApply[String, ValidMetaPath]("emea.json")
      val metaItem = resp.signed.meta.get(metaPath)
      metaItem shouldBe empty
    }

  }

  testWithRepo("DELETE and POST creates the role with an increased version number") { implicit ns =>
    val clientTarget0 = GenOfflineUpdateRequest.generate

    Post(apiUri(s"admin/repo/offline-updates/emea"), clientTarget0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val previousRoleVersion =
      Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[SignedPayload[OfflineUpdatesRole]].signed.version
      }

    Delete(apiUri("admin/repo/offline-updates/emea")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri(s"admin/repo/offline-updates/emea"), clientTarget0).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri("admin/repo/offline-snapshot.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val resp = responseAs[SignedPayload[OfflineSnapshotRole]]
      val metaPath = Refined.unsafeApply[String, ValidMetaPath]("emea.json")
      val metaItem = resp.signed.meta.get(metaPath)
      metaItem should not be empty
    }

    Get(apiUri("admin/repo/offline-updates/emea.json")).namespaced ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val version = responseAs[SignedPayload[OfflineUpdatesRole]].signed.version
      version shouldBe previousRoleVersion + 1
    }
  }

}
