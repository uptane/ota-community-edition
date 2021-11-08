package com.advancedtelematic.ota

import java.security.Security

import akka.actor.ActorSystem
import com.advancedtelematic.director.DirectorBoot
import com.advancedtelematic.director.daemon.DirectorDaemon
import com.advancedtelematic.libats.http.BootApp3
import com.advancedtelematic.tuf.keyserver.KeyserverBoot
import com.advancedtelematic.tuf.keyserver.daemon.KeyserverDaemon
import com.advancedtelematic.tuf.reposerver.ReposerverBoot
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider

object OtaLithBoot extends App {

  private lazy val globalConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  val keyserverDbConfig = globalConfig.getConfig("ats.keyserver.database")
  val keyserverBind = new KeyserverBoot(globalConfig, keyserverDbConfig, new MetricRegistry)(ActorSystem("keyserver-actor-system")).bind()

  val reposerverDbConfig = globalConfig.getConfig("ats.reposerver.database")
  val reposerverBind = new ReposerverBoot(globalConfig, reposerverDbConfig, new MetricRegistry)(ActorSystem("reposerver-actor-system")).bind()

  val directorDbConfig = globalConfig.getConfig("ats.director.database")
  val directorBind = new DirectorBoot(globalConfig, reposerverDbConfig, new MetricRegistry)(ActorSystem("director-actor-system")).bind()
}

object OtaLithDaemonBoot extends BootApp3 {
  override val projectName: String = "ota-lith"

  Security.addProvider(new BouncyCastleProvider)

  val keyserverDbConfig = globalConfig.getConfig("ats.keyserver.database")
  val keyserverDaemonBind = new KeyserverDaemon(globalConfig, keyserverDbConfig, new MetricRegistry)

  val directorDbConfig = globalConfig.getConfig("ats.director.database")
  val directorDaemonBind = new DirectorDaemon(globalConfig, directorDbConfig, new MetricRegistry)
}
