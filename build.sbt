organization := "org.ensime"
name := "sbt-ensime"

sbtPlugin := true

sonatypeGithub := ("ensime", "ensime-sbt")
licenses := Seq(Apache2)

publishArtifact in (Compile, packageDoc) := false
publishArtifact in packageDoc := false

enablePlugins(ShadingPlugin)
shadingNamespace := "ensime.shaded"
publish := publish.in(Shading).value
publishLocal := publishLocal.in(Shading).value

inConfig(Shading)(com.typesafe.sbt.pgp.PgpSettings.projectSettings)
ShadingPlugin.projectSettings // breaks without this!
PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value
PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value

scalacOptions += "-language:postfixOps"

libraryDependencies ++= Seq(
  // intentionally old version of scalariform: do not force an upgrade upon users
  "org.scalariform" %% "scalariform" % "0.1.4",
  // shade coursier, i.e. don't force binary compatibility on downstream
  "io.get-coursier" %% "coursier-cache" % "1.0.0-RC2" % "shaded",
  // directly depending on some of coursier's transitives (not shading)
  // https://github.com/alexarchambault/coursier/issues/25
  "org.scalamacros" %% "quasiquotes" % "2.1.0",
  "org.scalaz" %% "scalaz-concurrent" % "7.2.13",
  "org.jsoup" % "jsoup" % "1.10.2"
)

// sbt-shading needs custom slf4j jars
excludeDependencies := Nil

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.src=" + sys.props("user.dir"),
  "-Dplugin.version=" + version.value,
  // .jvmopts is ignored, simulate here
  "-XX:MaxPermSize=256m", "-Xmx2g", "-Xss2m"
)
