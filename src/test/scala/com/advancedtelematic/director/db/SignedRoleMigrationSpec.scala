package com.advancedtelematic.director.db

import java.util.UUID

import akka.actor.ActorSystem
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.test.DatabaseSpec
import com.advancedtelematic.libtuf.data.ClientDataType.SnapshotRole
import org.scalatest.AsyncFunSuite

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

class SignedRoleMigrationSpec extends AsyncFunSuite with DatabaseSpec {

  implicit val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  implicit val ec = ExecutionContext.Implicits.global

  val subject = new SignedRoleMigration("director1_test")

  val dbSignedRoleRepository = new DbSignedRoleRepository()

  test("signed roles are migrated") {
    val deviceId = DeviceId(UUID.fromString("00000095-1454-40a5-b54e-caeb117f7aab"))

    async {
      await(subject.run)
      val signedRole = await(dbSignedRoleRepository.findLatest[SnapshotRole](deviceId))
      assert(signedRole.device == deviceId)
    }
  }

}
