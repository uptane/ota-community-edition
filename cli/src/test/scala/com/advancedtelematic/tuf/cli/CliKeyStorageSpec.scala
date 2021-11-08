package com.advancedtelematic.tuf.cli

import com.advancedtelematic.libtuf.data.TufDataType.{EcPrime256KeyType, Ed25519KeyType, RsaKeyType}

import java.nio.file.{Files, Paths}
import java.nio.file.attribute.PosixFilePermission
import PosixFilePermission._
import com.advancedtelematic.tuf.cli.DataType.KeyName
import com.advancedtelematic.tuf.cli.repo.CliKeyStorage
import com.advancedtelematic.tuf.cli.util.{CliSpec, KeyTypeSpecSupport}
import org.scalatest.TryValues

import scala.collection.JavaConverters._

class CliKeyStorageSpec extends CliSpec with KeyTypeSpecSupport with TryValues {
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

  test("import RSA public key should work") {
    val rsaPublicKeyPem = Paths.get(this.getClass.getResource("/pem-files/valid-public-RSA.pem").toURI)
    val keyName = KeyName("test-rsa-key")

    subject.readPublicKey(keyName).failure.exception.getMessage should include ("No such file or directory")
    subject.importPublicKey(rsaPublicKeyPem, List(keyName)).isSuccess shouldBe true
    subject.readPublicKey(keyName).success.value.keytype shouldBe RsaKeyType
  }

  test("import Ed25519 public key should work") {
    val ed25519PublicKeyPem = Paths.get(this.getClass.getResource("/pem-files/valid-public-Ed25519.pem").toURI)
    val keyName = KeyName("test-Ed25519-key")

    subject.readPublicKey(keyName).failure.exception.getMessage should include ("No such file or directory")
    subject.importPublicKey(ed25519PublicKeyPem, List(keyName)).isSuccess shouldBe true
    subject.readPublicKey(keyName).success.value.keytype shouldBe Ed25519KeyType
  }

  test("import EcPrime256 public key should work") {
    val ecPrime256PublicKeyPem = Paths.get(this.getClass.getResource("/pem-files/valid-public-EcPrime256.pem").toURI)
    val keyName = KeyName("test-EcPrime256-key")

    subject.readPublicKey(keyName).failure.exception.getMessage should include ("No such file or directory")
    subject.importPublicKey(ecPrime256PublicKeyPem, List(keyName)).isSuccess shouldBe true
    subject.readPublicKey(keyName).success.value.keytype shouldBe EcPrime256KeyType
  }

  test("import invalid public key should fail") {
    val rsaPublicKeyPem = Paths.get(this.getClass.getResource("/pem-files/invalid-public-RSA.pem").toURI)
    val keyName = KeyName("test-invalid-rsa-key")

    subject.readPublicKey(keyName).failure.exception.getMessage should include ("No such file or directory")
    subject.importPublicKey(rsaPublicKeyPem, List(keyName)).failure.exception.getMessage should include (s"Cannot parse public key from $rsaPublicKeyPem")
  }
}
