import sbt.settingKey

object CustomSettings {
  val libatsVersion = settingKey[String]("libats version")
}
