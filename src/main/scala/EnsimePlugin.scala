// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import java.io.FileNotFoundException
import java.lang.management.ManagementFactory

import scala.collection.JavaConverters._
import scala.util._
import scala.util.control.NoStackTrace

import SExpFormatter._
import sbt._
import sbt.IO._
import sbt.Keys._
import sbt.complete.Parsers._

/**
 * Conventional way to define importable keys for an AutoPlugin.
 */
object EnsimeKeys {
  val ensimeServerIndex = taskKey[Unit](
    "Start up the ENSIME server and index your project."
  )

  // for ensimeConfig
  val ensimeName = settingKey[String](
    "Name of the ENSIME project"
  )
  val ensimeScalacOptions = taskKey[Seq[String]](
    "Arguments for the scala presentation compiler, extracted from the compiler flags."
  )
  val ensimeJavacOptions = taskKey[Seq[String]](
    "Arguments for the java presentation compiler, extracted from the compiler flags."
  )

  val ensimeIgnoreSourcesInBase = settingKey[Boolean](
    "ENSIME doesn't support sources in base, this tolerates their existence."
  )
  val ensimeIgnoreMissingDirectories = settingKey[Boolean](
    "ENSIME requires declared source directories to exist. If you find this noisy, ignore them."
  )
  val ensimeIgnoreScalaMismatch = settingKey[Boolean](
    "ENSIME can only use one version of scala for the entire build. If you have mixed scala versions (e.g. lib and sbt plugin), set this to acknowledge."
  )

  val ensimeJavaFlags = taskKey[Seq[String]](
    "Flags to be passed to ENSIME JVM process."
  )
  val ensimeJavaHome = settingKey[File](
    "The java home directory to be used by the ENSIME JVM process."
  )
  val ensimeScalaVersion = taskKey[String](
    "Version of scala for the ENSIME JVM process."
  )

  val ensimeScalaJars = taskKey[Seq[File]](
    "The scala compiler's jars."
  )
  val ensimeScalaProjectJars = taskKey[Seq[File]](
    "The build's scala compiler's jars."
  )

  val ensimeServerJars = taskKey[Seq[File]](
    "The ensime-server's jars."
  )
  val ensimeServerProjectJars = taskKey[Seq[File]](
    "The build's ensime-server's jars."
  )

  val ensimeUseTarget = taskKey[Option[File]](
    "Use a calculated jar instead of the class directory. " +
      "Note that `proj/compile` does not produce the jar, change your workflow to use `proj/packageBin`."
  )

  // used to start the REPL and assembly jar bundles of ensime-server.
  // intransitive because we don't need parser combinators, scala.xml or jline
  val ensimeScalaCompilerJarModuleIDs = settingKey[Seq[ModuleID]](
    "The artefacts to resolve for :scala-compiler-jars in ensimeConfig."
  )

  // for ensimeConfigProject
  val ensimeProjectScalacOptions = taskKey[Seq[String]](
    "Arguments for the project definition presentation compiler (not possible to extract)."
  )

  val ensimeUnmanagedSourceArchives = settingKey[Seq[File]](
    "Source jars (and zips) to complement unmanagedClasspath. May be set for the project and its submodules."
  )
  val ensimeUnmanagedJavadocArchives = settingKey[Seq[File]](
    "Documentation jars (and zips) to complement unmanagedClasspath. May only be set for submodules."
  )

  val ensimeMegaUpdate = taskKey[Map[ProjectRef, (UpdateReport, UpdateReport)]](
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

  val ensimeCachePrefix = settingKey[Option[File]](
    "Instead of putting the cache directory in the base directory, put it under this directory. " +
      "Useful if you want to put on a faster (local or RAM). " +
      "Will break clients that expect the cache to be in the project root."
  )
}

object EnsimePlugin extends AutoPlugin {
  import CommandSupport._

  // ensures compiler settings are loaded before us
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
  val autoImport = EnsimeKeys
  import autoImport._

  override lazy val buildSettings = Seq(
    // WORKAROUND: https://github.com/sbt/sbt/issues/2814
    scalaOrganization in updateSbtClassifiers := (scalaOrganization in Global).value,

    commands += Command.args("ensimeConfig", ("", ""), "Generate a .ensime for the project.", "proj1 proj2")(ensimeConfig),
    commands += Command.command("ensimeConfigProject", "", "Generate a project/.ensime for the project definition.")(ensimeConfigProject),

    ensimeScalaVersion := state.map { implicit s =>
      // infer the scalaVersion by majority vote, because many badly
      // written builds will forget to set the scalaVersion for the
      // root project. And we can't ask for (scalaVersion in
      // ThisBuild) because very few projects do it The Right Way.
      implicit val struct = Project.structure(s)

      val scalaVersions = struct.allProjectRefs.map { implicit p =>
        scalaVersion.gimme
      }.groupBy(identity).map { case (sv, svs) => sv -> svs.size }.toList

      scalaVersions.sortWith { case ((_, c1), (_, c2)) => c1 < c2 }.head._1
    }.value,

    ensimeIgnoreSourcesInBase := false,
    ensimeIgnoreMissingDirectories := false,
    ensimeIgnoreScalaMismatch := false,

    ensimeCachePrefix := None,

    // WORKAROUND: https://github.com/scala/scala/pull/5592
    ensimeJavaFlags := JavaFlags :+ "-Dscala.classpath.closeZip=true",
    ensimeJavaHome := javaHome.value.getOrElse(JdkDir),
    // unable to infer the user's scalac options: https://github.com/ensime/ensime-sbt/issues/98
    ensimeProjectScalacOptions := ensimeSuggestedScalacOptions(Properties.versionNumberString),
    ensimeMegaUpdate := Keys.state.flatMap { implicit s =>

      def checkCoursier(): Unit = {
        val structure = Project.extract(s).structure
        val plugins = structure.allProjects.flatMap(_.autoPlugins).map(_.getClass.getName)
        val usesCoursier = plugins.exists(_.contains("CoursierPlugin"))
        if (!usesCoursier) {
          log.warn(
            "SBT is using ivy to resolve dependencies which is known to be slow. " +
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
    }.value
  )

  override lazy val projectSettings = Seq(
    aggregate in ensimeServerIndex := false,
    ensimeServerIndex := ensimeServerIndexTask.value,

    ensimeUnmanagedSourceArchives := Nil,
    ensimeUnmanagedJavadocArchives := Nil,
    ensimeConfigTransformer := identity,
    ensimeConfigTransformerProject := identity,
    ensimeUseTarget := None,

    ensimeScalacOptions := (
      (scalacOptions in Compile).value ++
      ensimeSuggestedScalacOptions((ensimeScalaVersion in ThisBuild).value)
    ).distinct,
    ensimeJavacOptions := (javacOptions in Compile).value
  )

  // exposed for users to use
  def ensimeSuggestedScalacOptions(scalaVersion: String): Seq[String] = Seq(
    "-feature",
    "-deprecation",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xfuture"
  ) ++ {
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, 10))           => Seq("-Ymacro-no-expand")
        case Some((2, v)) if v >= 11 => Seq("-Ywarn-unused-import", "-Ymacro-expand:discard")
        case _                       => Nil
      }
    }

  def ensimeServerIndexTask: Def.Initialize[Task[Unit]] = Def.task {
    val javaH = ensimeJavaHome.value
    val java = javaH / "bin/java"
    val scalaCompilerJars = ensimeScalaJars.value.toSet
    val jars = ensimeServerJars.value.toSet ++ scalaCompilerJars + javaH / "lib/tools.jar"
    val jvmFlags = ensimeJavaFlags.value ++ Seq("-Densime.config=.ensime", "-Densime.exitAfterIndex=true")
    val cache = cacheDir(ensimeCachePrefix.value, file("."))

    cache.mkdirs()

    val options = ForkOptions(Some(javaH), runJVMOptions = jvmFlags)
    toError(
      new ForkRun(options).run(
        "org.ensime.server.Server",
        orderFiles(jars),
        Nil,
        streams.value.log
      )
    )
  }

  def ensimeConfig: (State, Seq[String]) => State = { (state, args) =>
    val extracted = Project.extract(state)
    implicit val st = state
    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

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

    val updateReports = ensimeMegaUpdate.run

    val javaH = (ensimeJavaHome).gimme
    val scalaCompilerJars = ensimeScalaJars.run.toSet
    val serverJars = ensimeServerJars.run.toSet -- scalaCompilerJars + javaH / "lib/tools.jar"

    // for some reason this gives the wrong number in projectData
    val ensimeScalaV = (ensimeScalaVersion in ThisBuild).run

    implicit val rawProjects = projects.flatMap {
      case (ref, proj) =>
        val (updateReport, updateClassifiersReport) = updateReports(ref)
        val projects = projectData(ensimeScalaV, proj, updateReport, updateClassifiersReport)(ref, bs, state)
        projects.map { p => (p.id, p) }
    }.toMap

    val subProjects: Seq[EnsimeProject] = rawProjects.toSeq.map {
      case (_, p) =>
        val deps = p.depends
        // restrict jars to immediate deps at each module
        p.copy(
          libraryJars = p.libraryJars -- deps.flatMap(_.libraryJars),
          librarySources = p.librarySources -- deps.flatMap(_.librarySources),
          libraryDocs = p.libraryDocs -- deps.flatMap(_.libraryDocs)
        )
    }

    val root = file(Properties.userDir)
    val out = file(".ensime")
    val name = (ensimeName).gimmeOpt.getOrElse {
      if (subProjects.size == 1) subProjects.head.id.project
      else root.getAbsoluteFile.getName
    }
    val compilerArgs = (ensimeScalacOptions).run.toList
    val javaCompilerArgs = (ensimeJavacOptions).run.toList
    val javaSrc = {
      file(javaH.getAbsolutePath + "/src.zip") match {
        case f if f.exists => Set(f)
        case _ =>
          log.warn(s"No Java sources detected in $javaH (your ENSIME experience will not be as good as it could be.)")
          Set.empty
      }
    } ++ (ensimeUnmanagedSourceArchives in ThisBuild).gimme

    val javaFlags = ensimeJavaFlags.run.toList

    val scalaVersion = (ensimeScalaVersion in ThisBuild).run

    val modules = subProjects.groupBy(_.id.project).mapValues(ensimeProjectsToModule)

    val config = EnsimeConfig(
      root, cacheDir(ensimeCachePrefix.gimme, root),
      scalaCompilerJars, serverJars,
      name, scalaVersion, compilerArgs,
      modules, javaH, javaFlags, javaCompilerArgs, javaSrc, subProjects
    )

    val transformedConfig = ensimeConfigTransformer.gimme.apply(config)

    // workaround for Windows
    write(out, toSExp(transformedConfig).replaceAll("\r\n", "\n") + "\n")

    state
  }

  private def ensureCreatedOrIgnore(ignore: Boolean, log: Logger)(sources: Set[File]): Set[File] = {
    sources.collect {
      case dir if dir.isDirectory() => dir
      case dir if !ignore =>
        log.info(s"""Creating $dir. Read about `ensimeIgnoreMissingDirectories`""")
        dir.mkdirs()
        dir
    }
  }

  def projectData(
    ensimeScalaV: String,
    project: ResolvedProject,
    updateReport: UpdateReport,
    updateClassifiersReport: UpdateReport
  )(
    implicit
    projectRef: ProjectRef,
    buildStruct: BuildStructure,
    state: State
  ): Seq[EnsimeProject] = {
    log.info(s"ENSIME processing ${project.id} (${name.gimme})")

    val builtInTestPhases = Set(Test, IntegrationTest)
    val testPhases = {
      for {
        phase <- ivyConfigurations.gimme
        if phase.isPublic
        if builtInTestPhases(phase) || builtInTestPhases.intersect(phase.extendsConfigs.toSet).nonEmpty
      } yield phase
    }.toSet

    def sourcesFor(config: Configuration) = {
      // invoke source generation so we can filter on existing directories
      (managedSources in config).runOpt
      (managedSourceDirectories in config).gimmeOpt.map(_.toSet).getOrElse(Set()) ++
        (unmanagedSourceDirectories in config).gimmeOpt.getOrElse(Set())
    }

    def targetForOpt(config: Configuration): Option[File] =
      (ensimeUseTarget in config).runOpt match {
        case Some(Some(jar)) => Some(jar)
        case _               => (classDirectory in config).gimmeOpt
      }

    def configFilter(config: Configuration): ConfigurationFilter = {
      val c = config.name.toLowerCase
      if (sbtPlugin.gimme) configurationFilter("provided" | c)
      else configurationFilter(c)
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
    ).toSet ++ (ensimeUnmanagedSourceArchives in config in projectRef).gimme

    def jarDocsFor(config: Configuration) = updateClassifiersReport.select(
      configuration = configFilter(config),
      artifact = artifactFilter(classifier = Artifact.DocClassifier)
    ).toSet ++ (ensimeUnmanagedJavadocArchives in config in projectRef).gimme

    // needs to take configurations into account
    // https://github.com/ensime/ensime-sbt/issues/247
    val deps = project.dependencies.map(_.project.project).map { n => EnsimeProjectId(n, "compile") }

    def configDataFor(config: Configuration): EnsimeProject = {
      val sbv = scalaBinaryVersion.gimme
      val sources = ensureCreatedOrIgnore(ensimeIgnoreMissingDirectories.gimme, log) {
        config match {
          case Compile => sourcesFor(Compile) ++ sourcesFor(Provided) ++ sourcesFor(Optional)
          case _       => sourcesFor(config)
        }
      }

      val target = targetForOpt(config).get
      val scalaCompilerArgs = ((scalacOptions in config).run ++
        ensimeSuggestedScalacOptions(ensimeScalaV)).toList
      val javaCompilerArgs = (javacOptions in config).run.toList
      val jars = config match {
        case Compile => jarsFor(Compile) ++ unmanagedJarsFor(Compile) ++ jarsFor(Provided) ++ jarsFor(Optional)
        case _       => jarsFor(config) ++ unmanagedJarsFor(config)
      }

      val jarSrcs = config match {
        case Compile => Seq(Provided, Compile).flatMap(jarSrcsFor)
        case _       => jarSrcsFor(config)
      }

      val jarDocs = config match {
        case Compile =>
          Seq(Provided, Compile).flatMap(jarDocsFor) ++
            (artifactPath in (Compile, packageDoc)).gimmeOpt.toList
        case _ => jarDocsFor(config)
      }

      val id = EnsimeProjectId(project.id, config.name)
      EnsimeProject(id, deps, sources, Set(target), scalaCompilerArgs, javaCompilerArgs, jars, jarSrcs.toSet, jarDocs.toSet)
    }

    if (scalaVersion.gimme != ensimeScalaV) {
      log.warn(
        s"""You have a different version of scala for ENSIME ($ensimeScalaV) and ${project.id} (${scalaVersion.gimme}).
           |If this is not what you intended, try fixing your build with:
           |  scalaVersion in ThisBuild := "${scalaVersion.gimme}"
           |in your build.sbt, or override the ENSIME scala version with:
           |  ensimeScalaVersion in ThisBuild := "${scalaVersion.gimme}"
           |in a ensime.sbt: http://ensime.org/build_tools/sbt/#customise""".stripMargin
      )
      if (!ensimeIgnoreScalaMismatch.gimme)
        throw new IllegalStateException(
          s"""To ignore this error (i.e. you have multiple scala versions), add `ensimeIgnoreScalaMismatch in ThisBuild := true` or `ensimeIgnoreScalaMismatch in LocalProject("${project.id}") := true` to your ensime.sbt"""
        ) with NoStackTrace
    }

    if (sourcesInBase.gimme) {
      val sources = baseDirectory.gimme.list().filter(_.endsWith(".scala"))
      if (sources.nonEmpty) {
        log.warn(
          s"""You have .scala files in the base of your project. Such "script style" projects
             |are not supported by ENSIME. Move them into src/main/scala to get support.
             |Please read https://github.com/ensime/ensime-server/issues/1432""".stripMargin
        )
        if (!ensimeIgnoreSourcesInBase.gimme) {
          throw new IllegalStateException("To ignore this error, customise `ensimeIgnoreSourcesInBase`") with NoStackTrace
        }
      }
    }

    (testPhases + Compile).map(configDataFor).toSeq
  }

  private def ensimeProjectsToModule(p: Iterable[EnsimeProject]): EnsimeModule = {
    val name = p.head.id.project
    val deps = for {
      s <- p
      d <- s.depends
    } yield d.project
    val (mains, tests) = p.toSet.partition(_.id.config == "compile")
    val mainSources = mains.flatMap(_.sources)
    val mainTargets = mains.flatMap(_.targets)
    val mainJars = mains.flatMap(_.libraryJars)
    val testSources = tests.flatMap(_.sources)
    val testTargets = tests.flatMap(_.targets)
    val testJars = tests.flatMap(_.libraryJars).toSet -- mainJars
    val sourceJars = p.flatMap(_.librarySources).toSet
    val docJars = p.flatMap(_.libraryDocs).toSet
    EnsimeModule(
      name, mainSources, testSources, mainTargets, testTargets, deps.toSet,
      mainJars, Set.empty, testJars, sourceJars, docJars
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
    val name = ensimeName.gimmeOpt.getOrElse {
      file(Properties.userDir).getName
    } + "-project"

    val compilerArgs = ensimeProjectScalacOptions.run.toList
    val scalaV = Properties.versionNumberString
    val javaH = ensimeJavaHome.gimme
    val javaSrc = javaH / "src.zip" match {
      case f if f.exists => Set(f)
      case _             => Set.empty[File]
    }
    val javaFlags = ensimeJavaFlags.run.toList

    val id = EnsimeProjectId(name, "compile")
    val proj = EnsimeProject(id, Nil, Set(root), targets.toSet, Nil, Nil, jars.toSet, srcs.toSet, docs.toSet)
    val module = ensimeProjectsToModule(Set(proj))

    val scalaCompilerJars = ensimeScalaProjectJars.run.toSet
    val serverJars = ensimeServerProjectJars.run.toSet -- scalaCompilerJars + javaH / "lib/tools.jar"

    val config = EnsimeConfig(
      root, cacheDir(ensimeCachePrefix.gimme, root),
      scalaCompilerJars, serverJars,
      name, scalaV, compilerArgs,
      Map(module.name -> module), javaH, javaFlags, Nil, javaSrc,
      Seq(proj)
    )

    val transformedConfig = ensimeConfigTransformerProject.gimme.apply(config)

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

  private def cacheDir(prefix: Option[File], base: File): File = prefix match {
    case None      => base / ".ensime_cache"
    case Some(pre) => pre / s"${base.getCanonicalPath}/.ensime_cache"
  }

  // normalise and ensure monkeys go first
  // (bit of a hack to do it here, maybe best when creating)
  private[ensime] def orderFiles(ss: Iterable[File]): List[File] = {
    val (monkeys, humans) = ss.toList.distinct.sortBy { f =>
      f.getName + f.getPath
    }.partition(_.getName.contains("monkey"))
    monkeys ::: humans
  }

}

case class EnsimeConfig(
  root: File,
  cacheDir: File,
  scalaCompilerJars: Set[File],
  ensimeServerJars: Set[File],
  name: String,
  scalaVersion: String,
  scalacOptions: List[String], // 1.0
  modules: Map[String, EnsimeModule], // 1.0
  javaHome: File,
  javaFlags: List[String],
  javacOptions: List[String], // 1.0
  javaSrc: Set[File],
  projects: Seq[EnsimeProject]
)

// 1.0
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

case class EnsimeProjectId(
  project: String,
  config: String
)

case class EnsimeProject(
  id: EnsimeProjectId,
  depends: Seq[EnsimeProjectId],
  sources: Set[File],
  targets: Set[File],
  scalacOptions: List[String],
  javacOptions: List[String],
  libraryJars: Set[File],
  librarySources: Set[File],
  libraryDocs: Set[File]
)

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
    else EnsimePlugin.orderFiles(ss).map(toSExp).mkString("(", " ", ")")

  def ssToSExp(ss: Iterable[String]): String =
    if (ss.isEmpty) "nil"
    else ss.toSeq.map(toSExp).mkString("(", " ", ")")

  def msToSExp(ss: Iterable[EnsimeModule]): String =
    if (ss.isEmpty) "nil"
    else ss.toSeq.sortBy(_.name).map(toSExp).mkString("(", " ", ")")

  def psToSExp(ss: Iterable[EnsimeProject]): String =
    if (ss.isEmpty) "nil"
    else ss.toSeq.sortBy(_.id.toString).map(toSExp).mkString("(", " ", ")")

  def fToSExp(key: String, op: Option[File]): String =
    op.map { f => s":$key ${toSExp(f)}" }.getOrElse("")

  def sToSExp(key: String, op: Option[String]): String =
    op.map { f => s":$key ${toSExp(f)}" }.getOrElse("")

  def toSExp(b: Boolean): String = if (b) "t" else "nil"

  // a lot of legacy key names and conventions
  def toSExp(c: EnsimeConfig): String = s"""(
 :root-dir ${toSExp(c.root)}
 :cache-dir ${toSExp(c.cacheDir)}
 :scala-compiler-jars ${fsToSExp(c.scalaCompilerJars)}
 :ensime-server-jars ${fsToSExp(c.ensimeServerJars)}
 :name "${c.name}"
 :java-home ${toSExp(c.javaHome)}
 :java-flags ${ssToSExp(c.javaFlags)}
 :java-sources ${fsToSExp(c.javaSrc)}
 :java-compiler-args ${ssToSExp(c.javacOptions)}
 :reference-source-roots ${fsToSExp(c.javaSrc)}
 :scala-version ${toSExp(c.scalaVersion)}
 :compiler-args ${ssToSExp(c.scalacOptions)}
 :subprojects ${msToSExp(c.modules.values)}
 :projects ${psToSExp(c.projects)}
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

  def toSExp(p: EnsimeProject): String = s"""(
    :id ${toSExp(p.id)}
    :depends ${idsToSExp(p.depends)}
    :sources ${fsToSExp(p.sources)}
    :targets ${fsToSExp(p.targets)}
    :scalac-options ${ssToSExp(p.scalacOptions)}
    :javac-options ${ssToSExp(p.javacOptions)}
    :library-jars ${fsToSExp(p.libraryJars)}
    :library-sources ${fsToSExp(p.librarySources)}
    :library-docs ${fsToSExp(p.libraryDocs)})"""

  def toSExp(id: EnsimeProjectId): String =
    s"""(:project ${toSExp(id.project)} :config ${toSExp(id.config)})"""

  def idsToSExp(ids: Iterable[EnsimeProjectId]): String =
    if (ids.isEmpty) "nil"
    else ids.toSeq.sortBy(_.toString).map(toSExp).mkString("(", " ", ")")

}
