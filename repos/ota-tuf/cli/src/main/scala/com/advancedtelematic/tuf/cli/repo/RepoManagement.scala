package com.advancedtelematic.tuf.cli.repo

import java.io._
import java.net.URI
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.TufRole._
import com.advancedtelematic.libtuf.data.ClientDataType.{RootRole, TufRole, TufRoleOps}
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.{SignedPayload, TufKey, TufPrivateKey}
import com.advancedtelematic.tuf.cli.CliCodecs._
import com.advancedtelematic.tuf.cli.{CliUtil, DataType}
import com.advancedtelematic.tuf.cli.DataType.{TufServerType, _}
import com.advancedtelematic.tuf.cli.repo.TufRepo.{MissingCredentialsZipFile, RepoAlreadyInitialized, TreehubConfigError}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.slf4j.LoggerFactory
import cats.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Failure, Success, Try}

object RepoManagement {
  private val zipTargetKeyName = KeyName("targets")

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def initialize(repoServerType: TufServerType, repoPath: Path, initFilePath: Path, repoUri: Option[URI] = None)
                (implicit ec: ExecutionContext): Try[TufRepo[_]] =
    ensureRepoNotExists(repoPath).flatMap { _ =>
      ZipRepoInitialization.init(repoServerType, repoPath, zipTargetKeyName, initFilePath, repoUri)
    }

  private def ensureRepoNotExists(repoPath: Path): Try[Unit] = {
    if(Files.exists(repoPath.resolve("config.json")))
      Failure(RepoAlreadyInitialized(repoPath))
    else
      Success(())
  }

  def export(repo: TufRepo[_], targetKey: KeyName, exportPath: Path): Try[Unit] = {
    def copyTreehubConfig(src: ZipFile, dest: ZipOutputStream): Try[Unit] = {
      for {
        _ <- Try(dest.putNextEntry(new ZipEntry("treehub.json")))
        authConfig <- repo.authConfig
        treehubConfig <- repo.treehubConfig
        treehubAuth = authConfig.collect { case oauth: OAuthConfig => oauth }
        newTreehubJson = treehubConfig.copy(oauth2 = treehubAuth).asJson
        _ <- Try(dest.write(newTreehubJson.spaces2.getBytes))
        _ <- Try(dest.closeEntry())
      } yield ()
    }

    def copyEntries(src: ZipFile, dest: ZipOutputStream): Try[Unit] = {
      val entries = src.entries().asScala.map { zipEntry =>
        val is = src.getInputStream(zipEntry)

        if (zipEntry.getName != zipTargetKeyName.publicKeyName &&
          zipEntry.getName != zipTargetKeyName.privateKeyName) {
          // ignoring any errors here
          Try {
            dest.putNextEntry(zipEntry)
            dest.write(toByteArray(is))
          }
        }

        Try(dest.closeEntry())
      }

      entries.toList.sequence_
    }

    def copyKeyPair(pubKey: TufKey, privKey: TufPrivateKey, dest: ZipOutputStream): Try[Unit] = Try {
      dest.putNextEntry(new ZipEntry(zipTargetKeyName.publicKeyName))
      dest.write(pubKey.asJson.spaces2.getBytes())
      dest.closeEntry()

      dest.putNextEntry(new ZipEntry(zipTargetKeyName.privateKeyName))
      dest.write(privKey.asJson.spaces2.getBytes())
      dest.closeEntry()
    }

    def copyRole[T : TufRole : Encoder : Decoder](repo: TufRepo[_], dest: ZipOutputStream): Try[Unit] = {
      repo.readSignedRole[T].map { role =>
        dest.putNextEntry(new ZipEntry(role.signed.metaPath.value))
        dest.write(role.asJson.spaces2.getBytes)
        dest.closeEntry()
      }
    }

    def writeTufUrl(repo: TufRepo[_], dest: ZipOutputStream): Try[Unit] = {
      repo.repoServerUri.map { uri =>
        dest.putNextEntry(new ZipEntry("tufrepo.url"))
        dest.write(uri.toString.getBytes)
        dest.closeEntry()
      }
    }

    def ensureSourceZipExists(repoPath: Path): Try[ZipFile] = {
      val zipPath = repo.repoPath.resolve("credentials.zip")

      Try(new ZipFile(zipPath.toFile)).recoverWith {
        case _: FileNotFoundException =>
          _log.info("zip file not found in repo, creating empty zip file")

          Try {
            val zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile))
            zos.close()
            new ZipFile(zipPath.toFile)
          }
      }
    }

    Try(new ZipOutputStream(new FileOutputStream(exportPath.toFile))).flatMap { zipExportStream ⇒
      ensureSourceZipExists(repo.repoPath).flatMap { sourceZip =>
        val t = for {
          (pubKey, privKey) <- repo.keyStorage.readKeyPair(targetKey)
          _ <- copyTreehubConfig(sourceZip, zipExportStream)
          _ <- writeTufUrl(repo, zipExportStream)
          _ <- copyKeyPair(pubKey, privKey, zipExportStream)
          _ <- copyRole[RootRole](repo, zipExportStream).recover {
            case ex =>
              _log.warn(s"Could not copy RootRole: ${ex.getMessage}")
          }
          _ ← copyEntries(sourceZip, zipExportStream)
        } yield ()

        Try(sourceZip.close())

        Try(zipExportStream.close())

        t
      }
    }
  }

  private def toByteArray(is: InputStream): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    Stream.continually(is.read).takeWhile(_ != -1).foreach(baos.write)
    baos.toByteArray
  }
}

protected object ZipRepoInitialization {
  import com.advancedtelematic.tuf.cli.CliCodecs._

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def init(repoServerType: TufServerType, repoPath: Path, zipTargetKeyName: KeyName, initFilePath: Path,
           repoUri: Option[URI])
          (implicit ec: ExecutionContext): Try[TufRepo[_]] = {
    val tufRepo = TufRepo(repoServerType, repoPath)

    tufRepo.initRepoDirs().get

    // copy whole ZIP file into repo
    Files.copy(initFilePath, repoPath.resolve("credentials.zip"))

    def readTreehubConfig(src: ZipFile): Try[TreehubConfig] = for {
      is <- Try(src.getInputStream(src.getEntry("treehub.json")))
      treehubConfig <- CliUtil.readJsonFrom[TreehubConfig](is)
    } yield treehubConfig

    def buildOauth(treehubConfig: TreehubConfig): Try[Option[OAuthConfig]] =
      if(treehubConfig.no_auth) Success(None)
      else
        Either.fromOption(treehubConfig.oauth2, TreehubConfigError("auth required with no_auth: false")).toTry.map(_.some)

    def readRepoUri(src: ZipFile): Try[URI] = {
      val filename = repoServerType match {
        case RepoServer => "tufrepo.url"
        case Director => "api_gateway.url"
      }

      for {
        entry <- Try(src.getEntry(filename)).filter(_ != null).orElse(Failure(MissingCredentialsZipFile(filename)))
        is <- Try(src.getInputStream(entry))
        uri ← Try(new URI(Source.fromInputStream(is).mkString.trim))
      } yield uri
    }

    def writeTargetKeys(src: ZipFile): Try[Unit] = for {
      pubKeyIs <- Try(src.getInputStream(src.getEntry(zipTargetKeyName.publicKeyName)))
      pubKey <- CliUtil.readJsonFrom[TufKey](pubKeyIs)

      privateKeyIs <- Try(src.getInputStream(src.getEntry(zipTargetKeyName.privateKeyName)))
      privKey <- CliUtil.readJsonFrom[TufPrivateKey](privateKeyIs)

      _ <- tufRepo.keyStorage.writeKeys(zipTargetKeyName, pubKey, privKey)
    } yield ()

    def writeRoot(src: ZipFile): Try[Unit] =  {
      if (repoServerType == DataType.RepoServer) {
        for {
          rootIs <- Try(src.getInputStream(src.getEntry(TufRole.rootTufRole.metaPath.value)))
          rootRole <- CliUtil.readJsonFrom[SignedPayload[RootRole]](rootIs)
          _ <- tufRepo.writeSignedRole(rootRole)
        } yield ()
      } else {
        Try(())
      }
    }

    def writeTlsCerts(src: ZipFile): Try[Option[MutualTlsConfig]] = Try {
      val clientCertO = Option(src.getEntry("client_auth.p12"))
      val serverCert = Option(src.getEntry("server.p12"))

      clientCertO.map { clientCert =>
        val cert = src.getInputStream(clientCert)
        val path = repoPath.resolve("client_auth.p12")

        val serverCertPath = serverCert.map { serverCertEntry =>
          val serverPath = repoPath.resolve("server.p12")
          Files.copy(src.getInputStream(serverCertEntry), serverPath)
          serverPath.getFileName
        }

        Files.copy(cert, path)

        MutualTlsConfig(path.getFileName, serverCertPath)
      }
    }

    for {
      src ← Try(new ZipFile(initFilePath.toFile))
      treehubConfig <- readTreehubConfig(src)
      clientCertPath <- writeTlsCerts(src)
      auth <- if(clientCertPath.isDefined) Success(clientCertPath) else buildOauth(treehubConfig)
      serverUri <- if(repoUri.isDefined) Success(repoUri.get) else readRepoUri(src)
      repoConfig =  RepoConfig(serverUri, auth, treehubConfig, repoServerType)
      _ <- TufRepo.writeConfig(repoPath, repoConfig)
      _ <- writeRoot(src).recover { case ex =>
        _log.warn(s"Could not read/write root.json from credentials zip file: ${ex.getMessage}. Continuing.")
      }
      _ <- writeTargetKeys(src).recover { case ex =>
        _log.warn(s"Could not read/write target keys from credentials zip file: ${ex.getMessage}. Continuing.")
      }
      _ = Try(src.close())
    } yield tufRepo
  }
}
