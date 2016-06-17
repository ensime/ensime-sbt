// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import Imports._
import SExpFormatter._
import java.io.FileNotFoundException
import java.lang.management.ManagementFactory
import sbt._
import sbt.IO._
import sbt.Keys._
import sbt.complete.Parsers._
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util._
import scalariform.formatter.preferences._

/**
 * Conventional way to define importable keys for an AutoPlugin.
 * Note that EnsimePlugin.autoImport == Imports
 */
object Imports {
  object EnsimeKeys {
    val ensimeCompileOnly = InputKey[Unit]("ensimeCompileOnly", "Compiles a single scala file")

    // for ensimeConfig
    val ensimeName = SettingKey[String](
      "Name of the ENSIME project"
    )
    val ensimeCompilerArgs = TaskKey[Seq[String]](
      "Arguments for the presentation compiler, extracted from the compiler flags."
    )

    val ensimeAdditionalCompilerArgs = TaskKey[Seq[String]](
      "Additional arguments for the presentation compiler."
    )
    val ensimeJavaFlags = TaskKey[Seq[String]](
      "Flags to be passed to ENSIME JVM process."
    )
    val ensimeScalariform = settingKey[IFormattingPreferences](
      "Scalariform formatting preferences to use in ENSIME."
    )

    val ensimeUseTarget = taskKey[Option[File]](
      "Use a calculated jar instead of the class directory. " +
        "Note that `proj/compile` does not produce the jar, change your workflow to use `proj/packageBin`."
    )

    val ensimeDisableSourceMonitoring = settingKey[Boolean](
      "Workaround temporary performance problems on large projects."
    )
    val ensimeDisableClassMonitoring = settingKey[Boolean](
      "Workaround temporary performance problems on large projects."
    )

    // used to start the REPL and assembly jar bundles of ensime-server.
    // intransitive because we don't need parser combinators, scala.xml or jline
    val ensimeScalaCompilerJarModuleIDs = settingKey[Seq[ModuleID]](
      "The artefacts to resolve for :scala-compiler-jars in ensimeConfig."
    )

    // for ensimeConfigProject
    val ensimeCompilerProjectArgs = TaskKey[Seq[String]](
      "Arguments for the project definition presentation compiler (not possible to extract)."
    )
    val ensimeAdditionalProjectCompilerArgs = TaskKey[Seq[String]](
      "Additional arguments for the project definition presentation compiler."
    )

    // for debugging
    val ensimeDebuggingFlag = SettingKey[String](
      "JVM flag to enable remote debugging of forked tasks."
    )
    val ensimeDebuggingPort = SettingKey[Int](
      "Port for remote debugging of forked tasks."
    )

    val ensimeUnmanagedSourceArchives = SettingKey[Seq[File]](
      "Source jars (and zips) to complement unmanagedClasspath. May be set for the project and its submodules."
    )
    val ensimeUnmanagedJavadocArchives = SettingKey[Seq[File]](
      "Documentation jars (and zips) to complement unmanagedClasspath. May only be set for submodules."
    )

    val ensimeMegaUpdate = TaskKey[Map[ProjectRef, (UpdateReport, UpdateReport)]](
      "Runs the aggregated UpdateReport for `update' and `updateClassifiers' respectively."
    )
    val ensimeConfigTransformer = settingKey[EnsimeConfig => EnsimeConfig](
      "A function that is applied to a generated ENSIME configuration. This transformer function " +
        "can be used to add or filter any resulting config and can serve as a hook for other plugins."
    )
    val ensimeConfigTransformerProject = settingKey[EnsimeConfig => EnsimeConfig](
      "A function that is applied to a generated ENSIME project config. Equivalent of 'configTransformer' task, " +
        "on the build level."
    )
  }
}

object EnsimePlugin extends AutoPlugin {
  import CommandSupport._

  // ensures compiler settings are loaded before us
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = Imports

  val EnsimeInternal = config("ensime-internal").hide

  override lazy val buildSettings = Seq(
    commands += Command.args("ensimeConfig", ("", ""), "Generate a .ensime for the project.", "proj1 proj2")(ensimeConfig),
    commands += Command.command("ensimeConfigProject", "", "Generate a project/.ensime for the project definition.")(ensimeConfigProject),
    commands += Command.command("debugging", "", "Add debugging flags to all forked JVM processes.")(toggleDebugging(true)),
    commands += Command.command("debuggingOff", "", "Remove debugging flags from all forked JVM processes.")(toggleDebugging(false)),

    // deprecating in 1.0
    commands += Command.args("gen-ensime", ("", ""), "Generate a .ensime for the project.", "proj1 proj2")(genEnsime),
    commands += Command.command("gen-ensime-project", "", "Generate a project/.ensime for the project definition.")(genEnsimeProject),
    commands += Command.command("debugging-off", "", "Remove debugging flags from all forked JVM processes.")(debugging_off(false)),

    EnsimeKeys.ensimeDebuggingFlag := "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=",
    EnsimeKeys.ensimeDebuggingPort := 5005,
    EnsimeKeys.ensimeJavaFlags := JavaFlags,
    EnsimeKeys.ensimeCompilerProjectArgs := Seq(), // https://github.com/ensime/ensime-sbt/issues/98
    EnsimeKeys.ensimeAdditionalProjectCompilerArgs := defaultCompilerFlags(Properties.versionNumberString),
    EnsimeKeys.ensimeScalariform := FormattingPreferences(),
    EnsimeKeys.ensimeDisableSourceMonitoring := false,
    EnsimeKeys.ensimeDisableClassMonitoring := false,
    EnsimeKeys.ensimeMegaUpdate <<= Keys.state.flatMap { implicit s =>

      def checkCoursier(): Unit = {
        val structure = Project.extract(s).structure
        val plugins = structure.allProjects.flatMap(_.autoPlugins).map(_.getClass.getName)
        val usesCoursier = plugins.exists(_.contains("CoursierPlugin"))
        if (!usesCoursier) {
          log.warn(
            "SBT is using ivy to resolve dependencies and is known to be slow. " +
              "Coursier is recommended: http://get-coursier.io"
          )
        }
      }

      val projs = Project.structure(s).allProjectRefs
      log.info("ENSIME update.")
      for {
        updateReport <- update.forAllProjects(s, projs)
        _ = checkCoursier()
        updateClassifiersReport <- updateClassifiers.forAllProjects(s, projs)
      } yield {
        projs.map { p =>
          (p, (updateReport(p), updateClassifiersReport(p)))
        }.toMap
      }
    }
  )

  override lazy val projectSettings = Seq(
    EnsimeKeys.ensimeUnmanagedSourceArchives := Nil,
    EnsimeKeys.ensimeUnmanagedJavadocArchives := Nil,
    EnsimeKeys.ensimeConfigTransformer := identity,
    EnsimeKeys.ensimeConfigTransformerProject := identity,
    EnsimeKeys.ensimeUseTarget := None,

    // Even though these are Global in ENSIME (until
    // https://github.com/ensime/ensime-server/issues/1152) if these
    // are in buildSettings, then build.sbt projects don't see the
    // right scalaVersion unless they used `in ThisBuild`, which is
    // too much to ask of .sbt users.
    EnsimeKeys.ensimeAdditionalCompilerArgs := defaultCompilerFlags((scalaVersion).value),
    EnsimeKeys.ensimeCompilerArgs := (scalacOptions in Compile).value,

    ivyConfigurations += EnsimeInternal,
    // must be here where the ivy config is defined
    EnsimeKeys.ensimeScalaCompilerJarModuleIDs := {
      if (organization.value == scalaOrganization.value) Nil
      else Seq(
        scalaOrganization.value % "scala-compiler" % scalaVersion.value,
        scalaOrganization.value % "scala-library" % scalaVersion.value,
        scalaOrganization.value % "scala-reflect" % scalaVersion.value,
        scalaOrganization.value % "scalap" % scalaVersion.value
      ).map(_ % EnsimeInternal.name intransitive ())
    },
    libraryDependencies ++= EnsimeKeys.ensimeScalaCompilerJarModuleIDs.value,
    aggregate in EnsimeKeys.ensimeCompileOnly := false
  ) ++ Seq(Compile, Test).flatMap { config =>
      // WORKAROUND https://github.com/sbt/sbt/issues/2580
      inConfig(config) {
        Seq(
          EnsimeKeys.ensimeCompileOnly <<= compileOnlyTask,
          scalacOptions in EnsimeKeys.ensimeCompileOnly := scalacOptions.value
        )
      }
    }

  def defaultCompilerFlags(scalaVersion: String): Seq[String] = Seq(
    "-feature",
    "-deprecation",
    "-Xlint",
    "-Yinline-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    //"-Ywarn-value-discard", // more annoying than useful
    "-Xfuture"
  ) ++ {
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, v)) if v >= 11 => Seq("-Ywarn-unused-import", "-Ymacro-expand:discard")
        case _                       => Nil
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
        scalacOptions in EnsimeKeys.ensimeCompileOnly,
        maxErrors,
        compileInputs in compile,
        compilers,
        streams
      ).map { (args, dirs, cp, out, opts, merrs, in, cs, s) =>
          if (args.isEmpty) throw new IllegalArgumentException("needs a file")
          args.foreach { arg =>
            val input: File = file(arg).getCanonicalFile
            val sourceDirs = dirs.map(_.getCanonicalFile)

            val here = sourceDirs.exists { dir => input.getPath.startsWith(dir.getPath) }
            if (!here || !input.exists())
              throw new IllegalArgumentException(s"$arg not associated to $sourceDirs")

            // there is no reason why we couldn't compileOnly other
            // languages, but they would require explicit support.
            // Java shouldn't be too hard if somebody wants it.
            if (!input.getName.endsWith(".scala"))
              throw new IllegalArgumentException(s"only .scala files are supported: $arg")

            if (!out.exists()) IO.createDirectory(out)
            s.log.info(s"""Compiling $input with ${opts.mkString(" ")}""")

            cs.scalac(
              Seq(input), noChanges, cp.map(_.data) :+ out, out, opts,
              noopCallback, merrs, in.incSetup.cache, s.log
            )
          }
        }
    }

  private def logDeprecatedCommand(old: String, replacement: String): State => State = {
    state =>
      state.globalLogging.full.error(s"`$old' is deprecated and will be removed. Use `$replacement' instead.")
      state
  }
  def debugging_off(enable: Boolean): State => State = toggleDebugging(enable) andThen logDeprecatedCommand("debugging-off", "debuggingOff")
  def genEnsime: (State, Seq[String]) => State = { (state: State, args: Seq[String]) =>
    ensimeConfig(state, args)
    logDeprecatedCommand("gen-ensime", "ensimeConfig")(state)
  }
  def genEnsimeProject: State => State = ensimeConfigProject andThen logDeprecatedCommand("gen-ensime-project", "ensimeConfigProject")

  // it would be good if debuggingOff was automatically triggered
  // https://stackoverflow.com/questions/32350617
  def toggleDebugging(enable: Boolean): State => State = { implicit state: State =>
    val extracted = Project.extract(state)

    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

    if (enable) {
      val port = EnsimeKeys.ensimeDebuggingPort.gimme
      log.warn(s"Enabling debugging for all forked processes on port $port")
      log.info("Only one process can use the port and it will await a connection before proceeding.")
    }

    val newSettings = extracted.structure.allProjectRefs map { proj =>
      val orig = (javaOptions in proj).run
      val debugFlags = ((EnsimeKeys.ensimeDebuggingFlag in proj).gimme + (EnsimeKeys.ensimeDebuggingPort in proj).gimme)
      val withoutDebug = orig.diff(List(debugFlags))
      val withDebug = withoutDebug :+ debugFlags
      val rewritten = if (enable) withDebug else withoutDebug

      (javaOptions in proj) := rewritten
    }
    extracted.append(newSettings, state)
  }

  def ensimeConfig: (State, Seq[String]) => State = { (state, args) =>
    val extracted = Project.extract(state)
    implicit val st = state
    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

    // no way to detect this value later on unless we capture it
    val scalaV = (scalaVersion).gimme

    var transitiveCache = Map.empty[ProjectRef, Set[ProjectRef]]
    def transitiveProjects(ref: ProjectRef): Set[ProjectRef] = {
      if (transitiveCache.contains(ref))
        transitiveCache(ref)
      else {
        val proj = Project.getProjectForReference(ref, bs).get
        val deps = Set(ref) ++ proj.dependencies.flatMap { dep =>
          transitiveProjects(dep.project)
        }
        transitiveCache += ref -> deps
        deps
      }
    }

    val active =
      if (args.isEmpty) bs.allProjectRefs
      else args.flatMap { name =>
        val ref = bs.allProjectRefs.find(_.project == name).getOrElse {
          throw new IllegalArgumentException(s"$name is not a valid project id")
        }
        transitiveProjects(ref)
      }

    val projects = active.flatMap { ref =>
      Project.getProjectForReference(ref, bs).map((ref, _))
    }.toMap

    val updateReports = EnsimeKeys.ensimeMegaUpdate.run

    val scalaCompilerJars = updateReports.head._2._1.select(
      configuration = configurationFilter(EnsimeInternal.name),
      artifact = artifactFilter(extension = Artifact.DefaultExtension)
    ).toSet

    implicit val rawModules = projects.collect {
      case (ref, proj) =>
        val (updateReport, updateClassifiersReport) = updateReports(ref)
        val module = projectData(scalaV, proj, updateReport, updateClassifiersReport)(ref, bs, state)
        (module.name, module)
    }.toMap

    val modules: Map[String, EnsimeModule] = rawModules.mapValues { m =>
      val deps = m.dependencies
      // restrict jars to immediate deps at each module
      m.copy(
        compileJars = m.compileJars -- deps.flatMap(_.compileJars),
        testJars = m.testJars -- deps.flatMap(_.testJars),
        runtimeJars = m.runtimeJars -- deps.flatMap(_.runtimeJars),
        sourceJars = m.sourceJars -- deps.flatMap(_.sourceJars),
        docJars = m.docJars -- deps.flatMap(_.docJars)
      )
    }

    val root = file(Properties.userDir)
    val out = file(".ensime")
    val cacheDir = file(".ensime_cache")
    val name = (EnsimeKeys.ensimeName).gimmeOpt.getOrElse {
      if (modules.size == 1) modules.head._2.name
      else root.getAbsoluteFile.getName
    }
    val compilerArgs = {
      (EnsimeKeys.ensimeCompilerArgs).run.toList ++
        (EnsimeKeys.ensimeAdditionalCompilerArgs).run
    }.distinct
    val javaH = (javaHome).gimme.getOrElse(JdkDir)
    val javaSrc = {
      file(javaH.getAbsolutePath + "/src.zip") match {
        case f if f.exists => List(f)
        case _ =>
          log.warn(s"No Java sources detected in $javaH (your ENSIME experience will not be as good as it could be.)")
          Nil
      }
    } ++ EnsimeKeys.ensimeUnmanagedSourceArchives.gimme

    val javaCompilerArgs = (javacOptions in Compile).run.toList

    val javaFlags = EnsimeKeys.ensimeJavaFlags.run.toList

    val formatting = EnsimeKeys.ensimeScalariform.gimmeOpt
    val disableSourceMonitoring = (EnsimeKeys.ensimeDisableSourceMonitoring).gimme
    val disableClassMonitoring = (EnsimeKeys.ensimeDisableClassMonitoring).gimme

    val config = EnsimeConfig(
      root, cacheDir,
      scalaCompilerJars,
      name, scalaV, compilerArgs,
      modules, javaH, javaFlags, javaCompilerArgs, javaSrc, formatting,
      disableSourceMonitoring, disableClassMonitoring
    )

    val transformedConfig = EnsimeKeys.ensimeConfigTransformer.gimme.apply(config)

    // workaround for Windows
    write(out, toSExp(transformedConfig).replaceAll("\r\n", "\n") + "\n")

    if (ignoringSourceDirs.nonEmpty) {
      log.warn(
        s"""Some source directories do not exist and will be ignored by ENSIME.
           |For example: ${ignoringSourceDirs.take(5).mkString(",")} """.stripMargin
      )
    }

    state
  }

  // sbt reports a lot of source directories that the user never
  // intends to use we want to create a report
  var ignoringSourceDirs = Set.empty[File]
  def filteredSources(sources: Set[File], scalaBinaryVersion: String): Set[File] = synchronized {
    ignoringSourceDirs ++= sources.filterNot { dir =>
      // ignoring to ignore a bunch of things that most people don't care about
      val n = dir.getName
      dir.exists() ||
        n.endsWith("-" + scalaBinaryVersion) ||
        n.endsWith("java") ||
        dir.getPath.contains("src_managed")
    }
    sources.filter(_.exists())
  }

  def projectData(
    scalaV: String,
    project: ResolvedProject,
    updateReport: UpdateReport,
    updateClassifiersReport: UpdateReport
  )(
    implicit
    projectRef: ProjectRef,
    buildStruct: BuildStructure,
    state: State
  ): EnsimeModule = {
    log.info(s"ENSIME processing ${project.id} (${name.gimme})")

    val builtInTestPhases = Set(Test, IntegrationTest)
    val testPhases = {
      for {
        phase <- ivyConfigurations.gimme
        if !phase.name.toLowerCase.contains("internal")
        if builtInTestPhases(phase) | builtInTestPhases.intersect(phase.extendsConfigs.toSet).nonEmpty
      } yield phase
    }.toSet

    def sourcesFor(config: Configuration) = {
      // invoke source generation so we can filter on existing directories
      (managedSources in config).runOpt
      (managedSourceDirectories in config).gimmeOpt.map(_.toSet).getOrElse(Set()) ++
        (unmanagedSourceDirectories in config).gimmeOpt.getOrElse(Set())
    }

    def targetForOpt(config: Configuration): Option[File] =
      (EnsimeKeys.ensimeUseTarget in config).runOpt match {
        case Some(Some(jar)) => Some(jar)
        case _               => (classDirectory in config).gimmeOpt
      }

    val myDoc = (artifactPath in (Compile, packageDoc)).gimmeOpt

    def configFilter(config: Configuration): ConfigurationFilter = {
      val c = config.name.toLowerCase
      val internal = EnsimeInternal.name
      if (sbtPlugin.gimme) configurationFilter(("provided" | c) - internal)
      else configurationFilter(c - internal)
    }

    def jarsFor(config: Configuration) = updateReport.select(
      configuration = configFilter(config),
      artifact = artifactFilter(extension = Artifact.DefaultExtension)
    ).toSet

    def unmanagedJarsFor(config: Configuration) =
      (unmanagedJars in config).runOpt.map(_.map(_.data).toSet).getOrElse(Set())

    def jarSrcsFor(config: Configuration) = updateClassifiersReport.select(
      configuration = configFilter(config),
      artifact = artifactFilter(classifier = Artifact.SourceClassifier)
    ).toSet ++ (EnsimeKeys.ensimeUnmanagedSourceArchives in projectRef).gimme

    def jarDocsFor(config: Configuration) = updateClassifiersReport.select(
      configuration = configFilter(config),
      artifact = artifactFilter(classifier = Artifact.DocClassifier)
    ).toSet ++ (EnsimeKeys.ensimeUnmanagedJavadocArchives in projectRef).gimme

    val sbv = scalaBinaryVersion.gimme
    val mainSources = filteredSources(sourcesFor(Compile) ++ sourcesFor(Provided) ++ sourcesFor(Optional), sbv)
    val testSources = filteredSources(testPhases.flatMap(sourcesFor), sbv)
    val mainTarget = targetForOpt(Compile).get
    val testTargets = testPhases.flatMap(targetForOpt).toSet
    val deps = project.dependencies.map(_.project.project).toSet
    val mainJars = jarsFor(Compile) ++ unmanagedJarsFor(Compile) ++ jarsFor(Provided) ++ jarsFor(Optional)
    val runtimeJars = jarsFor(Runtime) ++ unmanagedJarsFor(Runtime) -- mainJars
    val testJars = {
      testPhases.flatMap {
        phase => jarsFor(phase) ++ unmanagedJarsFor(phase)
      }
    } -- mainJars
    val jarSrcs = testPhases.flatMap(jarSrcsFor) ++ jarSrcsFor(Provided)
    val jarDocs = testPhases.flatMap(jarDocsFor) ++ jarDocsFor(Provided) ++ myDoc

    if (scalaV != scalaVersion.gimme) {
      if (System.getProperty("ensime.sbt.debug") != null) {
        // for testing
        IO.touch(file("scalaVersionAtStartupWarning"))
      }

      log.error(
        s"""You have a different version of scala for your build (${scalaV}) and ${project.id} (${scalaVersion.gimme}).
           |It is highly likely that this is a mistake with your configuration.
           |Please read https://github.com/ensime/ensime-sbt/issues/138""".stripMargin
      )
    }

    EnsimeModule(
      project.id, mainSources, testSources, Set(mainTarget), testTargets, deps,
      mainJars, runtimeJars, testJars, jarSrcs, jarDocs
    )
  }

  def ensimeConfigProject: State => State = { implicit state: State =>
    val extracted = Project.extract(state)

    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

    val jars = for {
      unit <- bs.units
      file <- unit._2.classpath
      if !file.isDirectory() & file.getName.endsWith(Artifact.DefaultExtension)
    } yield file

    val targets = for {
      unit <- bs.units
      dir <- unit._2.classpath
      if dir.isDirectory()
    } yield dir

    val classifiers = for {
      config <- updateSbtClassifiers.run.configurations
      module <- config.modules
      artefact <- module.artifacts
    } yield artefact

    val srcs = classifiers.collect {
      case (artefact, file) if artefact.classifier == Some(Artifact.SourceClassifier) => file
    }
    // they don't seem to publish docs...
    val docs = classifiers.collect {
      case (artefact, file) if artefact.classifier == Some(Artifact.DocClassifier) => file
    }

    val root = file(Properties.userDir) / "project"
    val out = root / ".ensime"
    val cacheDir = root / ".ensime_cache"
    val name = EnsimeKeys.ensimeName.gimmeOpt.getOrElse {
      file(Properties.userDir).getName + "-project"
    }

    val compilerArgs = {
      EnsimeKeys.ensimeCompilerProjectArgs.run.toList ++
        EnsimeKeys.ensimeAdditionalProjectCompilerArgs.run
    }.distinct
    val scalaV = Properties.versionNumberString
    val javaSrc = JdkDir / "src.zip" match {
      case f if f.exists => List(f)
      case _             => Nil
    }
    val javaFlags = EnsimeKeys.ensimeJavaFlags.run.toList

    val javaCompilerArgs = (javacOptions in Compile).run.toList

    val formatting = EnsimeKeys.ensimeScalariform.gimmeOpt

    val module = EnsimeModule(
      name, Set(root), Set.empty, targets.toSet, Set.empty, Set.empty,
      jars.toSet, Set.empty, Set.empty, srcs.toSet, docs.toSet
    )

    val scalaCompilerJars = jars.filter { file =>
      val f = file.getName
      f.startsWith("scala-library") ||
        f.startsWith("scala-compiler") ||
        f.startsWith("scala-reflect") ||
        f.startsWith("scalap")
    }.toSet

    val config = EnsimeConfig(
      root, cacheDir,
      scalaCompilerJars,
      name, scalaV, compilerArgs,
      Map(module.name -> module), JdkDir, javaFlags, javaCompilerArgs, javaSrc, formatting,
      false, false
    )

    val transformedConfig = EnsimeKeys.ensimeConfigTransformerProject.gimme.apply(config)

    write(out, toSExp(transformedConfig).replaceAll("\r\n", "\n") + "\n")

    state
  }

  // WORKAROUND: https://github.com/typelevel/scala/issues/75
  lazy val JdkDir: File = List(
    // manual
    sys.env.get("JDK_HOME"),
    sys.env.get("JAVA_HOME"),
    // fallback
    sys.props.get("java.home").map(new File(_).getParent),
    sys.props.get("java.home"),
    // osx
    Try("/usr/libexec/java_home".!!.trim).toOption
  ).flatten.filter { n =>
      new File(n + "/lib/tools.jar").exists
    }.headOption.map(new File(_).getCanonicalFile).getOrElse(
      throw new FileNotFoundException(
        """Could not automatically find the JDK/lib/tools.jar.
      |You must explicitly set JDK_HOME or JAVA_HOME.""".stripMargin
      )
    )

  lazy val JavaFlags = {
    // WORKAROUND https://github.com/ensime/ensime-sbt/issues/91
    val raw = ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList.map {
      case "-Xss1M" => "-Xss2m"
      case flag     => flag
    }
    raw.find { flag => flag.startsWith("-Xss") } match {
      case Some(has) => raw
      case None      => "-Xss2m" :: raw
    }
  }

}

case class EnsimeConfig(
  root: File,
  cacheDir: File,
  scalaCompilerJars: Set[File],
  name: String,
  scalaVersion: String,
  compilerArgs: List[String],
  modules: Map[String, EnsimeModule],
  javaHome: File,
  javaFlags: List[String],
  javaCompilerArgs: List[String],
  javaSrc: List[File],
  formatting: Option[IFormattingPreferences],
  disableSourceMonitoring: Boolean,
  disableClassMonitoring: Boolean
)

case class EnsimeModule(
  name: String,
  mainRoots: Set[File],
  testRoots: Set[File],
  targets: Set[File],
  testTargets: Set[File],
  dependsOnNames: Set[String],
  compileJars: Set[File],
  runtimeJars: Set[File],
  testJars: Set[File],
  sourceJars: Set[File],
  docJars: Set[File]
) {

  def dependencies(implicit lookup: String => EnsimeModule): Set[EnsimeModule] =
    dependsOnNames map lookup

}

object CommandSupport {
  private def fail(errorMessage: String)(implicit state: State): Nothing = {
    state.log.error(errorMessage)
    throw new IllegalArgumentException()
  }

  def log(implicit state: State) = state.log

  // our version of http://stackoverflow.com/questions/25246920
  implicit class RichSettingKey[A](key: SettingKey[A]) {
    def gimme(implicit pr: ProjectRef, bs: BuildStructure, s: State): A =
      gimmeOpt getOrElse { fail(s"Missing setting: ${key.key.label}") }
    def gimmeOpt(implicit pr: ProjectRef, bs: BuildStructure, s: State): Option[A] =
      key in pr get bs.data
  }

  implicit class RichTaskKey[A](key: TaskKey[A]) {
    def run(implicit pr: ProjectRef, bs: BuildStructure, s: State): A =
      runOpt.getOrElse { fail(s"Missing task key: ${key.key.label}") }
    def runOpt(implicit pr: ProjectRef, bs: BuildStructure, s: State): Option[A] =
      EvaluateTask(bs, key, s, pr).map(_._2) match {
        case Some(Value(v)) => Some(v)
        case _              => None
      }

    def forAllProjects(state: State, projects: Seq[ProjectRef]): Task[Map[ProjectRef, A]] = {
      val tasks = projects.flatMap(p => key.in(p).get(Project.structure(state).data).map(_.map(it => (p, it))))
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }

}

// direct formatter to deal with a small number of domain objects
// if we had to do this for general objects, it would make sense
// to create a series of implicit convertors to an SExp hierarchy
object SExpFormatter {

  def toSExp(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def toSExp(f: File): String = toSExp(f.getAbsolutePath)

  def fsToSExp(ss: Iterable[File]): String =
    if (ss.isEmpty) "nil"
    else ss.toSeq.sortBy { f => f.getName + f.getPath }.map(toSExp).mkString("(", " ", ")")

  def ssToSExp(ss: Iterable[String]): String =
    if (ss.isEmpty) "nil"
    else ss.toSeq.map(toSExp).mkString("(", " ", ")")

  def msToSExp(ss: Iterable[EnsimeModule]): String =
    if (ss.isEmpty) "nil"
    else ss.toSeq.sortBy(_.name).map(toSExp).mkString("(", " ", ")")

  def fToSExp(key: String, op: Option[File]): String =
    op.map { f => s":$key ${toSExp(f)}" }.getOrElse("")

  def sToSExp(key: String, op: Option[String]): String =
    op.map { f => s":$key ${toSExp(f)}" }.getOrElse("")

  def toSExp(b: Boolean): String = if (b) "t" else "nil"

  def toSExp(o: Option[IFormattingPreferences]): String = o match {
    case None                                => "nil"
    case Some(f) if f.preferencesMap.isEmpty => "nil"
    case Some(f) => f.preferencesMap.map {
      case (desc, value) =>
        val vs = value match {
          case b: Boolean => toSExp(b)
          case i: Int     => i.toString
          case intent =>
            // quick fix to serialize intents, until the scalariform dependency is
            // upgraded (pending #148)
            toSExp(intent.getClass.getSimpleName.replaceAll("\\$", "").toLowerCase)
        }
        s":${desc.key} $vs"
    }.mkString("(", " ", ")")
  }

  // a lot of legacy key names and conventions
  def toSExp(c: EnsimeConfig): String = s"""(
 :root-dir ${toSExp(c.root)}
 :cache-dir ${toSExp(c.cacheDir)}
 :scala-compiler-jars ${fsToSExp(c.scalaCompilerJars)}
 :name "${c.name}"
 :java-home ${toSExp(c.javaHome)}
 :java-flags ${ssToSExp(c.javaFlags)}
 :java-compiler-args ${ssToSExp(c.javaCompilerArgs)}
 :reference-source-roots ${fsToSExp(c.javaSrc)}
 :scala-version ${toSExp(c.scalaVersion)}
 :compiler-args ${ssToSExp(c.compilerArgs)}
 :formatting-prefs ${toSExp(c.formatting)}
 :disable-source-monitoring ${toSExp(c.disableSourceMonitoring)}
 :disable-class-monitoring ${toSExp(c.disableClassMonitoring)}
 :subprojects ${msToSExp(c.modules.values)}
)"""

  // a lot of legacy key names and conventions
  def toSExp(m: EnsimeModule): String = s"""(
   :name ${toSExp(m.name)}
   :source-roots ${fsToSExp((m.mainRoots ++ m.testRoots))}
   :targets ${fsToSExp(m.targets)}
   :test-targets ${fsToSExp(m.testTargets)}
   :depends-on-modules ${ssToSExp(m.dependsOnNames.toList.sorted)}
   :compile-deps ${fsToSExp(m.compileJars)}
   :runtime-deps ${fsToSExp(m.runtimeJars)}
   :test-deps ${fsToSExp(m.testJars)}
   :doc-jars ${fsToSExp(m.docJars)}
   :reference-source-roots ${fsToSExp(m.sourceJars)})"""
}
