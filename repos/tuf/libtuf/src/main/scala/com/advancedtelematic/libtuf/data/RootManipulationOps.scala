package com.advancedtelematic.libtuf.data

import java.time.{Duration, Instant, Period}

import com.advancedtelematic.libtuf.data.ClientDataType.{RoleKeys, RootRole}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, RoleType, TufKey}

object RootManipulationOps {
  implicit class RootManipulationOps(value: RootRole) {

    def roleKeys(roleTypes: RoleType*): List[TufKey] = {
      val keyids = value.roles.filterKeys(roleTypes.contains).values.map(_.keyids).toSet.flatten
      value.keys.filterKeys(keyids.contains).values.toList
    }

    def withVersion(version: Int): RootRole =
      value.copy(version = version)

    def addExpires(period: Period): RootRole =
      value.copy(expires = value.expires.plus(period))

    def addRoleKeys(roleType: RoleType, newKeys: TufKey*): RootRole = {
      val existingIds = value.roles(roleType).keyids.toSet
      val existingKeys = value.keys.filterKeys(existingIds.contains).values.toSeq
      withRoleKeys(roleType, existingKeys ++ newKeys:_*)
    }

    def removeRoleKeys(roleType: RoleType, keyIds: Set[KeyId]): RootRole = {
      val newRootKeys = value.roles(RoleType.ROOT).keyids.filterNot(keyIds.contains).toSet
      val newKeys = value.keys.filterKeys(newRootKeys.contains).values.toSeq
      withRoleKeys(roleType, newKeys:_*)
    }

    def withRoleKeys(roleType: RoleType, threshold: Int, keys: TufKey*): RootRole = {
      val newRoles = value.roles + (roleType -> RoleKeys(keys.map(_.id), threshold))

      val newKeys = newRoles.values.flatMap(_.keyids).toSet[KeyId].map { keyid =>
        value.keys.get(keyid).orElse(keys.find(_.id == keyid)).get
      }.map(k => k.id -> k).toMap

      value.copy(keys = newKeys, roles = newRoles)
    }

    def withRoleKeys(roleType: RoleType, keys: TufKey*): RootRole = {
      val oldRoleKeys = value.roles(roleType)
      withRoleKeys(roleType, oldRoleKeys.threshold, keys :_*)
    }
  }
}
