package com.advancedtelematic.ota.deviceregistry.daemon

import com.advancedtelematic.libats.http.{BootAppDatabaseConfig, BootAppDefaultConfig}
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.DatabaseSupport

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// needs to run once to inform the director which devices are deleted
object DeleteDevicePublisherBoot extends DatabaseSupport with BootAppDefaultConfig with BootAppDatabaseConfig {
  lazy val projectName: String = buildinfo.BuildInfo.name

  lazy val messageBus = MessageBus.publisher(system, globalConfig)

  val publishingF = new DeletedDevicePublisher(messageBus).run.map { res =>
    log.info(s"Migration finished $res")
  }

  Await.result(publishingF, Duration.Inf)
}
