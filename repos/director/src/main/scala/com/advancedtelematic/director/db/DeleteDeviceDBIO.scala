package com.advancedtelematic.director.db

import com.advancedtelematic.director.db.deviceregistry.{
  EventJournal,
  GroupMemberRepository,
  PublicCredentialsRepository,
  SystemInfoRepository,
  TaggedDeviceRepository
}
import com.advancedtelematic.director.deviceregistry.data.DataType.DeletedDevice
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickAnyVal.*
import com.advancedtelematic.libats.slick.db.SlickUUIDKey.*
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext

object DeleteDeviceDBIO {

  // does NOT fail if devices do not exist in the tables
  def deleteDeviceIO(ns: Namespace, id: DeviceId)(implicit ec: ExecutionContext): DBIO[Unit] = {
    val devRegistryIO = for {
      _ <- EventJournal.archiveIndexedEvents(id)
      _ <- EventJournal.deleteEvents(id)
      _ <- GroupMemberRepository.removeDeviceFromAllGroups(id)
      _ <- PublicCredentialsRepository.delete(id)
      _ <- SystemInfoRepository.delete(id)
      _ <- TaggedDeviceRepository.delete(id)
      oemIdOpt <- deviceregistry.Schema.devices
        .filter(_.namespace === ns)
        .filter(_.id === id)
        .forUpdate
        .map(_.oemId)
        .result
        .headOption
      _ <- deviceregistry.Schema.devices
        .filter(_.namespace === ns)
        .filter(_.id === id)
        .delete
      _ <- oemIdOpt
        .map { oemId =>
          deviceregistry.Schema.deletedDevices += DeletedDevice(ns, id, oemId)
        }
        .getOrElse(DBIO.successful(()))
    } yield ()

    val directorIO =
      DBIO
        .seq(
          Schema.allProvisionedDevices
            .filter(d => d.namespace === ns && d.id === id)
            .map(_.deleted)
            .update(true),
          Schema.allEcus
            .filter(e => e.namespace === ns && e.deviceId === id)
            .map(_.deleted)
            .update(true)
        )

    val io = DBIO.seq(devRegistryIO, directorIO)

    io.transactionally
  }

}
