package com.advancedtelematic.tuf.cli

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.security.Security
import java.time.{Instant, Period}

import cats.Eval
import cats.syntax.either._
import cats.syntax.option._
import ch.qos.logback.classic.{Level, Logger}
import com.advancedtelematic.libats.data.DataType.Checksum
import com.advancedtelematic.libtuf.data.ClientDataType.{DelegatedPathPattern, DelegatedRoleName}
import com.advancedtelematic.libtuf.data.TufDataType.TargetFormat.TargetFormat
import com.advancedtelematic.libtuf.data.TufDataType.{HardwareIdentifier, KeyId, KeyType, TargetFilename, TargetFormat, TargetName, TargetVersion, ValidSignatureType}
import com.advancedtelematic.libtuf.http.TufServerHttpClient.RoleChecksumNotValid
import com.advancedtelematic.libtuf.http.{DirectorClient, ReposerverClient}
import com.advancedtelematic.tuf.cli.CliConfigOptionOps._
import com.advancedtelematic.tuf.cli.Commands._
import com.advancedtelematic.tuf.cli.DataType._
import com.advancedtelematic.tuf.cli.Errors.CliArgumentsException
import com.advancedtelematic.tuf.cli.GenericOptionDefOps._
import com.advancedtelematic.tuf.cli.http.TufRepoCliClient
import com.advancedtelematic.tuf.cli.http.TufRepoCliClient._
import com.advancedtelematic.tuf.cli.repo.{CliKeyStorage, DirectorRepo, RepoServerRepo, TufRepo}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import scopt.{OptionDef, OptionParser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import com.advancedtelematic.libtuf.data.ValidatedString._
import DelegatedRoleName._

case class Config(command: Command,
                  home: Path = Paths.get("tuf"),
                  credentialsPath: Path = Paths.get("credentials.zip"),
                  repoType: Option[TufServerType] = RepoServer.some,
                  repoName: Option[RepoName] = None,
                  delegationName: DelegatedRoleName = "<empty>".unsafeApply,
                  rootKey: Option[KeyName] = None,
                  keyType: KeyType = KeyType.default,
                  oldRootKey: Option[KeyName] = None,
                  signatures: Option[Map[KeyName, ValidSignatureType]] = None,
                  keyNames: List[KeyName]= List.empty,
                  keyIds: List[KeyId]= List.empty,
                  keyId: Option[KeyId] = None,
                  version: Option[Int] = None,
                  expireOn: Option[Instant] = None,
                  expireAfter: Option[Period] = None,
                  length: Long = -1L,
                  targetFilename: Option[TargetFilename] = None,
                  targetName: Option[TargetName] = None,
                  targetFormat: TargetFormat = TargetFormat.BINARY,
                  targetVersion: Option[TargetVersion] = None,
                  checksum: Option[Checksum] = None,
                  hardwareIds: List[HardwareIdentifier] = List.empty,
                  targetUri: Option[URI] = None,
                  keySize: Option[Int] = None,
                  userKeysPath: Option[Path] = None,
                  inputPath: Option[Path] = None,
                  outputPath: Option[Path] = None,
                  timeout: Long = 3600,
                  serverCertPath: Option[Path] = None,
                  delegatedPaths: List[DelegatedPathPattern] = List.empty,
                  keyPaths: List[Path] = List.empty,
                  force: Boolean = false,
                  reposerverUrl: Option[URI] = None,
                  verbose: Boolean = false,
                  inplace: Boolean = false)

object Cli extends App with VersionInfo {

  import CliReads._

  Security.addProvider(new BouncyCastleProvider)

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val PROGRAM_NAME = "garage-sign"

  lazy val repoNameOpt: OptionParser[Config] => OptionDef[RepoName, Config] = { parser =>
    parser
      .opt[RepoName]("repo").abbr("r").required()
      .action( (arg, c) => c.copy(repoName = arg.some))
      .text("The name of the local repository. This repository should be a directory in your `tuf` repository. You can create the repository with the `init` command.")
  }

  lazy val manyKeyNamesOpt: OptionParser[Config] => OptionDef[KeyName, Config] = { parser =>
    parser
      .opt[KeyName]("key-name").abbr("k")
      .unbounded()
      .required()
      .action { (arg, config) => config.copy(keyNames = arg :: config.keyNames) }
      .text("The base filename for your keys. Generated files will be named `<key-name>.sec` and `<key-name>.pub`.")
  }

  lazy val keysPathOpt: OptionParser[Config] => OptionDef[Path, Config] = { parser =>
    parser
      .opt[Path]("keys-path").abbr("p")
      .toConfigOptionParam('userKeysPath)
      .text("The path where this executable will look for keys. By default, it is the `user-keys` directory in the directory that you specified with the `--home-dir` command.")
  }

  lazy val expirationOpts: OptionParser[Config] => Seq[OptionDef[_, Config]] = { parser =>
    Seq(
      parser.opt[Instant]("expires")
        .text("The metadata expiry date. It is a UTC instant, such as `2020-01-01T00:01:00Z`.")
        .toConfigOptionParam('expireOn),
      parser.opt[Period]("expire-after")
        .text("The expiration delay in years, months, and days (each optional, but in that order), such as `1Y3M5D`.")
        .toConfigOptionParam('expireAfter),
      parser.opt[Unit]("force")
        .action { (_, c) => c.copy(force = true) }
        .text("Skips sanity checking. For example, allows to set a date in the past."))
  }

  lazy val addTargetOptions: OptionParser[Config] => Seq[OptionDef[_, Config]] = { parser =>
    Seq(
      parser.opt[Long]("length")
        .required()
        .toConfigParam('length)
        .text("The length of the target, in bytes."),
      parser.opt[TargetName]("name")
        .required()
        .toConfigOptionParam('targetName)
        .text("The name of the target."),
      parser.opt[TargetVersion]("version")
        .required()
        .toConfigOptionParam('targetVersion)
        .text("The version string of the target."),
      parser.opt[TargetFormat]("format")
        .optional()
        .text("The format of the target: [ostree|binary].")
        .toConfigParam('targetFormat),
      parser.opt[Checksum]("sha256")
        .required()
        .toConfigOptionParam('checksum)
        .text("The hash of the binary. For OSTree images, it is the root hash of the target commit."),
      parser.opt[List[HardwareIdentifier]]("hardwareids")
        .required()
        .toConfigParam('hardwareIds)
        .text("The types of hardware with which this image is compatible."),
      parser.opt[URI]("url")
        .toConfigOptionParam('targetUri)
        .text("(Optional) An external URL where the binary can be downloaded.")
    )
  }

  lazy val parser = new scopt.OptionParser[Config](PROGRAM_NAME) {
    head(projectName, Cli.version)

    help("help").text("Prints all available `garage-sign` commands and options.")

    version("version").text("Prints the current binary version.")

    opt[Path]("home-dir").abbr("h").toConfigParam('home).text("The directory that you want to work with. By default, it is your current working directory.")

    opt[Unit]("verbose").text("Prints the verbose information for the execution.").action { (_, c) =>
      c.copy(verbose = true)
    }

    version("version").text("Prints the current binary version.")

    note(" " + sys.props("line.separator"))

    cmd("user-keys").children(
      keysPathOpt(this),
      cmd("gen")
        .toCommand(GenUserKey)
        .text("Creates a key pair and stores it in a configurable location.")
        .children(
          opt[KeyType]("type").abbr("t").toConfigParam('keyType)
            .text("The type of key that you want to create: Ed25519 or RSA."),
          opt[Int]("keysize").toConfigOptionParam('keySize)
            .text("The length of the key that you want to create, in bits. RSA 2048/4096 and Ed25519 are supported."),
          manyKeyNamesOpt(this).maxOccurs(1)
        ),
      cmd("id")
        .text("Calculates the Uptane key ID for a given public key.")
        .toCommand(IdUserKey)
        .children(
          opt[Path]("input").abbr("i").required().toConfigOptionParam('inputPath).text("The path to the file with your public key.")
        ),
      cmd("importpub")
        .toCommand(ImportPublicKey)
        .text("Imports a public key and stores it on a configurable location")
        .children(
          repoNameOpt(this),
          manyKeyNamesOpt(this).text("The path to the public key that you want to add."),
          opt[Path]("input")
            .abbr("i")
            .required()
            .toConfigOptionParam('inputPath)
            .text("The path to the file with your public key.")
        )
    ).text("Manages keys stored outside of a specific repository’s directory.")

    note(" " + sys.props("line.separator"))

    cmd("delegations").children(
      cmd("init")
        .toCommand(CreateDelegation)
        .text("Creates an empty .json file with delegation metadata that you can edit and sign."),
      cmd("sign")
        .toCommand(SignDelegation)
        .children(
          manyKeyNamesOpt(this).text("The base name of the key to use for signing."),
          keysPathOpt(this),
          opt[Path]("input").abbr("i").required().toConfigOptionParam('inputPath).text("The path to the delegated Targets metadata file that you want to sign."),
          opt[Unit]("inplace").abbr("e").optional().action { case (_, c) => c.copy(inplace = true) }.text("Modifies the input .json file directly. If this option is not specified, it outputs the signed metadata to stdout.")
        ).text("Signs delegation metadata."),
      cmd("push")
        .toCommand(PushDelegation)
        .children(
          repoNameOpt(this),
          opt[DelegatedRoleName]("name").abbr("n").required().toConfigParam('delegationName).text("The name of the delegation."),
          opt[Path]("input").abbr("i").required().toConfigOptionParam('inputPath).text("The path to the signed .json file with delegations.")
        ).text("Pushes delegation metadata to the server. Requires an initialized `tuf` repository."),
      cmd("pull")
        .toCommand(PullDelegation)
        .children(
          repoNameOpt(this),
          opt[DelegatedRoleName]("name").abbr("n").required().toConfigParam('delegationName).text("The name of the delegation."),
          opt[Path]("output").abbr("o").toConfigOptionParam('outputPath).text("The name of the file to which you want to save the delegation.")
        ).text("Pulls a delegated Targets metadata file from the server. Requires an initialized `tuf` repository."),
      cmd("add-target")
        .toCommand(AddTargetToDelegation)
        .children(addTargetOptions(this):_*)
        .children(
          opt[Path]("input").abbr("i").required().toConfigOptionParam('inputPath).text("The path to the delegated Targets metadata file that you want to modify."),
          opt[Unit]("inplace").abbr("e").optional().action { case (_, c) => c.copy(inplace = true) }.text("Modifies the input .json file directly. If this option is not specified, it outputs the signed metadata to stdout.")
        ).text("Adds a new target to a delegated Targets metadata file.")
    ).text("Manages delegation metadata.")

    note(" " + sys.props("line.separator"))

    cmd("init")
      .toCommand(InitRepo)
      .text("Creates an empty local repository.")
      .children(
        repoNameOpt(this).text("The name of the local repository that you want to create. This repository should be a directory in your `tuf` repository."),
        opt[URI]("reposerver").toConfigOptionParam('reposerverUrl).text("The repo server URL. By default, reads the URL from the .zip file with your provisioning credentials."),
        opt[Path]("credentials")
          .abbr("c")
          .toConfigParam('credentialsPath)
          .text("The path to the .zip file with your provisioning credentials.")
          .required(),
        opt[TufServerType]("servertype")
          .abbr("t")
          .text("The repo server type: `reposerver` (default) or `director`.")
          .toConfigOptionParam('repoType)
          .optional()
      )

    note(" " + sys.props("line.separator"))

    cmd("key").children(
      cmd("generate")
        .toCommand(GenRepoKeys)
        .text("Generates a new key and saves it in a specific repository.")
        .children(
          repoNameOpt(this),
          opt[KeyName]("name").abbr("n").required()
            .toConfigOptionParam('rootKey)
            .text("The base filename for your keys. Generated files will be named `<key-name>.sec` and `<key-name>.pub`."),
          opt[KeyType]("type")
            .abbr("t")
            .toConfigParam('keyType)
            .text("The type of key that you want to create: Ed25519 or RSA."),
          opt[Int]("keysize")
            .toConfigOptionParam('keySize)
            .text("The length of the key that you want to create, in bits. RSA 2048/4096 and Ed25519 are supported.")
        )
    ).text("Manages keys stored in a specific local repository’s directory.")

    note(" " + sys.props("line.separator"))

    cmd("move-offline")
      .toCommand(MoveOffline)
      .text("Removes online keys from OTA Connect, and updates the environment to use locally stored offline keys.")
      .children(
        repoNameOpt(this),
        opt[KeyName]("new-root")
          .text("(Optional) The new Root key that you want to add to the `root.json` file. If not provided, you'll have to manually sign and push it with `root sign` and `root push`.")
          .toConfigOptionParam('rootKey),
        opt[KeyName]("new-targets")
          .text("(Only for the repo server) The new Targets key that you want to add to the `root.json` file (should already exist).")
          .action { (keyName: KeyName, c) => c.copy(keyNames = List(keyName)) },
        opt[KeyName]("old-root-alias")
          .text("The alias of the old Root key. The old Root key will be saved under this name.")
          .required()
          .toConfigOptionParam('oldRootKey),
        opt[KeyId]("old-keyid")
          .text("(Optional) The ID of the key that you want to remove from the `root.json` file. This app will try to use the last key defined in the current `root.json` file.")
          .toConfigOptionParam('keyId)
      )

    note(" " + sys.props("line.separator"))

    cmd("root")
      .text("Manages root-of-trust metadata for a repository.")
      .children(
        repoNameOpt(this),
        cmd("pull")
          .toCommand(PullRoot)
          .children(
            opt[Unit]("force")
              .action { (_, c) => c.copy(force = true) }
              .text("Skips validation of the remote `root.json` file against the local `root.json` file")
              .hidden()
          ).text("Pulls the current `root.json` file from OTA Connect."),
        cmd("push")
          .toCommand(PushRoot)
          .text("Uploads local `root.json` file to OTA Connect. If the file does not have a valid signature, it will be rejected by the server."),
        cmd("get-unsigned")
          .toCommand(GetUnsignedRoot)
          .text("Generates an unsigned `root.json` file in a canonical JSON form."),
        cmd("key")
          .children(
            cmd("add")
              .toCommand(AddRootKey)
              .children(
                manyKeyNamesOpt(this).text("The path to the public key that you want to add.")
              ).text("Adds a specific key to the list of keys authorized to sign the root-of-trust metadata."),
            cmd("remove")
              .toCommand(RemoveRootKey)
              .children(
                manyKeyNamesOpt(this).text("The name of the file with the keys that you want to remove. You can use the `--key-id` command instead."),
                opt[KeyId]("key-id")
                  .unbounded()
                  .action { (arg, c) => c.copy(keyIds = arg :: c.keyIds) }
                  .text("The ID of the public key that you want to remove. You can use the `--key-name` command instead."),
              ).text("Removes a specific key from the list of keys authorized to sign the root-of-trust metadata.")
          ).text("Manages keys that are permitted to sign the root-of-trust metadata."),
        cmd("targets-key")
          .children(
            cmd("add")
              .toCommand(AddTargetsKey)
              .children(
                manyKeyNamesOpt(this).text("The path to the public key that you want to add.")
              ).text("Adds a specific key to the list of keys authorized to sign the targets metadata."),
            cmd("remove")
              .toCommand(RemoveTargetsKey)
              .children(
                manyKeyNamesOpt(this).text("The name of the file with the keys that you want to remove. You can use the `--key-id` command instead."),
                opt[KeyId]("key-id")
                  .unbounded()
                  .action { (arg, c) => c.copy(keyIds = arg :: c.keyIds) }
                  .text("The ID of the public key that you want to remove. You can use the `--key-name` command instead."),
              ).text("Removes a specific key from the list of keys authorized to sign the targets metadata.")
          ).text("Manages keys that are permitted to sign the targets metadata."),
        cmd("sign")
          .toCommand(SignRoot)
          .text("Signs your root-of-trust metadata with a specific key and sets the expiry.")
          .children(manyKeyNamesOpt(this).optional.text("The path to the public key to use for signing."))
          .children(expirationOpts(this):_*)
          .children(
            opt[KeyName]("old-root-alias")
              .optional
              .toConfigOptionParam('oldRootKey)
              .text("The alias of the old root key."),
            opt[Map[KeyName, ValidSignatureType]]("signatures")
              .toConfigOptionParam('signatures)
              .text("The external signatures to add to root.json.")
          ),
        cmd("increment-version")
          .toCommand(IncrementRootJsonVersion)
          .text("Increment version of root.json.")
      )

    note(" " + sys.props("line.separator"))

    cmd("targets")
      .text("""(Only for repositories of type `reposerver`) Manages Targets metadata.
                  |Target is a term from Uptane.
                  |Each Target corresponds to a software version available in your OTA Connect software repository.""".stripMargin)
      .toCommand(InitTargets)
      .children(
        repoNameOpt(this),
        cmd("init")
          .toCommand(InitTargets)
          .children(
            opt[Int]("version")
              .toConfigOptionParam('version)
              .required()
              .text("""The version of the `targets.json` file.
                          |Versions are integers, normally starting at 1. They must always increase in each successive `targets.json` version.""".stripMargin),
            opt[Instant]("expires")
              .toConfigOptionParam('expireOn)
              .text("The metadata expiry date. It is a UTC instant, such as `2020-01-01T00:01:00Z`.")
              .required()
          ).text("Creates a new top-level (non-delegated) `targets.json` file."),
        cmd("add")
          .toCommand(AddTarget)
          .children(addTargetOptions(this):_*)
          .text("Adds a target."),
        cmd("add-uploaded")
          .toCommand(AddUploadedTarget)
          .children(
            opt[Path]('i', "input")
              .required()
              .toConfigOptionParam('inputPath)
              .text("The path to the binary file."),
            opt[TargetName]("name")
              .required()
              .toConfigOptionParam('targetName)
              .text("The name of the target."),
            opt[TargetVersion]("version")
              .required()
              .toConfigOptionParam('targetVersion)
              .text("The version string of the target."),
            opt[List[HardwareIdentifier]]("hardwareids")
              .required()
              .toConfigParam('hardwareIds)
              .text("The types of hardware with which this image is compatible.")
          ).text("Adds a target that you previously uploaded to OTA Connect using the `targets upload` command."),
        cmd("delete")
          .toCommand(DeleteTarget)
          .text("Deletes a single target. This target can no longer be installed on devices.")
          .children(
            opt[TargetFilename]("filename")
              .required()
              .toConfigOptionParam('targetFilename)
              .text("""The exact name of the target to remove.
                          |Should be in one of the following forms: `<name>_<version>` for OSTree images, or `<name>-<version>` for binary images.""".stripMargin)
          ),
        cmd("get-unsigned")
          .toCommand(GetUnsignedTargets)
          .text("Generates an unsigned `targets.json` file in a canonical JSON form."),
        cmd("sign")
          .toCommand(SignTargets)
          .text("Signs your `targets.json` file with a specific key or adds a given signature.")
          .children(
            opt[KeyName]("key-name")
              .action { (arg, c) => c.copy(keyNames = List(arg)) }
              .text("The path to the public key to use for signing."),
            opt[Int]("version")
              .text("The version number to use for the signed metadata. Overrides the version in the unsigned `targets.json` file.")
              .toConfigOptionParam('version),
            opt[Map[KeyName, ValidSignatureType]]("signatures")
              .action { (m, c) => if (m.isEmpty) c.copy(signatures = None) else c.copy(signatures = Some(m)) }
              .text("The external rsassa-pss-sha256 signatures to add (after being verified)")
          )
          .children(expirationOpts(this):_*),
        cmd("pull")
          .toCommand(PullTargets)
          .text("Pulls the current `targets.json` file from OTA Connect."),
        cmd("push")
          .toCommand(PushTargets)
          .text("""Pushes the latest `targets.json` file to the server.
                  |If the Targets file is invalid, for example because of a bad signature or a non-increasing version number,
                  |this `push` will fail with exit code 2.""".stripMargin),

        cmd("upload")
          .toCommand(UploadTarget)
          .children(
            opt[Path]('i', "input")
              .required()
              .toConfigOptionParam('inputPath)
              .text("The path to the file that you want to upload."),
            opt[TargetName]("name")
              .required()
              .toConfigOptionParam('targetName)
              .text("The name of the target."),
            opt[TargetVersion]("version")
              .required()
              .toConfigOptionParam('targetVersion)
              .text("The version string of the target."),
            opt[Long]("timeout")
              .optional()
              .text("The timeout for the HTTP request of the upload, in seconds.")
              .toConfigParam('timeout),
          )
          .text("""Uploads a binary to the repository.
                  |Note that this will not make the binary available on its own.
                  |After the upload completes successfully, add it to your `targets.json` file using the `targets add-uploaded` command.
                  |""".stripMargin),

        cmd("delegations")
          .text("Manages the delegated Targets of the repository `targets.json` file.")
          .children(
            cmd("add")
              .toCommand(AddDelegationToTarget)
              .text("Adds a new delegation to the existing `targets.json` file.")
              .children(
                opt[DelegatedRoleName]("name").abbr("n").required()
                    .toConfigParam('delegationName).text("The name of the target."),
                opt[DelegatedPathPattern]("prefix").abbr("p").unbounded().minOccurs(1)
                  .action { (arg, c) => c.copy(delegatedPaths = arg :: c.delegatedPaths) }
                  .text("The path prefix of the image that you want to delegate."),
                opt[Path]("key").abbr("k").unbounded().minOccurs(1)
                  .text("The path to the public key that you want to add as a delegation key.")
                  .action { (arg, c) => c.copy(keyPaths = arg :: c.keyPaths) }
              )
          ),
        cmd("increment-version")
          .toCommand(IncrementTargetJsonVersion)
          .text("Increment version of target.json.")
      )

    note(" " + sys.props("line.separator"))

    cmd("export-credentials")
      .text("Exports settings and keys to the .zip file with your provisioning credentials.")
      .toCommand(ExportRepository)
      .children(
        repoNameOpt(this),
        manyKeyNamesOpt(this).text("The name of the file with your private and public keys that you want to export."),
        opt[Path]("output")
          .abbr("o")
          .text("The name of the file to which you want to export our credentials.")
          .required().toConfigOptionParam('outputPath)
      )

    note(" " + sys.props("line.separator"))

    cmd("get-targets")
      .toCommand(GetTargets)
      .text("Get a repo with targets and shows them on console")
      .hidden()
      .children(repoNameOpt(this))

    cmd("verify-root")
      .toCommand(VerifyRoot)
      .text("Verifies signatures for a signed `root.json` file")
      .hidden()
      .children(
        repoNameOpt(this),
        opt[Path]('i', "input").toConfigOptionParam('inputPath)
      )

    cmd("import-client-cert")
      .toCommand(ImportClientTls)
      .text("Imports a client certificate into the existing repository.")
      .children(
        repoNameOpt(this),
        opt[Path]('c', "client-cert")
          .text("The path to the valid PKCS#12 file that can be used to authenticate to a remote tuf repository.")
          .required()
          .toConfigOptionParam('inputPath),
        opt[Path]('s', "server-cert")
          .text("The path to the valid PKCS# file that can be used as a trust store to validate a remote `tuf` repository certificate.")
          .toConfigOptionParam('serverCertPath),
      )

    checkConfig { c =>
      c.command match {
        case RemoveRootKey if c.keyIds.isEmpty && c.keyNames.isEmpty =>
          "To remove a root key you need to specify at least one key ID or key name".asLeft
        case SignTargets | SignRoot if c.expireOn.isDefined && c.expireAfter.isDefined =>
          "The expiration date should be given with either `--expires` or `--expire-after`, not both".asLeft
        case _ =>
          Right(())
      }
    }
  }

  def validateRepoTypeCliArguments(repoPath: Eval[Path], config: Config): Either[String, TufServerType] = {
    val repoType =
      if (config.command == InitRepo)
        config.repoType.valueOrConfigError
      else
        TufRepo.readConfig(repoPath.value).map(_.repoServerType).getOrElse(RepoServer)

    // add checks depending on the repo type here

    repoType.asRight
  }

  val default = Config(command = Help)

  parser.parse(args, default) match {
    case Some(c) =>
      if (c.verbose) {
        setLoggingLevel(Level.DEBUG)
      }

      execute(c)
    case None =>
      sys.exit(-1)
  }

  def buildCommandHandler(config: Config): Eval[Future[Unit]] = {
    val repoPath = Eval.later(config.home.resolve(config.repoName.valueOrConfigError.value))
    val userKeyPath = config.userKeysPath.getOrElse(config.home.resolve("user-keys"))
    val userKeyStorage = CliKeyStorage.forUser(userKeyPath)

    validateRepoTypeCliArguments(repoPath, config) match {
      case Right(Director) => Eval.later {
        lazy val tufRepo = new DirectorRepo(repoPath.value)
        lazy val repoServer = TufRepoCliClient.forRepo[DirectorClient](tufRepo)
        lazy val delegationsServer = Future.failed(CliArgumentsException(s"$repoPath is a Director repository and does not support delegations"))

        CommandHandler.handle(tufRepo, repoServer, delegationsServer, userKeyStorage, config)
      }
      case Right(RepoServer) => Eval.later {
        lazy val tufRepo = new RepoServerRepo(repoPath.value)
        lazy val repoServer = TufRepoCliClient.forRepo[ReposerverClient](tufRepo)

        CommandHandler.handle(tufRepo, repoServer, repoServer, userKeyStorage, config)
      }

      case Left(err) => Eval.now {
        Future.failed(CliArgumentsException(err))
      }
    }
  }

  def execute(config: Config): Unit = {
    Files.createDirectories(config.home)

    try {
      Await.result(buildCommandHandler(config).value, Duration.Inf)
      System.exit(0) // Needed because AsyncHttpClient might still be using idle threads for it's connection pool
    } catch {
      case ex if CliHelp.explainError.isDefinedAt(ex) =>
        CliHelp.explainError(ex)
        sys.exit(1)

      case ex @ RoleChecksumNotValid =>
        log.error("Could not push targets", ex)
        sys.exit(2)

      case ex: Throwable =>
        log.error("An error occurred", ex)
        sys.exit(3)
    }
  }

  def setLoggingLevel(level: Level): Unit = {
    val logger: Logger = LoggerFactory.getLogger("com.advancedtelematic").asInstanceOf[Logger]
    logger.setLevel(level)
    val sttpLogger: Logger = LoggerFactory.getLogger("sttp.client.logging.slf4j").asInstanceOf[Logger]
    sttpLogger.setLevel(level)
  }
}
