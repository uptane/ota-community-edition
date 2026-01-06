package com.advancedtelematic.director.db

import java.util.UUID
import akka.actor.ActorSystem
import com.advancedtelematic.director.util.DirectorSpec
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.test.MysqlDatabaseSpec
import com.advancedtelematic.libtuf.data.ClientDataType.SnapshotRole

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

class SignedRoleMigrationSpec extends DirectorSpec with MysqlDatabaseSpec {

  implicit val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  implicit val ec: scala.concurrent.ExecutionContext = ExecutionContext.Implicits.global

  val subject = new SignedRoleMigration("director1_test")

  val dbSignedRoleRepository = new DbDeviceRoleRepository()

  test("signed roles are migrated") {
    val deviceId = DeviceId(UUID.fromString("00000095-1454-40a5-b54e-caeb117f7aab"))

    async {
      await(subject.run)
      val signedRole = await(dbSignedRoleRepository.findLatest[SnapshotRole](deviceId))
      assert(signedRole.device == deviceId)
    }
  }

}
