package com.advancedtelematic.director.daemon

import akka.http.scaladsl.server.Directives
import com.advancedtelematic.director.db.SignedRoleMigration
import com.advancedtelematic.director.{Settings, VersionInfo}
import com.advancedtelematic.libats.http.BootAppDefaultConfig
import com.advancedtelematic.libats.slick.db.DatabaseSupport

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object SignedRolesMigrationBoot extends BootAppDefaultConfig
  with Directives
  with Settings
  with VersionInfo
  with DatabaseSupport {

  override val dbConfig = appConfig.getConfig("ats.director.database")

  val migrationF = new SignedRoleMigration().run.map { res =>
    log.info(s"Migration finished $res")
  }

  Await.result(migrationF, Duration.Inf)
}
