// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import sbt._
import Keys._
import complete.{DefaultParsers, Parser}
import scalariform.formatter.ScalaFormatter
import scalariform.parser.ScalaParserException

import EnsimeKeys._

object EnsimeExtrasKeys {

  case class JavaArgs(mainClass: String, envArgs: Map[String, String], jvmArgs: Seq[String], classArgs: Seq[String])
  case class LaunchConfig(name: String, javaArgs: JavaArgs)

  val ensimeDebuggingFlag = settingKey[String](
    "JVM flag to enable remote debugging of forked tasks."
  )
  val ensimeDebuggingPort = settingKey[Int](
    "Port for remote debugging of forked tasks."
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
    ensimeRunMain <<= parseAndRunMainWithSettings(),
    ensimeRunDebug <<= parseAndRunMainWithSettings(
      // it would be good if this could reference Settings...
      extraArgs = Seq(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    ),
    ensimeLaunchConfigurations := Nil,
    ensimeLaunch in Compile <<= launchTask(Compile),
    aggregate in ensimeScalariformOnly := false,
    aggregate in ensimeCompileOnly := false
  ) ++ Seq(Compile, Test).flatMap { config =>
      // WORKAROUND https://github.com/sbt/sbt/issues/2580
      inConfig(config) {
        Seq(
          ensimeScalariformOnly <<= scalariformOnlyTask,
          ensimeCompileOnly <<= compileOnlyTask,
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

  def parseAndRunMainWithSettings(
    extraEnv: Map[String, String] = Map.empty,
    extraArgs: Seq[String] = Seq.empty
  ): Def.Initialize[InputTask[Unit]] = {
    val parser = (s: State) => ensimeRunMainTaskParser
    Def.inputTask {
      val givenArgs = parser.parsed
      val newJvmArgs = (javaOptions.value ++ extraArgs ++ givenArgs.jvmArgs).distinct
      val newEnvArgs = envVars.value ++ extraEnv ++ givenArgs.envArgs
      toError(new ForkRun(ForkOptions(runJVMOptions = newJvmArgs, envVars = newEnvArgs)).run(
        givenArgs.mainClass,
        Attributed.data((fullClasspath in Compile).value),
        givenArgs.classArgs,
        streams.value.log
      ))
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
          envVars = (envVars in config).value ++ args.envArgs
        )
        streams.value.log.info(s"launching $options -cp CLASSPATH ${args.mainClass} ${args.classArgs ++ additionalParams}")
        toError(
          new ForkRun(options).run(
            args.mainClass,
            Attributed.data((fullClasspath in config).value),
            args.classArgs ++ additionalParams,
            streams.value.log
          )
        )
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

  // DEPRECATED with no alternative https://github.com/sbt/sbt/issues/2417
  def compileOnlyTask: Def.Initialize[InputTask[Unit]] = InputTask(
    (s: State) => Def.spaceDelimited()
  ) { (argTask: TaskKey[Seq[String]]) =>
      (
        argTask,
        sourceDirectories,
        dependencyClasspath,
        classDirectory,
        scalacOptions in ensimeCompileOnly,
        maxErrors,
        compileInputs in compile,
        compilers,
        streams
      ).map { (args, dirs, cp, out, opts, merrs, in, cs, s) =>
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

  def scalariformOnlyTask: Def.Initialize[InputTask[Unit]] = InputTask(
    (s: State) => Def.spaceDelimited()
  ) { (argTask: TaskKey[Seq[String]]) =>
      (argTask, sourceDirectories, scalariformPreferences, scalaVersion, baseDirectory, streams).map {
        (files, dirs, preferences, version, base, s) =>
          if (files.isEmpty) throw new IllegalArgumentException("needs a file")
          files.foreach(arg => {
            val input: File = fileInProject(arg, (dirs.map(_.getCanonicalFile)) :+ new File(base.getPath + "/project"))

            try {
              val contents = IO.read(input)
              val formatted = ScalaFormatter.format(
                contents,
                preferences,
                scalaVersion = version split "-" head
              )
              if (formatted != contents) IO.write(input, formatted)
              s.log.info(s"Formatted $input")
            } catch {
              case e: ScalaParserException =>
                s.log.warn(s"Scalariform parser error for $input: $e.getMessage")
            }
          })
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
