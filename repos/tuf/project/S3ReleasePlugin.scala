package s3Release

import java.io.{BufferedInputStream, BufferedOutputStream}
import java.nio.file._

import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import net.virtualvoid.sbt.graph.DependencyGraphPlugin.autoImport._
import net.virtualvoid.sbt.graph.{ModuleGraph, ModuleId}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import sbt.Keys._
import sbt.{AutoPlugin, InputKey, Logger, SettingKey, TaskKey}
import sbt._

import scala.collection.JavaConverters._
import scala.collection.immutable.HashSet

object S3ReleasePlugin extends AutoPlugin {
  override lazy val projectSettings = s3ReleaseSettings

  object autoImport {
    lazy val s3UploadTask = InputKey[Unit]("s3release", "release artifacts to s3")
    lazy val s3DepsRelease = TaskKey[Unit]("s3depsRelease", "release dependencies sources to s3")
    lazy val s3Region = SettingKey[String]("s3Region")
    lazy val s3AccessKey = SettingKey[String]("s3AccessKey")
    lazy val s3SecretKey = SettingKey[String]("s3SecretKey")
    lazy val s3Bucket = SettingKey[String]("s3Bucket")
    lazy val s3Credentials = SettingKey[S3Credentials]("s3credentials")
  }

  import autoImport._

  private def readEnv(envKey: String, default: Option[String] = None): String =
    sys.env.get(envKey).orElse(default).getOrElse(s"<undefined env $envKey>")

  import sbt.complete.DefaultParsers._

  private def logTaskExceptions[T](log: Logger)(task: => T): T = try {
    task
  } catch  {
    case ex: Throwable =>
      log.error(s"Could not run task: ${ex.getLocalizedMessage}")
      throw ex
  }

  val s3ReleaseSettings = Seq (
    s3AccessKey := readEnv("AWS_ACCESS_KEY_ID"),
    s3SecretKey := readEnv("AWS_SECRET_ACCESS_KEY"),
    s3Bucket := readEnv("AWS_BUCKET_ID"),
    s3Region := readEnv("AWS_REGION", Option(Regions.EU_CENTRAL_1.getName)),
    s3Credentials := S3Credentials(s3AccessKey.value, s3SecretKey.value, s3Bucket.value, Regions.fromName(s3Region.value)),
    s3DepsRelease := logTaskExceptions(streams.value.log) {
      import sbt._
      val log = streams.value.log
      val graph = (moduleGraph in Compile in thisProject).value
      val upload = new S3Upload(log, s3Credentials.value)

      val depsUpload = new S3Dependenciesupload(log, upload)
      val versionName = s"${moduleName.value}-${version.value}"
      depsUpload.uploadAll(graph, versionName)
    },
    s3UploadTask := logTaskExceptions(streams.value.log) {
      val force = (token(Space) ~> token("force", "force release overwriting")).?.parsed.isDefined
      val log = streams.value.log
      val upload = new S3Upload(log, s3Credentials.value)
      val releaseUpload = new S3ReleaseUpload(log, upload)
      releaseUpload.uploadLatest(force)
    }
  )
}

class S3Dependenciesupload(log: Logger, upload: S3Upload) {
  def uploadAll(graph: ModuleGraph, version: String): Unit = {
    val out = Files.createTempDirectory("s3deps-compressed").resolve(version + "-dep-srcs.tgz")

    val dependenciesPaths = findDependencies(graph)
    createDependenciesTarball(dependenciesPaths, out)

    upload.uploadObject(Paths.get("deps-srcs"), out, force = false)

    log.info(s"Wrote dependencies tarball to $out")
  }

  private def isBlacklisted(moduleId: ModuleId): Boolean = {
    HashSet("libtuf", "cli").exists(moduleId.idString.contains)
  }

  private def findDependencies(graph:ModuleGraph): List[Path] = {
    graph.modules.values.flatMap { dependency ⇒
      val sourcesPath = dependency.jarFile.map { f ⇒
        Paths.get(f.getAbsolutePath
          .replace("/bundles/", "/srcs/")
          .replace("/jars/", "/srcs/")
          .replaceFirst(".jar$", "-sources.jar"))
      }

      sourcesPath match {
        case _ if isBlacklisted(dependency.id) ⇒
          log.info(s"Not adding ${dependency.id.name}, dependency is blacklisted for source publishing")
          None
        case Some(path) if Files.exists(path) ⇒
          log.info(s"Adding sources for ${dependency.id.name} ")
          Some(path)
        case Some(path) ⇒
          log.warn(s"No sources available for ${dependency.id.name} at path $path")
          None
        case None ⇒
          log.warn(s"No sources available for ${dependency.id.name}")
          None
      }
    }.toList
  }

  private def createDependenciesTarball(dependencies: List[Path], outFile: Path): Unit = {
    val out = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(Files.newOutputStream(outFile))))

    dependencies.foreach { path ⇒
      val entry = new TarArchiveEntry(path.getFileName.toString)
      entry.setSize(path.toFile.length())
      out.putArchiveEntry(entry)
      val is = new BufferedInputStream(Files.newInputStream(path))

      try
        IOUtils.copy(is, out)
      finally
        is.close()

      out.closeArchiveEntry()
    }

    out.close()
  }
}

class S3Upload(log: Logger, credentials: S3Credentials) {
  private lazy val s3Client =
    AmazonS3ClientBuilder.standard()
      .withCredentials(credentials)
      .withRegion(credentials.region)
      .build()

  def uploadObject(bucketDir: Path, path: Path, force: Boolean): Unit = {
    val fileName = bucketDir.resolve(path.getFileName.toString).toString
    val exists = s3Client.doesObjectExist(credentials.bucketId, fileName)

    if(exists && !force) {
      val url = s3Client.getUrl(credentials.bucketId, fileName)
      log.err(s"release already exists in s3: $url")
      throw new RuntimeException(s"release already exists in s3: $url")
    } else {
      if(force && exists)
        log.warn(s"Overwriting release $fileName")

      s3Client.putObject(credentials.bucketId, fileName, path.toFile)
      log.info(s"Uploaded file to s3 bucket ${credentials.bucketId}")
    }

    val url = s3Client.getUrl(credentials.bucketId, fileName)
    log.info(s"Url for $fileName release is $url")
  }
}

class S3ReleaseUpload(log: Logger, s3upload: S3Upload) {
  import java.nio.file.FileSystems

  def uploadLatest(force: Boolean = false): Unit = {
    val releasePath = findLatestRelease(log)
    log.info(s"Using release $releasePath")
    s3upload.uploadObject(Paths.get(""),  releasePath, force)
  }

  private def findLatestRelease(log: Logger): Path = {
    val matcher = FileSystems.getDefault.getPathMatcher("glob:**/*.tgz")

    val allReleases =
      Files.list(Paths.get("cli/target/universal/"))
        .iterator()
        .asScala
        .filter(matcher.matches)
        .toVector
        .sortBy(_.getFileName.toString)(Ordering[String].reverse)

    log.info(s"Available releases: $allReleases")

    allReleases.headOption.getOrElse(throw new Exception("Could not find release to push to s3"))
  }
}

case class S3Credentials(accessKey: String, secretKey: String, bucketId: String, region: Regions)
  extends AWSCredentials with AWSCredentialsProvider {

  override def getAWSAccessKeyId: String = accessKey

  override def getAWSSecretKey: String = secretKey

  override def refresh(): Unit = ()

  override def getCredentials: AWSCredentials = this
}
