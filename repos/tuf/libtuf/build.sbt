libraryDependencies ++= {
  val bouncyCastleV = "1.59"

  Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleV,
    "net.i2p.crypto" % "eddsa" % "0.3.0-ATS",
    "com.softwaremill.sttp.client" %% "core" % "2.0.6",
    "com.softwaremill.sttp.client" %% "slf4j-backend" % "2.0.6",
    "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.0.6",
    "org.slf4j" % "slf4j-api" % "1.7.16" % "provided",
    "com.azure" % "azure-storage-blob" % "12.7.0",
    "com.azure" % "azure-identity" % "1.0.6",
  )
}
