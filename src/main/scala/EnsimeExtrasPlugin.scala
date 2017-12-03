// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import sbt.Defaults.{loadForParser => _}
import sbt.Keys._
import sbt._
import sbt.complete.{DefaultParsers, Parser}

object EnsimeExtrasKeys extends CompatExtrasKeys {

  case class JavaArgs(mainClass: String, envArgs: Map[String, String], jvmArgs: Seq[String], classArgs: Seq[String])
  case class LaunchConfig(name: String, javaArgs: JavaArgs)

  val ensimeDebuggingFlag = settingKey[String](
    "JVM flag to enable remote debugging of forked tasks."
  )
  val ensimeDebuggingPort = settingKey[Int](
    "Port for remote debugging of forked tasks."
  )

  val ensimeRunMain = inputKey[Unit](
    "Run user specified env/args/class/params (e.g. `ensimeRunMain FOO=BAR -Xmx2g foo.Bar baz')"
  )
  val ensimeRunDebug = inputKey[Unit](
    "Run user specified env/args/class/params with debugging flags added"
  )

  val ensimeLaunchConfigurations = settingKey[Seq[LaunchConfig]](
    "Named applications with canned env/args/class/params"
  )

  val ensimeLaunch = inputKey[Unit](
    "Launch a named application in ensimeLaunchConfigurations"
  )

  val ensimeCompileOnly = inputKey[Unit](
    "Compiles a single scala file"
  )
}

object EnsimeExtrasPlugin extends AutoPlugin with CompatExtras {
  import EnsimeExtrasKeys._

  override def requires = EnsimePlugin
  override def trigger = allRequirements
  val autoImport = EnsimeExtrasKeys

  private[ensime] val emptyExtraArgs = settingKey[Seq[String]](
    "Stub key for tasks that accepts extra args"
  )

  private[ensime] val emptyExtraEnv = settingKey[Map[String, String]](
    "Stub key for tasks that accepts extra env args"
  )

  override lazy val projectSettings = Seq(
    ensimeDebuggingFlag := "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=",
    ensimeDebuggingPort := 5005,
    emptyExtraArgs := Seq.empty[String],
    emptyExtraEnv := Map.empty[String, String],
    ensimeDebuggingArgs := Seq(s"${ensimeDebuggingFlag.value}${ensimeDebuggingPort.value}"),
    ensimeRunMain in Compile := parseAndRunMainWithStaticSettings(Compile).evaluated,
    ensimeRunDebug in Compile := parseAndRunMainWithDynamicSettings(
      Compile,
      extraArgs = ensimeDebuggingArgs
    ).evaluated,
    ensimeLaunchConfigurations := Nil,
    ensimeLaunch in Compile := launchTask(Compile).evaluated,
    aggregate in ensimeCompileOnly := false
  ) ++ Seq(Compile, Test).flatMap { config =>
    // WORKAROUND https://github.com/sbt/sbt/issues/2580
    inConfig(config) {
      Seq(
        ensimeCompileOnly := compileOnlyTask.evaluated,
        scalacOptions in ensimeCompileOnly := scalacOptions.value
      )
    }
  } ++ compatSettings

  val ensimeRunMainTaskParser: Parser[JavaArgs] = {
    import DefaultParsers._

    val envArg = ScalaID ~ "=" ~ NotSpace <~ Space map {
      case key ~ _ ~ value => (key, value)
    }
    val jvmArg = "-" ~ NotSpace <~ Space map {
      case a ~ b => a + b
    }
    val mainClassPart = identifier(ScalaIDChar, charClass(isIDChar))
    val mainClass = mainClassPart ~ ("." ~ mainClassPart).* map {
      case first ~ seq => first + seq.map(t => t._1 + t._2).mkString
    }
    (Space ~> envArg.*.map(_.toMap) ~ jvmArg.* ~ mainClass ~ spaceDelimited("<arg>")) map {
      case envArgs ~ jvmArgs ~ mainClass ~ classArgs => JavaArgs(mainClass, envArgs, jvmArgs, classArgs)
    }
  }

  private def runMain(
    javaArgs: JavaArgs,
    classpath: Keys.Classpath,
    javaOptions: Seq[String],
    envVars: Map[String, String],
    baseDir: File,
    log: Logger,
    extraArgs: Seq[String] = Seq.empty,
    extraEnv: Map[String, String] = Map.empty
  ): Unit = {
    val newJvmArgs = (javaOptions ++ extraArgs ++ javaArgs.jvmArgs).distinct
    val newEnvArgs = envVars ++ extraEnv ++ javaArgs.envArgs
    val options = ForkOptions(
      None,
      None,
      Vector.empty,
      workingDirectory = Some(baseDir),
      runJVMOptions = newJvmArgs.toVector,
      false,
      envVars = newEnvArgs
    )
    log.debug(s"launching $options ${javaArgs.mainClass} $newJvmArgs ${javaArgs.classArgs}")
    SbtHelper.reportError(new ForkRun(options).run(
      javaArgs.mainClass,
      Attributed.data(classpath),
      javaArgs.classArgs,
      log
    ))
  }

  def parseAndRunMainWithStaticSettings(
    config: Configuration,
    extraEnv: Map[String, String] = Map.empty,
    extraArgs: Seq[String] = Seq.empty
  ): Def.Initialize[InputTask[Unit]] = {
    val parser = (s: State) => ensimeRunMainTaskParser
    Def.inputTask {
      runMain(
        parser.parsed,
        (fullClasspath in config).value,
        (javaOptions in config).value,
        (envVars in config).value,
        (baseDirectory in config).value,
        (streams in config).value.log
      )
    }
  }

  def parseAndRunMainWithDynamicSettings(
    config: Configuration,
    extraEnv: SettingKey[Map[String, String]] = emptyExtraEnv,
    extraArgs: SettingKey[Seq[String]] = emptyExtraArgs
  ): Def.Initialize[InputTask[Unit]] = {
    val parser = (s: State) => ensimeRunMainTaskParser
    Def.inputTask {
      runMain(
        parser.parsed,
        (fullClasspath in config).value,
        (javaOptions in config).value,
        (envVars in config).value,
        (baseDirectory in config).value,
        (streams in config).value.log,
        extraArgs.value,
        extraEnv.value
      )
    }
  }

  def launchTask(config: Configuration, extraArgs: Seq[String] = Nil): Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val (name :: additionalParams) = Def.spaceDelimited().parsed
    val launcherByName = ensimeLaunchConfigurations.value.find(_.name == name)
    launcherByName.fold(
      streams.value.log.warn(s"No launch configuration '$name'")
    ) { launcher =>
        val args = launcher.javaArgs
        val options = ForkOptions(
          None,
          None,
          Vector.empty,
          workingDirectory = Some((baseDirectory in config).value),
          runJVMOptions = ((javaOptions in config).value ++ args.jvmArgs ++ extraArgs).toVector,
          false,
          envVars = (envVars in config).value ++ args.envArgs
        )
        streams.value.log.info(s"launching $options -cp CLASSPATH ${args.mainClass} ${args.classArgs ++ additionalParams}")
        SbtHelper.reportError(new ForkRun(options).run(
          args.mainClass,
          Attributed.data((fullClasspath in config).value),
          args.classArgs ++ additionalParams,
          streams.value.log
        ))
      }
  }

  private[ensime] val noChanges = new xsbti.compile.DependencyChanges {
    def isEmpty = true
    def modifiedBinaries = Array()
    def modifiedClasses = Array()
  }

  private[ensime] def fileInProject(arg: String, sourceDirs: Seq[File]): File = {
    val input = file(arg).getCanonicalFile
    val here = sourceDirs.exists { dir => input.getPath.startsWith(dir.getPath) }
    if (!here || !input.exists())
      throw new IllegalArgumentException(s"$arg not associated to $sourceDirs")

    if (!input.getName.endsWith(".scala"))
      throw new IllegalArgumentException(s"only .scala files are supported: $arg")

    input
  }
}
