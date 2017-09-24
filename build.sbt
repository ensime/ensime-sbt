organization := "org.ensime"
name := "sbt-ensime"

sbtPlugin := true

sonatypeGithub := ("ensime", "ensime-sbt")
licenses := Seq(Apache2)

scalacOptions += "-language:postfixOps"

libraryDependencies += "io.get-coursier" %% "coursier-cache" % "1.0.0-RC12"

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

// from https://github.com/coursier/coursier/issues/650
sbtLauncher := {
  val rep = update
    .value
    .configuration(ScriptedPlugin.scriptedLaunchConf.name)
    .getOrElse(sys.error(s"Configuration ${ScriptedPlugin.scriptedLaunchConf.name} not found"))

  val org = "org.scala-sbt"
  val name = "sbt-launch"

  val (_, jar) = rep
    .modules
    .find { modRep =>
      modRep.module.organization == org && modRep.module.name == name
    }
    .getOrElse {
      sys.error(s"Module $org:$name not found in configuration ${ScriptedPlugin.scriptedLaunchConf.name}")
    }
    .artifacts
    .headOption
    .getOrElse {
      sys.error(s"No artifacts found for module $org:$name in configuration ${ScriptedPlugin.scriptedLaunchConf.name}")
    }

  jar
}

scalaVersion in ThisBuild := "2.12.3"
sbtVersion in Global := "1.0.2"
crossSbtVersions := Seq("1.0.2", "0.13.16")
scalaCompilerBridgeSource := {
  val sv = appConfiguration.value.provider.id.version
  ("org.scala-sbt" % "compiler-interface" % sv % "component").sources
}

libraryDependencies += Defaults.sbtPluginExtra(
  "com.dwijnand" % "sbt-compat" % "1.0.0",
  (sbtBinaryVersion in pluginCrossBuild).value,
  (scalaBinaryVersion in update).value
)
