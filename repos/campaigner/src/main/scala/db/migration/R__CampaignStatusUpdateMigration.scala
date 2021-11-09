package db.migration

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.advancedtelematic.campaigner.db.{CampaignStatusRecalculate, Repositories}
import com.advancedtelematic.libats.slick.db.AppMigration
import org.bouncycastle.jce.provider.BouncyCastleProvider
import slick.jdbc.MySQLProfile.api._

import java.security.Security

class R__CampaignStatusUpdateMigration extends AppMigration  {
  Security.addProvider(new BouncyCastleProvider)

  implicit val system = ActorSystem(this.getClass.getSimpleName)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  override def migrate(implicit db: Database) = new CampaignStatusRecalculate(Repositories()).run.map(_ => ())
}
