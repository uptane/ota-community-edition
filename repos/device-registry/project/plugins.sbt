addSbtPlugin("com.typesafe.sbt"  % "sbt-git"       % "0.9.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header"    % "4.0.0")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "1.3.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.4.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")
addSbtPlugin("com.github.mwz" % "sbt-sonar" % "2.1.1")

libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25" // Needed by sbt-git
