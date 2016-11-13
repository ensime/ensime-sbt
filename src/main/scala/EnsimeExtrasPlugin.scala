// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import EnsimeKeys._
import sbt._
import sbt.Keys._
import sbt.complete.{DefaultParsers, Parser}
import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences._
import scalariform.parser.ScalaParserException

import sbt.complete.Parsers._
import sbt.complete.Parser._

object EnsimeExtrasKeys {

  case class JavaArgs(mainClass: String, envArgs: Map[String, String], jvmArgs: Seq[String], classArgs: Seq[String])
  case class LaunchConfig(name: String, javaArgs: JavaArgs)

  val ensimeDebuggingFlag = settingKey[String](
    "JVM flag to enable remote debugging of forked tasks."
  )
  val ensimeDebuggingPort = settingKey[Int](
    "Port for remote debugging of forked tasks."
  )

  val ensimeDebuggingArgs = settingKey[Seq[String]](
    "Java args for for debugging"
  )

  val ensimeCompileOnly = inputKey[Unit](
    "Compiles a single scala file"
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
  val ensimeScalariformOnly = inputKey[Unit](
    "Formats a single scala file"
  )
}

object EnsimeExtrasPlugin extends AutoPlugin {
  import EnsimeExtrasKeys._

  override def requires = EnsimePlugin
  override def trigger = allRequirements
  val autoImport = EnsimeExtrasKeys

  override lazy val buildSettings = Seq(
    commands += Command.command("debugging", "", "Add debugging flags to all forked JVM processes.")(toggleDebugging(true)),
    commands += Command.command("debuggingOff", "", "Remove debugging flags from all forked JVM processes.")(toggleDebugging(false))
  )

  override lazy val projectSettings = Seq(
    ensimeDebuggingFlag := "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=",
    ensimeDebuggingPort := 5005,
    ensimeDebuggingArgs := Seq(s"${ensimeDebuggingFlag.value}${ensimeDebuggingPort.value}"),
    ensimeRunMain in Compile := parseAndRunMainWithStaticSettings(Compile).evaluated,
    ensimeRunDebug in Compile := parseAndRunMainWithDynamicSettings(
      Compile,
      extraArgsO = Some(ensimeDebuggingArgs)
    ).evaluated,
    ensimeLaunchConfigurations := Nil,
    ensimeLaunch in Compile := launchTask(Compile).evaluated,
    aggregate in ensimeScalariformOnly := false,
    ensimeScalariformOnly := scalariformOnlyTask.evaluated,
    aggregate in ensimeCompileOnly := false
  ) ++ Seq(Compile, Test).flatMap { config =>
      // WORKAROUND https://github.com/sbt/sbt/issues/2580
      inConfig(config) {
        Seq(
          ensimeCompileOnly := compileOnlyTask.evaluated,
          scalacOptions in ensimeCompileOnly := scalacOptions.value
        )
      }
    }

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
    s: Keys.TaskStreams,
    extraArgs: Seq[String] = Seq.empty,
    extraEnv: Map[String, String] = Map.empty
  ): Unit = {
    val cp = Attributed.data(classpath)
    val newJvmArgs = (javaOptions ++ extraArgs ++ javaArgs.jvmArgs).distinct
    val newEnvArgs = envVars ++ extraEnv ++ javaArgs.envArgs
    val options = ForkOptions(
      runJVMOptions = newJvmArgs,
      envVars = newEnvArgs,
      workingDirectory = Some(baseDir)
    )
    s.log.debug(s"launching $options ${javaArgs.mainClass} $newJvmArgs ${javaArgs.classArgs}")
    toError(new ForkRun(options).run(
      javaArgs.mainClass,
      cp,
      javaArgs.classArgs,
      s.log
    ))
  }

  def parseAndRunMainWithStaticSettings(
    config: Configuration,
    extraEnv: Map[String, String] = Map.empty,
    extraArgs: Seq[String] = Seq.empty
  ): Def.Initialize[InputTask[Unit]] =
    InputTask((s: State) => ensimeRunMainTaskParser) {
      (argTask: TaskKey[JavaArgs]) =>
        (
          argTask,
          (fullClasspath in config),
          (javaOptions in config),
          (envVars in config),
          (baseDirectory in config),
          (streams in config)
        ).map {
            (args, classpath, javaOps, eVars, baseDir, s) =>
              runMain(args, classpath, javaOps, eVars, baseDir, s, extraArgs, extraEnv)
          }
    }

  def parseAndRunMainWithDynamicSettings(
    config: Configuration,
    extraEnvO: Option[SettingKey[Map[String, String]]] = None,
    extraArgsO: Option[SettingKey[Seq[String]]] = None
  ): Def.Initialize[InputTask[Unit]] =
    InputTask((s: State) => ensimeRunMainTaskParser) {
      (argTask: TaskKey[JavaArgs]) =>
        (extraArgsO, extraEnvO) match {
          case (Some(extraArgs), Some(extraEnv)) => (
            argTask,
            (fullClasspath in config),
            (javaOptions in config),
            (envVars in config),
            (baseDirectory in config),
            (streams in config),
            extraArgs,
            extraEnv
          ).map(
              (args, classpath, javaOps, eVars, baseDir, s, extArgs, extEnv) =>
                runMain(args, classpath, javaOps, eVars, baseDir, s, extArgs, extEnv)
            )
          case (Some(extraArgs), None) => (
            argTask,
            (fullClasspath in config),
            (javaOptions in config),
            (envVars in config),
            (baseDirectory in config),
            (streams in config),
            extraArgs
          ).map(
              (args, classpath, javaOps, eVars, baseDir, s, extArgs) =>
                runMain(args, classpath, javaOps, eVars, baseDir, s, extraArgs = extArgs)
            )
          case (None, Some(extraEnv)) => (
            argTask,
            (fullClasspath in config),
            (javaOptions in config),
            (envVars in config),
            (baseDirectory in config),
            (streams in config),
            extraEnv
          ).map(
              (args, classpath, javaOps, eVars, baseDir, s, extEnv) =>
                runMain(args, classpath, javaOps, eVars, baseDir, s, extraEnv = extEnv)
            )
          case (None, None) => (
            argTask,
            (fullClasspath in config),
            (javaOptions in config),
            (envVars in config),
            (baseDirectory in config),
            (streams in config)
          ).map(
              (args, classpath, javaOps, eVars, baseDir, s) =>
                runMain(args, classpath, javaOps, eVars, baseDir, s)
            )
        }
    }

  def launchTask(
    config: Configuration,
    extraArgs: Seq[String] = Nil
  ): Def.Initialize[InputTask[Unit]] =
    InputTask((s: State) => spaceDelimited("args")) {
      (argTask: TaskKey[Seq[String]]) =>
        (
          argTask,
          (ensimeLaunchConfigurations in config),
          (javaOptions in config),
          (envVars in config),
          (fullClasspath in config),
          streams
        ).map {
            case (taskName :: additionalParams, configs, javaOps, eVars, classpath, s) =>
              val launcherByName = configs.find(_.name == taskName)
              launcherByName.fold(
                s.log.warn(s"No launch configuration '$taskName'")
              ) { launcher =>
                  val args = launcher.javaArgs
                  val options = ForkOptions(
                    runJVMOptions = javaOps ++ args.jvmArgs ++ extraArgs,
                    envVars = eVars ++ args.envArgs
                  )
                  val cp = Attributed.data(classpath)
                  s.log.info(s"launching $options -cp CLASSPATH ${args.mainClass} ${args.classArgs ++ additionalParams}")
                  toError(
                    new ForkRun(options).run(
                      args.mainClass,
                      cp,
                      args.classArgs ++ additionalParams,
                      s.log
                    )
                  )
                }
          }
    }

  private val noChanges = new xsbti.compile.DependencyChanges {
    def isEmpty = true
    def modifiedBinaries = Array()
    def modifiedClasses = Array()
  }

  private object noopCallback extends xsbti.AnalysisCallback {
    val includeSynthToNameHashing: Boolean = true
    override val nameHashing: Boolean = true
    def beginSource(source: File): Unit = {}
    def generatedClass(source: File, module: File, name: String): Unit = {}
    def api(sourceFile: File, source: xsbti.api.SourceAPI): Unit = {}
    def sourceDependency(dependsOn: File, source: File, publicInherited: Boolean): Unit = {}
    def binaryDependency(binary: File, name: String, source: File, publicInherited: Boolean): Unit = {}
    def endSource(sourcePath: File): Unit = {}
    def problem(what: String, pos: xsbti.Position, msg: String, severity: xsbti.Severity, reported: Boolean): Unit = {}
    def usedName(sourceFile: File, names: String): Unit = {}
    override def binaryDependency(file: File, s: String, file1: File, dependencyContext: xsbti.DependencyContext): Unit = {}
    override def sourceDependency(file: File, file1: File, dependencyContext: xsbti.DependencyContext): Unit = {}
  }

  def compileOnlyTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val args = Def.spaceDelimited().parsed
    val dirs = sourceDirectories.value
    val cp = dependencyClasspath.value
    val out = classDirectory.value
    val opts = (scalacOptions in ensimeCompileOnly).value
    val merrs = maxErrors.value
    val in = (compileInputs in compile).value
    val cs = compilers.value
    val s = streams.value

    if (args.isEmpty) throw new IllegalArgumentException("needs a file")
    args.foreach { arg =>
      val input: File = fileInProject(arg, dirs.map(_.getCanonicalFile))

      if (!out.exists()) IO.createDirectory(out)
      s.log.info(s"""Compiling $input with ${opts.mkString(" ")}""")

      cs.scalac(
        Seq(input), noChanges, cp.map(_.data) :+ out, out, opts,
        noopCallback, merrs, in.incSetup.cache, s.log
      )
    }
  }

  // it would be good if debuggingOff was automatically triggered
  // https://stackoverflow.com/questions/32350617
  def toggleDebugging(enable: Boolean): State => State = { implicit state: State =>
    import CommandSupport._
    val extracted = Project.extract(state)

    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

    if (enable) {
      val port = ensimeDebuggingPort.gimme
      log.warn(s"Enabling debugging for all forked processes on port $port")
      log.info("Only one process can use the port and it will await a connection before proceeding.")
    }

    val newSettings = extracted.structure.allProjectRefs map { proj =>
      val orig = (javaOptions in proj).run
      val debugFlags = ((ensimeDebuggingFlag in proj).gimme + (ensimeDebuggingPort in proj).gimme)
      val withoutDebug = orig.diff(List(debugFlags))
      val withDebug = withoutDebug :+ debugFlags
      val rewritten = if (enable) withDebug else withoutDebug

      (javaOptions in proj) := rewritten
    }
    extracted.append(newSettings, state)
  }

  // exploiting a single namespace to workaround https://github.com/ensime/ensime-sbt/issues/148
  private val scalariformPreferences = settingKey[IFormattingPreferences](
    "Scalariform formatting preferences, e.g. indentation"
  )

  def scalariformOnlyTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val files = Def.spaceDelimited().parsed
    // WORKAROUND https://github.com/ensime/ensime-sbt/issues/148
    val preferences = scalariformPreferences.?.value.getOrElse(FormattingPreferences())

    val version = scalaVersion.value
    val s = streams.value

    files.foreach { arg =>
      val input: File = file(arg) // don't demand it to be in a source dir
      s.log.info(s"Formatting $input")
      val contents = IO.read(input)
      val formatted = ScalaFormatter.format(
        contents,
        preferences,
        scalaVersion = version split "-" head
      )
      if (formatted != contents) IO.write(input, formatted)
    }
  }

  private def fileInProject(arg: String, sourceDirs: Seq[File]): File = {
    val input = file(arg).getCanonicalFile
    val here = sourceDirs.exists { dir => input.getPath.startsWith(dir.getPath) }
    if (!here || !input.exists())
      throw new IllegalArgumentException(s"$arg not associated to $sourceDirs")

    if (!input.getName.endsWith(".scala"))
      throw new IllegalArgumentException(s"only .scala files are supported: $arg")

    input
  }

}
