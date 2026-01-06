package com.advancedtelematic.treehub.repo_metrics

import java.nio.file.{Files, Paths}
import akka.actor.ActorSystem
import akka.testkit.TestKitBase
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.treehub.object_store.{LocalFsBlobStore, ObjectStore}
import com.advancedtelematic.util.{DatabaseSpec, TreeHubSpec}
import com.typesafe.config.ConfigFactory

trait UsageUpdateSpec extends DatabaseSpec with ObjectRepositorySupport with TestKitBase {
  self: TreeHubSpec =>

  override implicit lazy val system: ActorSystem = ActorSystem(this.getClass.getSimpleName)

  import system.dispatcher

  lazy val localFsDir = Files.createTempDirectory(this.getClass.getSimpleName)

  lazy val namespaceDir = Files.createDirectories(Paths.get(s"${localFsDir.toAbsolutePath}/${defaultNs.get}"))

  lazy val objectStore = new ObjectStore(LocalFsBlobStore(localFsDir))

  lazy val messageBus = MessageBus.publisher(system, ConfigFactory.load())
}
