organization := "org.ensime"
name := "sbt-ensime"

sbtPlugin := true

sonatypeGithub := ("ensime", "ensime-sbt")
licenses := Seq(Apache2)

// intentionally old version of scalariform: do not force an upgrade upon users
libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.4"
// scalap needed for :scala-compiler-jars in project/.ensime
libraryDependencies += "org.scala-lang" % "scalap" % scalaVersion.value

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.version=" + version.value,
  // .jvmopts is ignored, simulate here
  "-XX:MaxPermSize=256m", "-Xmx2g", "-Xss2m"
)
