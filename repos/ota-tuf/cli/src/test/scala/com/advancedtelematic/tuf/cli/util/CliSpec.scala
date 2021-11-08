package com.advancedtelematic.tuf.cli.util

import java.nio.file.Path
import java.security.Security
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import com.advancedtelematic.libats.data.DataType.ValidChecksum
import com.advancedtelematic.libtuf.crypt.SignedPayloadSignatureOps._
import com.advancedtelematic.libtuf.crypt.TufCrypto
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedRoleName, RoleKeys, RootRole, TargetsRole}
import com.advancedtelematic.libtuf.data.TufDataType.{Ed25519KeyType, KeyId, KeyType, RoleType, RsaKeyType, SignedPayload, TargetFilename, TargetVersion, TufKeyPair}
import com.advancedtelematic.libtuf.http.TufServerHttpClient.{RoleNotFound, TargetsResponse}
import com.advancedtelematic.libtuf.http._
import eu.timepit.refined.api.Refined
import io.circe.syntax._
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalactic.source.Position
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import com.advancedtelematic.tuf.cli.TryToFuture._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait KeyTypeSpecSupport {
  self: AnyFunSuite =>

  def keyTypeTest(name: String)(fn: KeyType => Any)(implicit pos: Position): Unit = {
    test(name + " Ed25519")(fn(Ed25519KeyType))
    test(name + " RSA")(fn(RsaKeyType))
  }
}

abstract class CliSpec extends AnyFunSuite with Matchers with ScalaFutures {
  Security.addProvider(new BouncyCastleProvider)

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(10, Seconds))
}

trait FakeTufServerClient extends TufServerClient {
  val keyType: KeyType

  protected val oldPair = keyType.crypto.generateKeyPair(keyType.crypto.defaultKeySize)

  protected val targetsPair = keyType.crypto.generateKeyPair(keyType.crypto.defaultKeySize)
  protected val pairs = Set(oldPair, targetsPair)
  protected var targetsPubKey = targetsPair.pubkey

  protected val rootRoles = new ConcurrentHashMap[Int, SignedPayload[RootRole]]()

  protected val pushedDelegations = new ConcurrentHashMap[DelegatedRoleName, SignedPayload[TargetsRole]]()

  override def fetchKeyPair(keyId: KeyId): Future[TufKeyPair] = {
    Future.successful(pairs.filter(_.pubkey.id == keyId).head)
  }

  override def root(version: Option[Int] = None): Future[SignedPayload[RootRole]] = {
    if (version.isDefined)
      Option(rootRoles.get(version.get)) match {
        case Some(r) => Future.successful(r)
        case None => Future.failed(RoleNotFound(s"role with version ${version.get} not found"))
      }
    else if (rootRoles.isEmpty) {
      val unsignedRoot = RootRole(
        Map(
          oldPair.pubkey.id -> oldPair.pubkey,
          targetsPubKey.id -> targetsPubKey),
        Map(
          RoleType.ROOT -> RoleKeys(Seq(oldPair.pubkey.id), 1),
          RoleType.TARGETS -> RoleKeys(Seq(targetsPubKey.id), 1)
        ), 1, Instant.now.plus(1, ChronoUnit.HOURS))

      val sig = TufCrypto.signPayload(oldPair.privkey, unsignedRoot.asJson).toClient(oldPair.pubkey.id)
      val signedRoot = SignedPayload(Seq(sig), unsignedRoot, unsignedRoot.asJson)

      rootRoles.put(signedRoot.signed.version, signedRoot)
      Future.successful(signedRoot)
    } else Future.successful {
      rootRoles.asScala.maxBy(_._1)._2
    }
  }

  override def deleteKey(keyId: KeyId): Future[Unit] = {
    if (keyId == oldPair.pubkey.id)
      Future.successful(())
    else
      Future.failed(new RuntimeException(s"[test] key not found $keyId"))
  }

  override def pushSignedRoot(signedRoot: SignedPayload[RootRole]): Future[Unit] = {
    if (signedRoot.isValidFor(oldPair.pubkey)) {
      rootRoles.put(signedRoot.signed.version, signedRoot)
      Future.successful(())
    } else
      Future.failed(new RuntimeException("[test] invalid signatures for root role"))
  }

  def sign(rootRole: RootRole): SignedPayload[RootRole] = {
    val sig = TufCrypto.signPayload(oldPair.privkey, rootRole.asJson).toClient(oldPair.pubkey.id)
    SignedPayload(Seq(sig), rootRole, rootRole.asJson)
  }

  def setRoot(signedPayload: SignedPayload[RootRole]): Unit = {
    rootRoles.put(signedPayload.signed.version, signedPayload)
  }

  def delegations(): List[(DelegatedRoleName, SignedPayload[TargetsRole])] = pushedDelegations.asScala.toList
}

object FakeReposerverTufServerClient {
  def apply(keyType: KeyType): FakeReposerverTufServerClient = new FakeReposerverTufServerClient(keyType)
}

class FakeReposerverTufServerClient(val keyType: KeyType) extends ReposerverClient with FakeTufServerClient with DirectorClient {

  private val _log = LoggerFactory.getLogger(this.getClass)

  private var unsignedTargets = TargetsRole(Instant.now.plus(1, ChronoUnit.DAYS), Map.empty, 1)

  val uploaded = new ConcurrentHashMap[TargetFilename, Path]()

  override def targets(): Future[TargetsResponse] = Future.successful {
    val sig = TufCrypto.signPayload(targetsPair.privkey, unsignedTargets.asJson).toClient(targetsPubKey.id)
    val signedPayload = SignedPayload(Seq(sig), unsignedTargets, unsignedTargets.asJson)
    TargetsResponse(signedPayload, Option(Refined.unsafeApply("095c33175e5af42691c1b41d388c8ce842c7fbb792fcc6514b5436f7f80420db")))
  }

  override def pushTargets(targetsRole: SignedPayload[TargetsRole], checksum: Option[Refined[String, ValidChecksum]]): Future[Unit] =
    Try(checksum.get).flatMap { _ =>
      val lastRoot = rootRoles.asScala.maxBy(_._1)._2.signed
      val targetsPubKey = lastRoot.keys(lastRoot.roles(RoleType.TARGETS).keyids.head)

      if (targetsRole.isValidFor(targetsPubKey)) {
        unsignedTargets = targetsRole.signed
        Success(())
      } else
        Failure(new RuntimeException("[test] invalid signatures for targets role"))
    }.toFuture

  override def pushDelegation(name: DelegatedRoleName, delegation: SignedPayload[TargetsRole]): Future[Unit] = Future.successful {
    this.pushedDelegations.put(name, delegation)
  }

  override def pullDelegation(name: DelegatedRoleName): Future[SignedPayload[TargetsRole]] = Future.successful {
    Option(pushedDelegations.get(name)).getOrElse(throw new IllegalArgumentException("[test] delegation not found"))
  }

  override def uploadTarget(targetFilename: TargetFilename, inputPath: Path, timeout: Duration): Future[Unit] = {
    _log.info(s"Received upload for $targetFilename, $inputPath")
    uploaded.put(targetFilename, inputPath)
    Future.successful(())
  }
}
