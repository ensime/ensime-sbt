scalaVersion := "2.12.2"
libraryDependencies += "com.lihaoyi" %% "utest" % "0.4.7" % "test"
testFrameworks += new TestFramework("utest.runner.Framework")

val root = Project("ensime-run-main", file("."))
  .settings(
    ivyLoggingLevel := UpdateLogging.Quiet,
    fork := true,
    javaOptions += "-Dtesting_default_key1=default_value1",
    envVars += ("testing_default_key2", "default_value2"),
    ensimeDebuggingFlag := "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=",
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