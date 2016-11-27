organization := "org.ensime"
name := "sbt-ensime"

sbtPlugin := true

sonatypeGithub := ("ensime", "ensime-sbt")
licenses := Seq(Apache2)

// BLOCKED https://github.com/ensime/ensime-sbt/issues/270
scalacOptions -= "-Xfatal-warnings"

libraryDependencies ++= Seq(
  // intentionally old version of scalariform: do not force an upgrade upon users
  "org.scalariform" %% "scalariform" % "0.1.4",
  "io.get-coursier" %% "coursier" % "1.0.0-M15",
  "io.get-coursier" %% "coursier-cache" % "1.0.0-M15"
)

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.src=" + sys.props("user.dir"),
  "-Dplugin.version=" + version.value,
  // .jvmopts is ignored, simulate here
  "-XX:MaxPermSize=256m", "-Xmx2g", "-Xss2m"
)
