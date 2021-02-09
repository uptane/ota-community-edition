package com.advancedtelematic.tuf.cli

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import PosixFilePermission._

import com.advancedtelematic.tuf.cli.DataType.KeyName
import com.advancedtelematic.tuf.cli.repo.CliKeyStorage
import com.advancedtelematic.tuf.cli.util.{CliSpec, KeyTypeSpecSupport}

import scala.collection.JavaConverters._

class CliKeyStorageSpec extends CliSpec with KeyTypeSpecSupport  {
  val tempDir = Files.createTempDirectory("tuf-keys")

  lazy val subject = CliKeyStorage.forRepo(tempDir)

  keyTypeTest("generates a key ") { keyType =>
    val keyName = KeyName("test-key")
    subject.genKeys(keyName, keyType)

    val (pub, priv) = subject.readKeyPair(keyName).get

    pub.keytype shouldBe keyType
    priv.keytype shouldBe keyType
  }

  keyTypeTest("writes keys with limited permissions ") { keyType =>
    val keyName = KeyName("test-key-02")
    subject.genKeys(keyName, keyType)

    val perms = Files.getPosixFilePermissions(tempDir.resolve("keys").resolve(keyName.value + ".sec"))
    perms.asScala shouldBe Set(OWNER_READ, OWNER_WRITE)
  }

  keyTypeTest("creates key directory with limited permissions " ) { keyType =>
    val keyName = KeyName("test-key-02")
    subject.genKeys(keyName, keyType)

    val perms = Files.getPosixFilePermissions(tempDir.resolve("keys"))
    perms.asScala shouldBe Set(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)
  }
}
