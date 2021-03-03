package com.advancedtelematic.tuf.keyserver.db

import java.security.PublicKey

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import com.advancedtelematic.libats.slick.codecs.SlickRefined._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, RSATufKey, TufKey}
import io.circe.syntax._
import org.slf4j.LoggerFactory
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SetParameter}

import scala.concurrent.{ExecutionContext, Future}


class KeysToJsonEncodedMigration(implicit
                                 val db: Database,
                                 val system: ActorSystem,
                                 val ec: ExecutionContext) {

  private val _log = LoggerFactory.getLogger(this.getClass)

  def backupKeys(implicit db: Database): Future[Done] = {
    val sql = sqlu"""create table `rsa_keys_pem` as select key_id, public_key from `keys` where key_type = 'RSA'"""
    db.run(sql).map(_ => Done)
  }

  def writeKey(keyId: KeyId, publicKey: PublicKey)(implicit db: Database): Future[TufKey] = {
    implicit val setRsaKey: SetParameter[TufKey] = (tufKey: TufKey, pp: PositionedParameters) => {
      pp.setString(tufKey.asJson.noSpaces)
    }

    implicit val setKeyId: SetParameter[KeyId] = (keyId: KeyId, pp: PositionedParameters) => {
      pp.setString(keyId.value)
    }

    val rsaKey = RSATufKey(publicKey)
    val sql = sqlu"""update `keys` set public_key = $rsaKey where key_id = $keyId"""

    db.run(sql).map(_ => rsaKey)
  }

  def existingKeys(implicit db: Database):  Source[(KeyId, PublicKey), NotUsed] = {
    implicit val getResult: GetResult[(KeyId, PublicKey)] = pr => {
      val keyId = implicitly[ColumnType[KeyId]].getValue(pr.rs, 1)
      val publicKeyStr = pr.rs.getString("public_key")
      keyId -> TufCrypto.parsePublicPem(publicKeyStr).get
    }

    val findQ = sql""" select key_id, public_key from `keys` where key_type = 'RSA'""".as[(KeyId, PublicKey)]

    Source.fromPublisher(db.stream(findQ))
  }

  def run: Future[Done] = {
    val source = existingKeys.mapAsync(1)((writeKey _).tupled)

    backupKeys.flatMap { _ =>
      source.runWith(Sink.foreach { key =>
        _log.info(s"Converted ${key.id} to new format")
      })
    }
  }
}

