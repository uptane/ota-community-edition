libraryDependencies ++= {
  val bouncyCastleV = "1.69"

  Seq(
    "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleV,
    "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleV,
    "net.i2p.crypto" % "eddsa" % "0.3.0",
    "com.softwaremill.sttp.client" %% "core" % "2.2.10",
    "com.softwaremill.sttp.client" %% "slf4j-backend" % "2.2.10",
    "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.2.10",
    "org.slf4j" % "slf4j-api" % "1.7.16" % "provided",
    "com.azure" % "azure-storage-blob" % "12.14.1",
    "com.azure" % "azure-identity" % "1.4.0"
  )
}
