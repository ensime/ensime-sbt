// Copyright (C) 2015 Vladimir Polushin
// License: Apache-2.0

import sbt._
import Keys._
import Def.Initialize
import org.ensime.EnsimeExtrasPlugin
import org.ensime.EnsimeExtrasKeys._

object runMainBuild extends Build {

  override lazy val settings = super.settings ++ Seq(
    scalaVersion := "2.10.6"
  )

  val root = Project("ensime-run-main", file("."))
    .settings(
      ivyLoggingLevel := UpdateLogging.Quiet,
      fork := true,
      javaOptions += "-Dtesting_default_key1=default_value1",
      envVars += ("testing_default_key2", "default_value2"),
      ensimeRunDebug <<= EnsimeExtrasPlugin.parseAndRunMainWithSettings(
        // suspend=n otherwise we hang forever...
        extraArgs = Seq(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
      ),
      ensimeLaunchConfigurations := Seq(
        LaunchConfig(
          "test",
          JavaArgs(
            "runEnsimeMain.printArgs",
            Map("testing_key1" -> "value1", "testing_key2" -> "value2"),
            Seq("-Dtesting_key3=value3", "-Xms2G", "-Xmx2G"),
            Seq("output_args8", "-arg1", "-arg2")
          )
        ),
        LaunchConfig(
          "extra",
          JavaArgs(
            "runEnsimeMain.printArgs",
            Map("testing_key1" -> "value1", "testing_key2" -> "value2"),
            Seq("-Dtesting_key3=value3", "-Xms2G", "-Xmx2G"),
            Seq("output_args10", "-arg1", "-arg2")
          )
        ),
        LaunchConfig(
          "largeMemory",
          JavaArgs(
            "runEnsimeMain.printArgs",
            Map.empty,
            Seq("-Xms4G", "-Xmx4G"),
            Seq("output_args9")
          )
        ),
        LaunchConfig(
          "hello",
          JavaArgs(
            "runEnsimeMain.printHello",
            Map.empty,
            Seq.empty,
            Seq("output_hello")
          )
        )
      )
    )
}
