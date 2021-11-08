package com.advancedtelematic.tuf.cli

import java.net.URI
import java.time.{Instant, Period, ZoneOffset}
import java.util.concurrent.TimeUnit
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.crypt.Sha256FileDigest
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, RootRole, TargetCustom}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, RoleType, TargetFilename, TargetFormat, TargetName, TargetVersion, ValidTargetFilename}
import com.advancedtelematic.libtuf.http.{ReposerverClient, TufServerClient}
import com.advancedtelematic.tuf.cli.CliConfigOptionOps._
import com.advancedtelematic.tuf.cli.Commands._
import com.advancedtelematic.tuf.cli.Errors.PastDate
import com.advancedtelematic.tuf.cli.TryToFuture._
import com.advancedtelematic.tuf.cli.repo._
import eu.timepit.refined._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import cats.implicits._

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

object CommandHandler {

  val DEFAULT_ROOT_LIFETIME: Period = Period.ofDays(365)
  val DEFAULT_TARGET_LIFETIME: Period = Period.ofDays(31)

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private implicit def tryToFutureConversion[T](value: Try[T]): Future[T] = value.toFuture

  private def targetFilenameFrom(name: TargetName, version: TargetVersion): Try[TargetFilename] = {
    refineV[ValidTargetFilename](s"${name.value}-${version.value}").leftMap(s => new IllegalArgumentException(s)).toTry
  }

  private def buildClientTarget(name: TargetName, version: TargetVersion, length: Long, checksum: Checksum,
                                hardwareIds: List[HardwareIdentifier], uri: Option[URI], format: TargetFormat, cliUploaded: Boolean = false): Try[(TargetFilename, ClientTargetItem)] =
    for {
      targetFilename <- targetFilenameFrom(name, version)
      newTarget = {
        val custom = TargetCustom(name, version, hardwareIds, format.some, uri, cliUploaded = cliUploaded.some)
        val clientHashes = Map(checksum.method -> checksum.hash)
        ClientTargetItem(clientHashes, length, custom.asJson.some)
      }
    } yield targetFilename -> newTarget

  def expirationDate(config: Config, now: Instant = Instant.now())(previous: Instant): Instant = {
    val d = config.expireAfter
      .map(now.atOffset(ZoneOffset.UTC).plus(_).toInstant)
      .orElse(config.expireOn)
      .getOrElse(previous)

    if (d.isBefore(now) && !config.force) throw PastDate()
    else d
  }

  def handle[S <: TufServerClient](tufRepo: => TufRepo[S],
                                   repoServer: => Future[S],
                                   delegationsServer: => Future[ReposerverClient],
                                   userKeyStorage: CliKeyStorage,
                                   config: Config)
                                  (implicit ec: ExecutionContext): Future[Unit] = config.command match {
    case Help =>
      Cli.parser.showUsage()
      Future.successful(())

    case InitRepo =>
      RepoManagement.initialize(config.repoType.valueOrConfigError, tufRepo.repoPath, config.credentialsPath, config.reposerverUrl)
        .map(_ => log.info(s"Finished init for ${config.repoName.valueOrConfigError.value} using ${config.credentialsPath}"))


    case MoveOffline =>
      repoServer.flatMap { client =>
        tufRepo.moveRootOffline(client,
          config.rootKey,
          config.oldRootKey.get,
          config.keyId,
          config.keyNames.headOption,
          Instant.now().plus(DEFAULT_ROOT_LIFETIME))
          .map(_ => log.info(s"root keys moved offline, root.json saved to ${tufRepo.repoPath}"))
          .recover {
            case _:java.util.NoSuchElementException => log.warn(s"root keys moved offline, but you'll have to manually sign and push key!")
          }
      }

    case GetTargets =>
      repoServer.flatMap { client =>
        tufRepo.pullTargets(client).map { targetsResponse => log.info(targetsResponse.asJson.spaces2) }
      }

    case GetUnsignedTargets =>
      tufRepo
        .canonicalTargets
        .map(_.getBytes)
        .map(config.outputPath.streamOrStdout.write)

    case InitTargets =>
      tufRepo
        .initTargets(config.version.valueOrConfigError, config.expireOn.getOrElse(Instant.now().plus(DEFAULT_TARGET_LIFETIME)))
        .map(p => log.info(s"Wrote empty targets to $p"))


    case AddTarget =>
      val itemT = buildClientTarget(
        config.targetName.valueOrConfigError,
        config.targetVersion.valueOrConfigError,
        config.length,
        config.checksum.valueOrConfigError,
        config.hardwareIds,
        config.targetUri,
        config.targetFormat
      )

      itemT
        .flatMap((tufRepo.addTarget _).tupled)
        .map(p => log.info(s"added target to $p"))

    case AddUploadedTarget =>
      val file = config.inputPath.valueOrConfigError

      val itemT = buildClientTarget(
        config.targetName.valueOrConfigError,
        config.targetVersion.valueOrConfigError,
        file.toFile.length(),
        Sha256FileDigest.from(file),
        config.hardwareIds,
        uri = None,
        format = TargetFormat.BINARY,
        cliUploaded = true
      )

      itemT
        .flatMap((tufRepo.addTarget _).tupled)
        .map(p => log.info(s"added uploaded target to $p"))

    case DeleteTarget =>
      tufRepo
        .deleteTarget(config.targetFilename.valueOrConfigError)
        .map(p => log.info(s"Wrote empty targets to $p"))


    case SignTargets =>
      tufRepo
        .signTargets(config.keyNames, expirationDate(config), config.version, config.signatures)
        .map(p => log.info(s"signed targets.json to $p"))


    case UploadTarget =>
      for {
        filename <- Future.fromTry(targetFilenameFrom(config.targetName.valueOrConfigError, config.targetVersion.valueOrConfigError))
        client <- repoServer
        _ <- tufRepo.uploadTarget(client, filename, config.inputPath.valueOrConfigError, Duration(config.timeout, TimeUnit.SECONDS))
      } yield log.info("Target uploaded. You can now add this target to your targets with `targets add`")

    case PullTargets =>
      for {
        client <- repoServer
        rootRole <- tufRepo.readSignedRole[RootRole].toFuture
        _ <- tufRepo.pullVerifyTargets(client, rootRole.signed)
      } yield log.info("Pulled targets")

    case PushTargets =>
      repoServer
        .flatMap(tufRepo.pushTargets)
        .map(_ => log.info("Pushed targets"))

    case VerifyRoot =>
      CliUtil.verifyRootFile(config.inputPath.valueOrConfigError).map(_ => ())

    case GenRepoKeys =>
      assert(config.rootKey.isDefined, "The base filename for keys must be defined when generating a new key.")
      tufRepo.genKeys(config.rootKey.get, config.keyType, config.keySize).map(_ => ())

    case GetUnsignedRoot =>
      tufRepo
        .canonicalRoot
        .map(_.getBytes)
        .map(config.outputPath.streamOrStdout.write)

    case PullRoot =>
      repoServer.flatMap(client => tufRepo.pullRoot(client, config.force))
        .map(_ => log.info("Pulled root.json"))

    case PushRoot =>
      repoServer.flatMap(client => tufRepo.pushRoot(client))
        .map(_ => log.info("Pushed root.json"))

    case SignRoot =>
      tufRepo.signRoot(config.keyNames, expirationDate(config), config.oldRootKey, config.signatures)
        .map(p => log.info(s"signed root.json saved to $p"))

    case AddRootKey =>
      tufRepo.addRoleKeys(RoleType.ROOT, config.keyNames)
        .map(p => log.info(s"keys added to unsigned root.json saved to $p"))

    case RemoveRootKey =>
      tufRepo.keyIdsByName(config.keyNames).flatMap { keyIds =>
        tufRepo.removeRoleKeys(RoleType.ROOT, config.keyIds ++ keyIds)
      }.map { case (p, c) => log.info(s"$c keys removed from unsigned root.json saved to $p") }

    case AddTargetsKey =>
      tufRepo.addRoleKeys(RoleType.TARGETS, config.keyNames)
        .map(p => log.info(s"target keys added to unsigned root.json saved to $p"))

    case RemoveTargetsKey =>
      tufRepo.keyIdsByName(config.keyNames).flatMap { keyIds =>
        tufRepo.removeRoleKeys(RoleType.TARGETS, config.keyIds ++ keyIds)
      }.map { case (p, c) => log.info(s"$c keys removed from unsigned targets.json saved to $p") }

    case ExportRepository =>
      RepoManagement.export(tufRepo, config.keyNames.head, config.outputPath.valueOrConfigError)

    case GenUserKey =>
      config.keyNames.map { keyName =>
        userKeyStorage.genKeys(keyName, config.keyType, config.keySize)
      }.sequence_

    case IdUserKey =>
      CliKeyStorage.readPublicKey(config.inputPath.valueOrConfigError).map { key =>
        config.outputPath.streamOrStdout.write(key.id.value.getBytes)
      }

    case CreateDelegation =>
      Delegations.writeNew(config.outputPath.streamOrStdout)

    case SignDelegation =>
      config.keyNames
        .map(userKeyStorage.readKeyPair).sequence
        .flatMap { keyPairs =>
          Delegations.signPayload(keyPairs, config.inputPath.valueOrConfigError, WriteOutput.fromConfig(config))
        }

    case PushDelegation =>
      delegationsServer.flatMap { server =>
        Delegations.push(server, config.delegationName, config.inputPath.valueOrConfigError)
      }

    case AddDelegationToTarget =>
      config.keyPaths.map(CliKeyStorage.readPublicKey).sequence.flatMap { keys =>
        tufRepo.addTargetDelegation(config.delegationName, keys, config.delegatedPaths, threshold = 1)
      }.map(_ => ())

    case AddTargetToDelegation =>
      val itemT = buildClientTarget(
        config.targetName.valueOrConfigError,
        config.targetVersion.valueOrConfigError,
        config.length,
        config.checksum.valueOrConfigError,
        config.hardwareIds,
        config.targetUri,
        config.targetFormat
      )

      itemT.flatMap { case(targetFilename, targetItem) =>
        Delegations.addTarget(config.inputPath.valueOrConfigError, WriteOutput.fromConfig(config), targetFilename, targetItem)
      }

    case PullDelegation =>
      delegationsServer.flatMap { server =>
        Delegations.pull(server, config.delegationName, WriteOutput.fromConfig(config)).map(_ => ())
      }

    case ImportClientTls =>
      TufRepo.importTlsCerts(tufRepo.repoPath, config.inputPath.valueOrConfigError, config.serverCertPath)
        .map(_ => log.info("Certificate(s) imported"))
        .toFuture

    case IncrementRootJsonVersion =>
      tufRepo.incrementRootVersion
        .map(p => log.info(s"Version incremented for unsigned root.json saved to $p"))
        .toFuture

    case IncrementTargetJsonVersion =>
      tufRepo.incrementTargetVersion
        .map(p => log.info(s"Version incremented for unsigned target.json saved to $p"))
        .toFuture

    case ImportPublicKey =>
      CliKeyStorage
        .forRepo(tufRepo.repoPath)
        .importPublicKey(config.inputPath.valueOrConfigError, config.keyNames)
        .toFuture
  }
}
