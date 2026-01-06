package com.advancedtelematic.libtuf.crypt

import com.advancedtelematic.libtuf.data.TufDataType.{KeyId, TufKey}

object KeyListToKeyMapOps {

  implicit class KeyListToKeyMapConversion(value: List[TufKey]) {
    def toKeyMap: Map[KeyId, TufKey] = value.map(k => k.id -> k).toMap
  }

}
