organization := "org.ensime"
name := "sbt-ensime"

sbtPlugin := true

sonatypeGithub := ("ensime", "ensime-sbt")
licenses := Seq(Apache2)

libraryDependencies ++= Seq(
  // intentionally old version of scalariform: do not force an upgrade upon users
  "org.scalariform" %% "scalariform" % "0.1.4",
  "io.get-coursier" %% "coursier-cache" % "1.0.0-M15-1" % "shaded",
  // directly depending on these so that they don't get shaded (scalaz brings like ~10k classes)
  "org.scalamacros" %% "quasiquotes" % "2.1.0",
  "org.scalaz" %% "scalaz-concurrent" % "7.2.7"
)

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.src=" + sys.props("user.dir"),
  "-Dplugin.version=" + version.value,
  // .jvmopts is ignored, simulate here
  "-XX:MaxPermSize=256m", "-Xmx2g", "-Xss2m"
)


enablePlugins(coursier.ShadingPlugin)
shadingNamespace := "ensime.shaded"

publish := publish.in(Shading).value
publishLocal := publishLocal.in(Shading).value
