package com.advancedtelematic.ota.deviceregistry.daemon

import com.advancedtelematic.libats.http.{BootApp, BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.DatabaseSupport
import com.advancedtelematic.ota.deviceregistry.VersionInfo

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// needs to run once to inform the director which devices are deleted
object DeleteDevicePublisherBoot extends BootApp with BootAppDefaultConfig with BootAppDatabaseConfig with DatabaseSupport with VersionInfo {
  lazy val messageBus = MessageBus.publisher(system, appConfig)

  val publishingF = new DeletedDevicePublisher(messageBus).run.map { res =>
    log.info(s"Migration finished $res")
  }

  Await.result(publishingF, Duration.Inf)
}
