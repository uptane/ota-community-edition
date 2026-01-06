package com.advancedtelematic.tuf.keyserver.db

import scala.async.Async._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import com.advancedtelematic.libats.http.{BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.slick.codecs.SlickRefined._
import com.advancedtelematic.libats.slick.db.SlickCrypto
import com.advancedtelematic.libtuf.data.TufDataType.KeyId
import com.advancedtelematic.tuf.keyserver.VersionInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import slick.jdbc.GetResult
import slick.jdbc.MySQLProfile.api._
import com.advancedtelematic.libats.slick.db.{DatabaseSupport => LibatsDbSupport}

import java.security.Security
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class ChangeEncryptionKey(
  implicit val db: Database,
  val system: ActorSystem,
  val ec: ExecutionContext) {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  type Converter = String => String

  def decrypt(salt: String, pass: String)(payload: String): String =
    SlickCrypto(salt, pass).decrypt(payload)

  def encrypt(salt: String, pass: String)(payload: String): String =
    SlickCrypto(salt, pass).encrypt(payload)

  def updateKey(keyId: KeyId, oldPayload: String, payload: String): Future[(KeyId, Boolean)] = {
    val backup =
      sql"""insert into `key_enc_migration` (key_id, old_payload) values (${keyId.value}, $oldPayload)""".asUpdate
    val update =
      sql"""update `keys` set private_key = $payload where key_id = ${keyId.value}""".asUpdate
    db.run((backup >> update).map(i => (keyId, i == 1)).transactionally)
  }

  private def Error(msg: String) = new IllegalArgumentException(msg)

  def run(): Future[Unit] = async {
    val oldSalt = sys.env.getOrElse("OLD_SALT", throw Error("OLD_SALT must be set"))
    val newSalt = sys.env.getOrElse("NEW_SALT", throw Error("NEW_SALT must be set"))

    val oldPass = sys.env.getOrElse("OLD_PASS", throw Error("OLD_PASS must be set"))
    val newPass = sys.env.getOrElse("NEW_PASS", throw Error("NEW_pASS must be set"))

    val decryptOld = decrypt(oldSalt, oldPass)(_)
    val encryptNew = encrypt(newSalt, newPass)(_)

    val stateTable =
      sql"""create table if not exists key_enc_migration(
            key_id varchar(254) not null,
            old_payload text,
            created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
            primary key (`key_id`)) DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci""".asUpdate

    await(db.run(stateTable))

    implicit val getKeyId = GetResult[(KeyId, String)] { pr =>
      val keyId = implicitly[ColumnType[KeyId]].getValue(pr.rs, 1)
      val payload = pr.rs.getString("private_key")
      keyId -> payload
    }

    val stream_sql =
      sql"""select key_id, private_key from `keys` where key_id not in (select key_id from key_enc_migration)"""
        .as[(KeyId, String)]

    val source = Source.fromPublisher(db.stream(stream_sql))

    val pipeline = source
      .map { case (keyId, oldPayload) =>
        log.info(s"Processing $keyId")
        (keyId, oldPayload, encryptNew(decryptOld(oldPayload)))
      }
      .mapAsync(10)((updateKey _).tupled)
      .runForeach {
        case (keyId, true) =>
          log.info(s"Processed key ${keyId.value} successfully")
        case (keyId, false) =>
          log.error(s"Error: Could not process key ${keyId.value}. Continuing")
      }

    await(pipeline)
  }

}

object ChangeEncryptionKey
    extends BootAppDefaultConfig
    with BootAppDatabaseConfig
    with VersionInfo
    with LibatsDbSupport {
  Security.addProvider(new BouncyCastleProvider)

  def main(args: Array[String]): Unit = {
    val f = new ChangeEncryptionKey().run().flatMap { _ =>
      system.terminate()
    }
    Await.result(f, Duration.Inf)
  }

}
