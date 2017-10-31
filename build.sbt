inThisBuild(
  Seq(
    organization := "org.ensime",
    sonatypeGithost := (Github, "ensime", "ensime-sbt"),
    licenses := Seq(Apache2)
  )
)

name := "sbt-ensime"
sbtPlugin := true

scalacOptions += "-language:postfixOps"

enablePlugins(ShadingPlugin)
shadingNamespace := "ensime.shaded"
publish := publish.in(Shading).value
publishLocal := publishLocal.in(Shading).value
inConfig(Shading)(com.typesafe.sbt.pgp.PgpSettings.projectSettings)
ShadingPlugin.projectSettings // breaks without this!
PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value
PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
shadeNamespaces ++= Set("coursier", "scalaz")

libraryDependencies ++= Seq(
  // shade coursier, i.e. don't force binary compatibility on downstream
  "io.get-coursier" %% "coursier-cache" % "1.0.0-RC12" % "shaded"
) ++ {
  // coursier needs to know about unshaded things...
  val currentSbtVersion = (sbtVersion in pluginCrossBuild).value
  if (currentSbtVersion.startsWith("0.13"))
    Seq("org.scalamacros" %% "quasiquotes" % "2.1.0")
  else
    Seq(
      "org.scala-lang" % "scala-library" % scalaVersion.value,
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
    )
}

scriptedSettings
scriptedBufferLog := false
scriptedLaunchOpts := Seq(
  "-Dplugin.version=" + version.value,
  "-Dplugin.test.directory=" + sbtTestDirectory.value,
  // .jvmopts is ignored, simulate here
  "-Xmx2g", "-Xss2m"
)
sbtTestDirectory := {
  val currentSbtVersion = (sbtVersion in pluginCrossBuild).value
  CrossVersion.partialVersion(currentSbtVersion) match {
    case Some((0, 13)) => sourceDirectory.value / "sbt-test-0.13"
    case Some((1, _))  => sourceDirectory.value / "sbt-test-1.0"
    case _             => sys.error(s"Unsupported sbt version: $currentSbtVersion")
  }
}

scalaVersion in ThisBuild := "2.12.4"
sbtVersion in Global := "1.0.3"
crossSbtVersions := Seq("1.0.3", "0.13.16")
scalaCompilerBridgeSource := {
  val sv = appConfiguration.value.provider.id.version
  ("org.scala-sbt" % "compiler-interface" % sv % "component").sources
}

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.0.0",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)
