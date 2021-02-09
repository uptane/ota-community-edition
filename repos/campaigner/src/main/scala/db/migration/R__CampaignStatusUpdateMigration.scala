package db.migration

import java.security.Security

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.advancedtelematic.campaigner.db.CampaignStatusRecalculate
import com.advancedtelematic.libats.slick.db.AppMigration
import org.bouncycastle.jce.provider.BouncyCastleProvider
import slick.jdbc.MySQLProfile.api._

class R__CampaignStatusUpdateMigration extends AppMigration  {
  Security.addProvider(new BouncyCastleProvider)

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  override def migrate(implicit db: Database) = new CampaignStatusRecalculate().run.map(_ => ())
}
