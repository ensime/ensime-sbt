// sbt-ensime is needed for the integration tests
addSbtPlugin("org.ensime" % "sbt-ensime" % System.getProperty("plugin.version"))

// BUG https://github.com/sbt/sbt-header/issues/31
//addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.0")

// not working on Windows https://github.com/sbt/sbt/issues/1952
//addMavenResolverPlugin

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.5.1")

// waiting for 1.3.6 https://github.com/scoverage/sbt-scoverage/issues/153
// addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.3.5")
// sbt-coveralls needs a new release
// https://github.com/scoverage/sbt-coveralls/issues/52
//addSbtPlugin("org.scoverage" %% "sbt-coveralls" % "1.0.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4" exclude("org.apache.maven", "maven-plugin-api"))

scalacOptions in Compile ++= Seq("-feature", "-deprecation")

// sbt, STFU...
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.0")

// to match sbt-ensime's CI
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC2")

