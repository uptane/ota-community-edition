package com.advancedtelematic.campaigner.util

import java.util.concurrent.ConcurrentHashMap

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.campaigner.client.{DeviceRegistryClient, DirectorClient, ExternalUpdate, ResolverClient, UserProfileClient}
import com.advancedtelematic.campaigner.data.DataType._
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace}
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import org.scalacheck.Gen

import scala.collection.JavaConverters._
import scala.concurrent.Future

class FakeDirectorClient extends DirectorClient with CampaignerSpecUtil {

  val updates = new ConcurrentHashMap[ExternalUpdateId, Set[DeviceId]]()
  val affected = new ConcurrentHashMap[ExternalUpdateId, Set[DeviceId]]()
  val cancelled = ConcurrentHashMap.newKeySet[DeviceId]()

  override def setMultiUpdateTarget(namespace: Namespace,
                                    update: ExternalUpdateId,
                                    devices: Seq[DeviceId],
                                    correlationId: CorrelationId): Future[Seq[DeviceId]] = {
    val affected = devices.filterNot(cancelled.asScala.contains)

    updates.compute(update, (_, existing) => {
      if(existing != null)
        existing ++ affected
      else
        devices.toSet
    })

    FastFuture.successful(affected)
  }

  override def cancelUpdate(
    ns: Namespace,
    devices: Seq[DeviceId]): Future[Seq[DeviceId]] = {
    val devs = Gen.someOf(devices).generate
    cancelled.addAll(devs.asJava)
    FastFuture.successful(devs)
  }

  override def cancelUpdate(
    ns: Namespace,
    device: DeviceId): Future[Unit] = {
    cancelled.add(device)
    FastFuture.successful(())
  }

  override def findAffected(ns: Namespace, updateId: ExternalUpdateId, devices: Seq[DeviceId]): Future[Seq[DeviceId]] = {
    FastFuture.successful(affected.asScala.get(updateId).toSeq.flatten)
  }
}

class FakeDeviceRegistry extends DeviceRegistryClient {

  private val groups = new ConcurrentHashMap[GroupId, Set[DeviceId]]()
  private val oemIds = new ConcurrentHashMap[DeviceId, String]()

  def clear(): Unit = groups.clear()

  def setGroup(groupId: GroupId, devices: Seq[DeviceId]): Unit =
    groups.put(groupId, devices.toSet)

  def setOemIds(pairs: (DeviceId, String)*): Unit =
    pairs.foreach { case (k, v) =>
      oemIds.put(k, v)
    }

  def allGroups(): Set[GroupId] = {
    groups.keys().asScala.toSet
  }

  def allGroupDevices(groupId: GroupId): Vector[DeviceId] =
    groups.getOrDefault(groupId, Set.empty).toVector.sortBy(_.uuid)

  override def devicesInGroup(namespace: Namespace, groupId: GroupId, offset: Long, limit: Long): Future[Seq[DeviceId]] = Future.successful {
    allGroupDevices(groupId).slice(offset.toInt, (offset + limit).toInt)
  }

  override def fetchOemId(ns: Namespace, deviceId: DeviceId): Future[String] = Future.successful {
    oemIds.getOrDefault(deviceId, "OEM-ID-NOT-FOUND")
  }
}

class SlowFakeDeviceRegistry extends FakeDeviceRegistry {
  override def devicesInGroup(namespace: Namespace, groupId: GroupId, offset: Long, limit: Long): Future[Seq[DeviceId]] = {
    Thread.sleep(11000L)
    super.devicesInGroup(namespace, groupId, offset, limit)
  }

  override def fetchOemId(ns: Namespace, deviceId: DeviceId): Future[String] = {
    Thread.sleep(11000L)
    super.fetchOemId(ns, deviceId)
  }
}

class FakeResolverClient extends ResolverClient {
  val updates = new ConcurrentHashMap[Uri, ConcurrentHashMap[DeviceId, Seq[ExternalUpdateId]]]()

  def setUpdates(resolverUri: Uri, devices: Seq[DeviceId], externalUpdates: Seq[ExternalUpdateId]): Unit = {
    updates.putIfAbsent(resolverUri, new ConcurrentHashMap[DeviceId, Seq[ExternalUpdateId]]())
    updates.get(resolverUri).putAll(devices.map(_ -> externalUpdates).toMap.asJava)
  }

  override def availableUpdatesFor(resolverUri: Uri, ns: Namespace, devices: Set[DeviceId]): Future[Seq[ExternalUpdateId]] = FastFuture.successful {
    val deviceSet = devices

    val set =
      if (updates.containsKey(resolverUri)) {
        updates.get(resolverUri).asScala.foldLeft(Set.empty[ExternalUpdateId]) { case (acc, (deviceId, u)) =>
          if (deviceSet.contains(deviceId)) acc ++ u.toSet else acc
        }
      } else {
        updates.values().asScala.foldLeft(Set.empty[ExternalUpdateId]) { case (acc, map) =>
          acc ++ map.values().asScala.flatten
        }
    }

    set.toSeq
  }

  override def updatesForDevice(resolverUri: Uri, ns: Namespace, deviceId: DeviceId): Future[List[ExternalUpdate]] = ???
}

class FakeUserProfileClient extends UserProfileClient {
  val namespaceSettings = new ConcurrentHashMap[Namespace, Uri]()

  def setNamespaceSetting(ns : Namespace, uri: Uri): Uri = namespaceSettings.put(ns, uri)

  override def externalResolverUri(ns: Namespace): Future[Option[Uri]] = FastFuture.successful {
    if (namespaceSettings.containsKey(ns)) Some(namespaceSettings.get(ns)) else None
  }
}
