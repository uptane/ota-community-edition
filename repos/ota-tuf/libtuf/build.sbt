libraryDependencies ++= {
  val bouncyCastleV = "1.81"

  Seq(
    "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleV,
    "net.i2p" % "i2p" % "2.8.0",
    "com.softwaremill.sttp.client4" %% "core" % "4.0.9",
    "com.softwaremill.sttp.client4" %% "slf4j-backend" % "4.0.9",
    "org.slf4j" % "slf4j-api" % "1.7.16" % "provided",
    "com.azure" % "azure-storage-blob" % "12.30.1",
    "com.azure" % "azure-identity" % "1.16.2"
  )
}
