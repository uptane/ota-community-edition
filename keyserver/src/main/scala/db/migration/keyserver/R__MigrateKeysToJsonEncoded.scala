package db.migration.keyserver

import org.apache.pekko.actor.ActorSystem
import com.advancedtelematic.libats.slick.db.AppMigration
import com.advancedtelematic.tuf.keyserver.db.KeysToJsonEncodedMigration
import org.bouncycastle.jce.provider.BouncyCastleProvider
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.JdbcBackend.Database

import java.security.Security

class R__MigrateKeysToJsonEncoded extends AppMigration {
  Security.addProvider(new BouncyCastleProvider)

  implicit val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)
  import system.dispatcher

  override def migrate(implicit db: Database) = new KeysToJsonEncodedMigration().run.map(_ => ())
}
