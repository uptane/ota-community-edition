package com.advancedtelematic.tuf.keyserver.db

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKitBase
import com.advancedtelematic.libats.data.RefinedUtils._
import com.advancedtelematic.libats.test.{DatabaseSpec, LongTest}
import com.advancedtelematic.libtuf.data.TufDataType.{RSATufKey, ValidKeyId}
import com.advancedtelematic.tuf.util.TufKeyserverSpec
import org.scalatest.concurrent.PatienceConfiguration
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext

class KeysToJsonEncodedMigrationSpec extends TufKeyserverSpec with TestKitBase with DatabaseSpec with PatienceConfiguration with LongTest
  with KeyRepositorySupport {

  override implicit lazy val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  implicit val ec = ExecutionContext.Implicits.global

  val migration = new KeysToJsonEncodedMigration()

  def runFreshMigration = {
    db.run(sqlu"drop table rsa_keys_pem").futureValue
    migration.run.futureValue
  }

  test("updates a key from old encoding to new encoding") {
    val keyId = "fdd99c4f6447e10d6d5373d80622d4e26b227e67a22b2b3963914b8ed75f7555".refineTry[ValidKeyId].get

    val sql =
      sqlu"""insert into `keys` (key_id, repo_id, role_type, key_type, public_key, private_key) VALUES (
            '#${keyId.value}',
            '16306570-f8e3-477d-8028-1f06b4d4ab96',
            'TARGETS',
            'RSA',
            '-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAq0lf5LYsxLhayQbPaG+w\n+3fzYL4eKQxUZbSk20O+kc3afErfdkXqN/8+Qq4ua3x0mO5VXNiAArq/IZnr3Z8W\nQmjSVnZprVNx5ZsxFCwSfs0nK67rYch7F9vtyCczdpJW15Jcoh9aowzfUbOla6JK\nh76gAhjzbnJpGxZLqBXxM6VxsXrmzFjjsctE54q9wkJrK25tpDDg8qXlv2DMOLgY\nKrMkXhURhIZF24iTx33st+R8dl6jo0qpmp7y0X0ZxXo2kBM87y+d4uCeJM1gfoED\nt4eyUHipGMnT1Kx4xRQqDdCc07+bQJmlQPXifoO8biIH1bjslHpeLVcWrORpeoQM\n7wIDAQAB\n-----END PUBLIC KEY-----\n',
            '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'
            );"""

    db.run(sql).futureValue

    runFreshMigration

    keyRepo.find(keyId).futureValue.publicKey shouldBe a[RSATufKey]
  }
}
