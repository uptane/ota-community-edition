import sbt.Keys._
import sbtbuildinfo._
import sbtbuildinfo.BuildInfoKeys._

object BuildInfoSettings {
  def apply(pkgName: String) = {
    Seq(
      buildInfoOptions += BuildInfoOption.ToMap,
      buildInfoOptions += BuildInfoOption.BuildTime,
      buildInfoObject := "AppBuildInfo",
      buildInfoPackage := pkgName,
      buildInfoUsePackageAsPath := true,
      buildInfoOptions += BuildInfoOption.Traits("com.advancedtelematic.libats.boot.VersionInfoProvider")
    )
  }
}
