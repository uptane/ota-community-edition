package com.advancedtelematic.ota_lith

import java.security.Security
import akka.actor.ActorSystem
import com.advancedtelematic.api_provider.ApiProviderBoot
import com.advancedtelematic.campaigner.{CampaignerBoot, CampaignerDaemon}
import com.advancedtelematic.director.DirectorBoot
import com.advancedtelematic.director.daemon.DirectorDaemon
import com.advancedtelematic.ota.deviceregistry.{DeviceRegistryBoot, DeviceRegistryDaemon}
import com.advancedtelematic.treehub.TreehubBoot
import com.advancedtelematic.tuf.keyserver.KeyserverBoot
import com.advancedtelematic.tuf.keyserver.daemon.KeyserverDaemon
import com.advancedtelematic.tuf.reposerver.ReposerverBoot
import com.advancedtelematic.user_profile.{UserProfileBoot, UserProfileDaemonBoot}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider

object OtaLithBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  val keyserverDbConfig = appConfig.getConfig("ats.keyserver.database")
  val keyserverBind = new KeyserverBoot(appConfig, keyserverDbConfig, new MetricRegistry)(ActorSystem("keyserver-actor-system")).bind()

  val reposerverDbConfig = appConfig.getConfig("ats.reposerver.database")
  val reposerverBind = new ReposerverBoot(appConfig, reposerverDbConfig, new MetricRegistry)(ActorSystem("reposerver-actor-system")).bind()

  val directorDbConfig = appConfig.getConfig("ats.director.database")
  val directorBind = new DirectorBoot(appConfig, reposerverDbConfig, new MetricRegistry)(ActorSystem("director-actor-system")).bind()

  val treehubDbConfig = appConfig.getConfig("ats.treehub.database")
  val treehubBind = new TreehubBoot(appConfig, treehubDbConfig, new MetricRegistry)(ActorSystem("treehub-actor-system")).bind()

  val deviceRegistryDbConfig = appConfig.getConfig("ats.deviceregistry.database")
  val deviceRegistryBind = new DeviceRegistryBoot(appConfig, deviceRegistryDbConfig, new MetricRegistry)(ActorSystem("deviceregistry-actor-system")).bind()

  val campaignerDbConfig = appConfig.getConfig("ats.campaigner.database")
  val campaignerBind = new CampaignerBoot(appConfig, campaignerDbConfig, new MetricRegistry)(ActorSystem("campaigner-actor-system")).bind()

  val userprofileDbConfig = appConfig.getConfig("ats.user_profile.database")
  val userprofileBind = new UserProfileBoot(appConfig, userprofileDbConfig, new MetricRegistry)(ActorSystem("userprofile-actor-system")).bind()

  val apiproviderDbConfig = appConfig.getConfig("ats.user_profile.database")
  val apiproviderBind = new ApiProviderBoot(appConfig, apiproviderDbConfig, new MetricRegistry)(ActorSystem("apiprovider-actor-system")).bind()
}

object OtaLithDaemonBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  val keyserverDbConfig = appConfig.getConfig("ats.keyserver.database")
  val keyserverDaemonBind = new KeyserverDaemon(appConfig, keyserverDbConfig, new MetricRegistry)(ActorSystem("keyserver-actor-system"))

  val directorDbConfig = appConfig.getConfig("ats.director.database")
  val directorDaemonBind = new DirectorDaemon(appConfig, directorDbConfig, new MetricRegistry)(ActorSystem("director-actor-system"))

  val deviceRegistryDbConfig = appConfig.getConfig("ats.deviceregistry.database")
  val deviceRegistryDaemonBind = new DeviceRegistryDaemon(appConfig, deviceRegistryDbConfig, new MetricRegistry)(ActorSystem("deviceregistry-actor-system"))

  val campaignerDbConfig = appConfig.getConfig("ats.campaigner.database")
  val campaignerDaemonBind = new CampaignerDaemon(appConfig, campaignerDbConfig, new MetricRegistry)(ActorSystem("campaigner-actor-system"))

  val userprofileDbConfig = appConfig.getConfig("ats.user_profile.database")
  val userprofileDaemonBind = new UserProfileDaemonBoot(appConfig, userprofileDbConfig, new MetricRegistry)(ActorSystem("userprofile-actor-system"))
}
