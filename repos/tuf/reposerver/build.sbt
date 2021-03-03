libraryDependencies ++= {
  Seq(
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.338",
    "com.advancedtelematic" %% "libats-auth" % CustomSettings.libatsVersion.value
  )
}

mainClass in Compile := Some("com.advancedtelematic.tuf.reposerver.Boot")

fork := true
