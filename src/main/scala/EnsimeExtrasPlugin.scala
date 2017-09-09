// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import EnsimeKeys._
import sbt.Defaults.{loadForParser => _, toError => _, _}
import sbt._
import sbt.Keys._
import sbt.Tests.Execution
import sbt.complete.{DefaultParsers, Parser}

import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences._
import scalariform.parser.ScalaParserException
import sbt.complete.Parsers._
import sbt.complete.Parser._
import sbt.testing.{Framework, Runner}

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

  val ensimeTestOnlyDebug = inputKey[Unit](
    "The equivalent of ensimeRunDebug for testOnly command"
  )
}

object EnsimeExtrasPlugin extends AutoPlugin {
  import EnsimeExtrasKeys._

  override def requires = EnsimePlugin
  override def trigger = allRequirements
  val autoImport = EnsimeExtrasKeys

  private val emptyExtraArgs = settingKey[Seq[String]](
    "Stub key for tasks that accepts extra args"
  )

  private val emptyExtraEnv = settingKey[Map[String, String]](
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
    ensimeTestOnlyDebug in Test := testOnlyWithSettings(
      Test,
      extraArgs = ensimeDebuggingArgs
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

  val ensimeTestOnlyParser: Parser[(String, Seq[String])] = {
    import DefaultParsers._

    val selectTest = token(Space) ~> token(NotSpace & not("--", "-- in test"))
    val options = (token(Space) ~> token("--") ~> spaceDelimited("<option>")) ?? Nil
    selectTest ~ options
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
      runJVMOptions = newJvmArgs,
      envVars = newEnvArgs,
      workingDirectory = Some(baseDir)
    )
    log.debug(s"launching $options ${javaArgs.mainClass} $newJvmArgs ${javaArgs.classArgs}")
    new ForkRun(options).run(
      javaArgs.mainClass,
      Attributed.data(classpath),
      javaArgs.classArgs,
      log
    ).foreach(sys.error)
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
          runJVMOptions = (javaOptions in config).value ++ args.jvmArgs ++ extraArgs,
          envVars = (envVars in config).value ++ args.envArgs,
          workingDirectory = Some((baseDirectory in config).value)
        )
        streams.value.log.info(s"launching $options -cp CLASSPATH ${args.mainClass} ${args.classArgs ++ additionalParams}")
        new ForkRun(options).run(
          args.mainClass,
          Attributed.data((fullClasspath in config).value),
          args.classArgs ++ additionalParams,
          streams.value.log
        ).foreach(sys.error)
      }
  }

  private def testOnlyWithSettingsTask(
    settings: (String, Seq[String]),
    extraArgs: Seq[String],
    extraEnv: Map[String, String],
    config: Configuration,
    tests: Seq[TestDefinition],
    s: TaskStreams,
    st: State,
    exec: Execution,
    frameworks: Map[TestFramework, Framework],
    loader: ClassLoader,
    javaOps: Seq[String],
    eVars: Map[String, String],
    baseDir: File,
    cp: Classpath,
    trl: TestResultLogger,
    scoped: Def.ScopedKey[_]
  ) = {
    val (selected, frameworkOptions) = settings
    implicit val display = Project.showContextKey(st)
    val modifiedOpts = Tests.Argument(frameworkOptions: _*) +: exec.options
    val newConfig = exec.copy(options = modifiedOpts)

    val runners = createTestRunners(frameworks, loader, newConfig)
    val test = tests.find(_.name == selected)
    if (test.isDefined) {
      val forkOpts = ForkOptions(
        runJVMOptions = javaOps ++ extraArgs,
        envVars = eVars ++ extraEnv,
        workingDirectory = Some(baseDir)
      )
      val output = SbtHelper.constructForkTests(
        runners, List(test.get), newConfig, cp.files, forkOpts, s.log, Tags.ForkedTestGroup
      )

      val taskName = display(scoped)
      val processed = output.map(out => trl.run(s.log, out, taskName))
      Def.value(processed)
    } else {
      s.log.warn(s"There's no test with name $selected")
      Def.value(constant(()))
    }
  }

  private def testOnlyWithSettings(
    config: Configuration,
    extraArgs: SettingKey[Seq[String]] = emptyExtraArgs,
    extraEnv: SettingKey[Map[String, String]] = emptyExtraEnv
  ): Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      testOnlyWithSettingsTask(
        ensimeTestOnlyParser.parsed,
        extraArgs.value,
        extraEnv.value,
        config,
        (definedTests in config).value,
        (streams in config).value,
        (state in config).value,
        (testExecution in testQuick in config).value,
        (loadedTestFrameworks in config).value,
        (testLoader in config).value,
        (javaOptions in config).value,
        (envVars in config).value,
        (baseDirectory in config).value,
        (fullClasspath in config).value,
        (testResultLogger in config).value,
        (resolvedScoped in config).value
      )
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
    val baseOpts = (scalacOptions in ensimeCompileOnly).value
    val merrs = maxErrors.value
    val in = (compileInputs in compile).value
    val cs = compilers.value
    val s = streams.value

    val (extraOpts, files) = args.partition(_.startsWith("-"))
    val opts = baseOpts ++ extraOpts

    val input = files.map { arg =>
      fileInProject(arg, dirs.map(_.getCanonicalFile))
    }

    if (!out.exists()) IO.createDirectory(out)
    s.log.info(s"""Compiling $input with ${opts.mkString(" ")}""")

    cs.scalac(
      input, noChanges, cp.map(_.data) :+ out, out, opts,
      noopCallback, merrs, in.incSetup.cache, s.log
    )
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
