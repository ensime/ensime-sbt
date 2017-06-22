organization := "org.ensime"
name := "sbt-ensime"

sbtPlugin := true

sonatypeGithub := ("ensime", "ensime-sbt")
licenses := Seq(Apache2)

enablePlugins(ShadingPlugin)
shadingNamespace := "ensime.shaded"
publish := publish.in(Shading).value
publishLocal := publishLocal.in(Shading).value
inConfig(Shading)(com.typesafe.sbt.pgp.PgpSettings.projectSettings)
ShadingPlugin.projectSettings // breaks without this!
PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value
PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
shadeNamespaces ++= Set("coursier", "scalaz")

scalacOptions += "-language:postfixOps"

libraryDependencies ++= Seq(
  // intentionally old version of scalariform: do not force an upgrade upon users
  "org.scalariform" %% "scalariform" % "0.1.4",
  // shade coursier, i.e. don't force binary compatibility on downstream
  "io.get-coursier" %% "coursier-cache" % "1.0.0-RC5" % "shaded",
  // coursier still needs non-shaded quasiquotes
  "org.scalamacros" %% "quasiquotes" % "2.1.0"
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
