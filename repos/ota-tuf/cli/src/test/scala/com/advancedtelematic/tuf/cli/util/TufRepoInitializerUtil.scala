package com.advancedtelematic.tuf.cli.util

import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit

import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.libtuf.data.ClientDataType.{RoleKeys, RootRole}
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, RoleType}
import com.advancedtelematic.tuf.cli.DataType.{KeyName, RepoConfig, RepoName}
import com.advancedtelematic.tuf.cli.repo.{DirectorRepo, RepoServerRepo, TufRepo}

import scala.concurrent.ExecutionContext.Implicits.global

object TufRepoInitializerUtil {

  trait TufRepoBuilder[T <: TufRepo[_]] {
    def apply(): T
  }

  def initRepo[T <: TufRepo[_]]()(implicit builder: TufRepoBuilder[T]): T =
    initRepo[T](KeyType.default)

  def initRepo[T <: TufRepo[_]](keyType: KeyType)(implicit builder: TufRepoBuilder[T]): T = {
    val repo = builder()
    val rootKeys = repo.genKeys(KeyName("root"), keyType).get
    val targetKeys = repo.genKeys(KeyName("target"), keyType).get

    val keys = Map(rootKeys.pubkey.id -> rootKeys.pubkey, targetKeys.pubkey.id -> targetKeys.pubkey)

    val roles = Map(
      RoleType.ROOT -> RoleKeys(Seq(rootKeys.pubkey.id), threshold = 1),
      RoleType.TARGETS -> RoleKeys(Seq(targetKeys.pubkey.id), threshold = 1)
    )

    val rootRole = RootRole(keys, roles, version = 1, Instant.now.plus(365, ChronoUnit.DAYS))

    repo.writeUnsignedRole(rootRole)

    repo
  }

  implicit val reposerverRepoBuilder: TufRepoBuilder[RepoServerRepo] = () => {
    val repo = new RepoServerRepo(
      Files.createTempDirectory(s"tuf-repo-${RandomNames()}").resolve("repo")
    )
    repo.initRepoDirs().get
    repo.initTargets(11, Instant.now).get
    repo
  }

  implicit val directorRepoBuilder: TufRepoBuilder[DirectorRepo] = () => {
    val repo = new DirectorRepo(
      Files.createTempDirectory(s"director-repo${RandomNames()}").resolve("repo")
    )
    repo.initRepoDirs().get
    repo
  }

}
