package com.advancedtelematic.director.daemon

import org.apache.pekko.http.scaladsl.server.Directives
import com.advancedtelematic.director.db.SignedRoleMigration
import com.advancedtelematic.director.{Settings, VersionInfo}
import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.slick.db.DatabaseSupport

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object SignedRolesMigrationBoot
    extends BootApp
    with BootAppDefaultConfig
    with BootAppDatabaseConfig
    with Directives
    with Settings
    with VersionInfo
    with DatabaseSupport {

  implicit val _db: slick.jdbc.MySQLProfile.backend.Database = db

  val migrationF = new SignedRoleMigration().run.map { res =>
    log.info(s"Migration finished $res")
  }

  Await.result(migrationF, Duration.Inf)
}
