package org.ensime

import sbt.Defaults.createTestRunners
import sbt.Keys._
import sbt.Tests.Execution
import sbt._
import sbt.complete.{DefaultParsers, Parser}
import sbt.testing.Framework

trait CompatExtrasKeys {
  val ensimeTestOnlyDebug = inputKey[Unit](
    "The equivalent of ensimeRunDebug for testOnly command"
  )

  val ensimeCompileOnly = inputKey[Unit](
    "Compiles a single scala file"
  )

  val ensimeDebuggingArgs = settingKey[Seq[String]](
    "Java args for for debugging"
  )

}

object CompatExtrasKeysHack extends CompatExtrasKeys

trait CompatExtras {
  import CompatExtrasKeysHack._

  def compatSettings: Seq[Setting[_]] = Seq(
    ensimeTestOnlyDebug in Test := testOnlyWithSettings(
      Test,
      extraArgs = ensimeDebuggingArgs
    ).evaluated,
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

  val ensimeTestOnlyParser: Parser[(String, Seq[String])] = {
    import DefaultParsers._

    val selectTest = token(Space) ~> token(NotSpace & not("--", "-- in test"))
    val options = (token(Space) ~> token("--") ~> spaceDelimited("<option>")) ?? Nil
    selectTest ~ options
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
        None,
        None,
        Vector.empty,
        workingDirectory = Some(baseDir),
        runJVMOptions = (javaOps ++ extraArgs).toVector,
        false,
        envVars = eVars ++ extraEnv
      )
      val output = SbtHelper.constructForkTests(
        runners, List(test.get), newConfig, cp.files, forkOpts, s.log, Tags.ForkedTestGroup
      )

      val taskName = SbtHelper.showShow(display, scoped)
      val processed = output.map(out => trl.run(s.log, out, taskName))
      Def.value(processed)
    } else {
      s.log.warn(s"There's no test with name $selected")
      Def.value(constant(()))
    }
  }

    private def testOnlyWithSettings(
      config: Configuration,
      extraArgs: SettingKey[Seq[String]] = EnsimeExtrasPlugin.emptyExtraArgs,
      extraEnv: SettingKey[Map[String, String]] = EnsimeExtrasPlugin.emptyExtraEnv
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
