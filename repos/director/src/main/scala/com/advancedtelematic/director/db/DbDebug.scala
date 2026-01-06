package com.advancedtelematic.director.db

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.ExecutionContext

class DirectorDbDebug()(implicit val db: Database, ec: ExecutionContext) {

  import com.advancedtelematic.libats.debug.DebugDatatype.*
  import com.advancedtelematic.libats.debug.db.DbDebug.*

  lazy val namespace_resources = ResourceGroup[Namespace](
    "namespaces",
    "namespace",
    List(
      TableResource(
        "provisioned_devices",
        readTable(
          "provisioned_devices",
          ns =>
            sql"""select * from provisioned_devices where namespace = $ns order by created_at DESC LIMIT #$DEFAULT_LIMIT"""
        )
      ),
      TableResource(
        "admin_roles",
        readTable(
          "admin_roles",
          ns =>
            sql"""select a.* from admin_roles a join repo_namespaces r using(repo_id) where r.namespace = $ns  order by `role` ASC, version desc"""
        )
      ),
      TableResource(
        "hardware_updates",
        readTable(
          "hardware_updates",
          ns =>
            sql"""select * from hardware_updates where namespace = $ns order by created_at ASC LIMIT #$DEFAULT_LIMIT"""
        )
      ),
      TableResource(
        "ecu_targets",
        readTable(
          "ecu_targets",
          ns =>
            sql"""select * from ecu_targets where namespace = $ns order by created_at ASC LIMIT #$DEFAULT_LIMIT"""
        )
      ),
      TableResource(
        "repo_namespaces",
        readTable(
          "repo_namespaces",
          ns => sql"""select * from repo_namespaces where namespace = $ns"""
        )
      ),
      TableResource(
        "dr-devices",
        readTable(
          "Device",
          ns =>
            sql"""select * from Device where namespace = $ns order by created_at DESC LIMIT #$DEFAULT_LIMIT"""
        )
      )
    )
  )

  lazy val device_resources = ResourceGroup[DeviceId](
    "devices",
    "device-id",
    List(
      TableResource(
        "provisioned_devices",
        readTable("devices", id => sql"""select * from provisioned_devices where id = $id""")
      ),
      TableResource(
        "ecus",
        readTable("ecus", id => sql"""select * from ecus where device_id = $id""")
      ),
      TableResource(
        "device_roles",
        readTable(
          "device_roles",
          id =>
            sql"""select * from device_roles where device_id = $id order by `role` ASC, version desc"""
        )
      ),
      TableResource(
        "admin_roles",
        readTable(
          "admin_roles",
          id =>
            sql"""select a.* from admin_roles a join repo_namespaces r using(repo_id) join provisioned_devices d using (namespace) where d.id = $id  order by `role` ASC, version desc"""
        )
      ),
      TableResource(
        "assignments",
        readTable(
          "assignments",
          id =>
            sql"select a.* from assignments a join provisioned_devices d on d.primary_ecu_id = a.ecu_serial where d.id = $id"
        )
      ),
      TableResource(
        "processed_assignments",
        readTable(
          "processed_assignments",
          id =>
            sql"select a.* from processed_assignments a join provisioned_devices d on d.primary_ecu_id = a.ecu_serial where d.id = $id"
        )
      ),
      TableResource(
        "device_type",
        readTable("DeviceType", id => sql"""select * from DeviceType where id = $id""")
      ),
      TableResource(
        "scheduled_updates",
        readTable(
          "scheduled_updates",
          id => sql"""select * from scheduled_updates where device_id = $id"""
        )
      )
    )
  )

}
