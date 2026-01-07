package com.advancedtelematic.ota_lith

import org.apache.pekko.actor.ActorSystem
import com.advancedtelematic.director.DirectorBoot
import com.advancedtelematic.director.daemon.DirectorDaemonBoot
import com.advancedtelematic.treehub.TreehubBoot
import com.advancedtelematic.tuf.keyserver.KeyserverBoot
import com.advancedtelematic.tuf.reposerver.ReposerverBoot
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.security.Security
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object OtaLithBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  implicit val reposerverSystem: ActorSystem = ActorSystem("reposerver-actor-system")
  val reposerverDbConfig = appConfig.getConfig("ats.reposerver.database")
  val reposerverBind = new ReposerverBoot(appConfig, reposerverDbConfig, new MetricRegistry)(reposerverSystem).bind()

  implicit val keyserverSystem: ActorSystem = ActorSystem("keyserver-actor-system")
  val keyserverDbConfig = appConfig.getConfig("ats.keyserver.database")
  val keyserverBind = new KeyserverBoot(appConfig, keyserverDbConfig, new MetricRegistry)(keyserverSystem).bind()

  implicit val directorSystem: ActorSystem = ActorSystem("director-actor-system")
  val directorDbConfig = appConfig.getConfig("ats.director-v2.database")
  val directorBind = new DirectorBoot(appConfig, directorDbConfig, new MetricRegistry)(directorSystem).bind()

  implicit val treehubSystem: ActorSystem = ActorSystem("treehub-actor-system")
  val treehubDbConfig = appConfig.getConfig("ats.treehub.database")
  val treehubBind = new TreehubBoot(appConfig, treehubDbConfig, new MetricRegistry)(treehubSystem).bind()

  // Wait for all services to bind and keep the application running
  implicit val ec: scala.concurrent.ExecutionContext = reposerverSystem.dispatcher
  val allBindings = Future.sequence(List(reposerverBind, keyserverBind, directorBind, treehubBind))
  
  // Wait for all bindings to complete
  Await.ready(allBindings, Duration.Inf)
  
  // Keep the application running by waiting for any ActorSystem to terminate
  // (which should never happen unless explicitly shut down)
  Await.result(reposerverSystem.whenTerminated, Duration.Inf)
}

object OtaLithDaemonBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  implicit val directorSystem: ActorSystem = ActorSystem("director-actor-system")
  val directorDbConfig = appConfig.getConfig("ats.director-v2.database")
  val directorDaemonBind = new DirectorDaemonBoot(appConfig, directorDbConfig, new MetricRegistry)(directorSystem).bind()

  // Wait for the daemon to bind and keep the application running
  implicit val ec: scala.concurrent.ExecutionContext = directorSystem.dispatcher
  
  // Wait for binding to complete
  Await.ready(directorDaemonBind, Duration.Inf)
  
  // Keep the application running by waiting for the ActorSystem to terminate
  // (which should never happen unless explicitly shut down)
  Await.result(directorSystem.whenTerminated, Duration.Inf)
}

object OtaLithCombinedBoot extends App {
  private lazy val appConfig = ConfigFactory.load()

  Security.addProvider(new BouncyCastleProvider)

  // Start all HTTP API services
  implicit val reposerverSystem: ActorSystem = ActorSystem("reposerver-actor-system")
  val reposerverDbConfig = appConfig.getConfig("ats.reposerver.database")
  val reposerverBind = new ReposerverBoot(appConfig, reposerverDbConfig, new MetricRegistry)(reposerverSystem).bind()

  implicit val keyserverSystem: ActorSystem = ActorSystem("keyserver-actor-system")
  val keyserverDbConfig = appConfig.getConfig("ats.keyserver.database")
  val keyserverBind = new KeyserverBoot(appConfig, keyserverDbConfig, new MetricRegistry)(keyserverSystem).bind()

  implicit val directorSystem: ActorSystem = ActorSystem("director-actor-system")
  val directorDbConfig = appConfig.getConfig("ats.director-v2.database")
  val directorBind = new DirectorBoot(appConfig, directorDbConfig, new MetricRegistry)(directorSystem).bind()

  implicit val treehubSystem: ActorSystem = ActorSystem("treehub-actor-system")
  val treehubDbConfig = appConfig.getConfig("ats.treehub.database")
  val treehubBind = new TreehubBoot(appConfig, treehubDbConfig, new MetricRegistry)(treehubSystem).bind()

  // Start the director daemon (uses the same directorSystem and dbConfig)
  val directorDaemonBind = new DirectorDaemonBoot(appConfig, directorDbConfig, new MetricRegistry)(directorSystem).bind()

  // Wait for all services to bind and keep the application running
  implicit val ec: scala.concurrent.ExecutionContext = reposerverSystem.dispatcher
  val allBindings = Future.sequence(List(reposerverBind, keyserverBind, directorBind, treehubBind, directorDaemonBind))
  
  // Wait for all bindings to complete
  Await.ready(allBindings, Duration.Inf)
  
  // Keep the application running by waiting for any ActorSystem to terminate
  // (which should never happen unless explicitly shut down)
  Await.result(reposerverSystem.whenTerminated, Duration.Inf)
}
