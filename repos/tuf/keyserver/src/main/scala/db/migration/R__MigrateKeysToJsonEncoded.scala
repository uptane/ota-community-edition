package db.migration

import java.security.Security

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.advancedtelematic.libats.slick.db.AppMigration
import com.advancedtelematic.tuf.keyserver.db.KeysToJsonEncodedMigration
import org.bouncycastle.jce.provider.BouncyCastleProvider
import slick.jdbc.MySQLProfile.api._

class R__MigrateKeysToJsonEncoded extends AppMigration  {
  Security.addProvider(new BouncyCastleProvider)

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  import system.dispatcher

  override def migrate(implicit db: Database) = new KeysToJsonEncodedMigration().run.map(_ => ())
}


