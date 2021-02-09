package com.advancedtelematic.tuf.cli.repo

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.nio.file.{FileAlreadyExistsException, Files, Path}
import PosixFilePermission._
import scala.collection.JavaConverters._
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, TufKey, TufKeyPair, TufPrivateKey}
import com.advancedtelematic.tuf.cli.DataType.KeyName
import org.slf4j.LoggerFactory

import scala.util.Try
import com.advancedtelematic.libtuf.data.TufCodecs._
import io.circe.syntax._
import io.circe.jawn._


object CliKeyStorage {
  def forUser(path: Path): CliKeyStorage = new CliKeyStorage(path)

  def forRepo(repoPath: Path): CliKeyStorage = new CliKeyStorage(repoPath.resolve("keys"))

  def readPrivateKey(path: Path): Try[TufPrivateKey] =
    parseFile(path.toFile).flatMap(_.as[TufPrivateKey]).toTry

  def readPublicKey(path: Path): Try[TufKey] = {
    parseFile(path.toFile).flatMap(_.as[TufKey]).toTry
  }
}

class CliKeyStorage private (root: Path) {
  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private lazy val SECRET_KEY_PERMISSIONS = Set(OWNER_READ, OWNER_WRITE)

  implicit private class KeyNamePath(v: KeyName) {
    def publicKeyPath: Path = root.resolve(v.publicKeyName)

    def privateKeyPath: Path = root.resolve(v.privateKeyName)
  }

  private def writePublic(keyName: KeyName, tufKey: TufKey): Try[Unit] = Try {
    Files.write(keyName.publicKeyPath, tufKey.asJson.spaces2.getBytes)
  }

  private def writePrivate(keyName: KeyName, tufKey: TufPrivateKey): Try[Unit] = Try {
    try Files.createFile(keyName.privateKeyPath, PosixFilePermissions.asFileAttribute(SECRET_KEY_PERMISSIONS.asJava))
    catch { case _: FileAlreadyExistsException => () }

    Files.write(keyName.privateKeyPath, tufKey.asJson.spaces2.getBytes)
  }

  def writeKeys(name: KeyName, pair: TufKeyPair): Try[Unit] =
    writeKeys(name, pair.pubkey, pair.privkey)

  private def ensureKeysDirCreated(): Try[Unit] = Try {
    val perms = PosixFilePermissions.asFileAttribute((SECRET_KEY_PERMISSIONS + OWNER_EXECUTE).asJava)
    Files.createDirectories(root, perms)

    val currentPerms = Files.getPosixFilePermissions(root)
    if(currentPerms.asScala != Set(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
      log.warn(s"Permissions for $root are too open")
  }

  def writeKeys(name: KeyName, pub: TufKey, priv: TufPrivateKey): Try[Unit] = {
    assert(pub.keytype == priv.keytype)

    for {
      _ <- ensureKeysDirCreated()
      _ <- writePublic(name, pub)
      _ <- writePrivate(name, priv)
      _ = log.info(s"Saved keys to $root/{${root.relativize(name.privateKeyPath)}, ${root.relativize(name.publicKeyPath)}}")
    } yield ()
  }

  def genKeys(name: KeyName, keyType: KeyType, keySize: Option[Int] = None): Try[TufKeyPair] = {
    val pair = keyType.crypto.generateKeyPair(keySize.getOrElse(keyType.crypto.defaultKeySize))
    writeKeys(name, pair).map(_ => pair)
  }

  def readPrivateKey(keyName: KeyName): Try[TufPrivateKey] =
    CliKeyStorage.readPrivateKey(keyName.privateKeyPath)

  def readPublicKey(keyName: KeyName): Try[TufKey] =
    CliKeyStorage.readPublicKey(keyName.publicKeyPath)

  def readKeyPair(keyName: KeyName): Try[(TufKey, TufPrivateKey)] = for {
    pub <- readPublicKey(keyName)
    priv <- readPrivateKey(keyName)
  } yield (pub, priv)
}
