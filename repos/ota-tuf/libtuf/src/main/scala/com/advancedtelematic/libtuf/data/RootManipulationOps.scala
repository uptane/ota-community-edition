package com.advancedtelematic.libtuf.data

import java.time.Period

import com.advancedtelematic.libtuf.data.ClientDataType.{RoleKeys, RootRole}
import com.advancedtelematic.libtuf.data.TufDataType.RoleType.RoleType
import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, TufKey}

object RootManipulationOps {

  implicit class RootRoleExtension(val rootRole: RootRole) extends AnyVal {

    def roleKeys(roleTypes: RoleType*): List[TufKey] = {
      val keyids =
        rootRole.roles.view.filterKeys(roleTypes.contains).values.map(_.keyids).toSet.flatten
      rootRole.keys.view.filterKeys(keyids.contains).values.toList
    }

    def withVersion(version: Int): RootRole =
      rootRole.copy(version = version)

    def addExpires(period: Period): RootRole =
      rootRole.copy(expires = rootRole.expires.plus(period))

    def addRoleKeys(roleType: RoleType, newKeys: TufKey*): RootRole = {
      val existingIds = rootRole.roles(roleType).keyids.toSet
      val existingKeys = rootRole.keys.view.filterKeys(existingIds.contains).values.toSeq
      withRoleKeys(roleType, existingKeys ++ newKeys: _*)
    }

    def removeRoleKeys(roleType: RoleType, keyIds: Set[KeyId]): (RootRole, Int) = {
      val oldKeyIds = rootRole.roles(roleType).keyids
      val newKeyIds = oldKeyIds.filterNot(keyIds.contains).toSet
      val newKeys = rootRole.keys.view.filterKeys(newKeyIds.contains).values.toSeq
      (withRoleKeys(roleType, newKeys: _*), oldKeyIds.size - newKeyIds.size)
    }

    def withRoleKeys(roleType: RoleType, threshold: Int, keys: TufKey*): RootRole = {
      val newRoles = rootRole.roles + (roleType -> RoleKeys(keys.map(_.id).distinct, threshold))

      val newKeys = newRoles.values
        .flatMap(_.keyids)
        .toSet[KeyId]
        .map { keyid =>
          rootRole.keys.get(keyid).orElse(keys.find(_.id == keyid)).get
        }
        .map(k => k.id -> k)
        .toMap

      rootRole.copy(keys = newKeys, roles = newRoles)
    }

    def withRoleKeys(roleType: RoleType, keys: TufKey*): RootRole = {
      val oldRoleKeys = rootRole.roles(roleType)
      withRoleKeys(roleType, oldRoleKeys.threshold, keys: _*)
    }

  }

}
