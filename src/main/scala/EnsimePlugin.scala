package org.ensime

import sbt._
import Keys._
import IO._
import complete.Parsers._
import collection.immutable.SortedMap
import collection.JavaConverters._
import java.lang.management.ManagementFactory
import scala.util._
import java.io.FileNotFoundException
import SExpFormatter._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences.IFormattingPreferences

/** Conventional way to define importable keys for an AutoPlugin.
  * Note that EnsimePlugin.autoImport == Imports
  */
object Imports {
  object EnsimeKeys {
    val name = SettingKey[String]("name of the ENSIME project")
    val compilerArgs = TaskKey[Seq[String]]("arguments for the presentation compiler")
    val additionalSExp = TaskKey[String]("raw SExp to include in the output")
  }
}

object EnsimePlugin extends AutoPlugin with CommandSupport {

  val autoImport = Imports

  // Ensures the underlying base SBT plugin settings are loaded prior to Ensime.
  // This is important otherwise the `compilerArgs` would not be able to
  // depend on `scalacOptions in Compile` (becuase they wouldn't be set yet)
  override def requires = plugins.JvmPlugin

  // Automatically enable the plugin so the user doesn't have to `enablePlugins`
  // in their projects' build.sbt
  override def trigger = allRequirements

  import autoImport._

  lazy val ensimeCommand = Command.command("gen-ensime")(genEnsime)

  override lazy val projectSettings = Seq(
    commands += ensimeCommand,
    EnsimeKeys.compilerArgs := (scalacOptions in Compile).value,
    EnsimeKeys.additionalSExp := ""
  )

  def genEnsime(state: State): State = {
    implicit val s = state
    val provider = state.configuration.provider

    // val sbtScalaVersion = provider.scalaProvider.version
    // val sbtInstance = ScalaInstance(sbtScalaVersion, provider.scalaProvider.launcher)
    // val sbtProject = BuildPaths.projectStandard(state.baseDir)
    // val sbtOut = BuildPaths.crossPath(BuildPaths.outputDirectory(sbtProject), sbtInstance)

    val extracted = Project.extract(state)
    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

    val projects = bs.allProjectRefs.flatMap { ref =>
      Project.getProjectForReference(ref, bs).map((ref, _))
    }.toMap

    implicit val rawModules = projects.collect {
      case (ref, proj) =>
        val module = projectData(proj)(ref, bs, state)
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
    val name = EnsimeKeys.name.gimmeOpt.getOrElse {
      if (modules.size == 1) modules.head._2.name
      else root.getAbsoluteFile.getName
    }
    val compilerArgs = (EnsimeKeys.compilerArgs in Compile).run.toList
    val scalaV = (scalaVersion in Compile).gimme
    val javaH = (javaHome in Compile).gimme.getOrElse(JdkDir)
    val javaSrc = file(javaH.getAbsolutePath + "/src.zip") match {
      case f if f.exists => Some(f)
      case _ =>
        log.warn(s"No Java sources detected in $javaH (your ENSIME experience will not be as good as it could be.)")
        None
    }
    val javaFlags = ManagementFactory.getRuntimeMXBean.
      getInputArguments.asScala.toList
    val raw = (EnsimeKeys.additionalSExp in Compile).run

    val formatting = (ScalariformKeys.preferences in Compile).gimmeOpt

    val config = EnsimeConfig(
      root, cacheDir, name, scalaV, compilerArgs,
      modules, javaH, javaFlags, javaSrc, formatting, raw
    )

    // workaround for Windows
    write(out, toSExp(config).replaceAll("\r\n", "\n") + "\n")

    state
  }

  def projectData(project: ResolvedProject)(
    implicit projectRef: ProjectRef,
    buildStruct: BuildStructure,
    state: State): EnsimeModule = {
    log.info(s"ENSIME processing ${project.id} (${name.gimme})")

    def sourcesFor(config: Configuration) = {
      // invoke source generation so we can filter on existing directories
      (managedSources in config).runOpt
      (managedSourceDirectories in config).gimmeOpt.map(_.filter(_.exists).toSet).getOrElse(Set()) ++
        (unmanagedSourceDirectories in config).gimmeOpt.getOrElse(Set())
    }

    def targetFor(config: Configuration) =
      (classDirectory in config).gimme

    def targetForOpt(config: Configuration) =
      (classDirectory in config).gimmeOpt

    // run these once for performance
    val updateReports = List(
      (update in Test).runOpt,
      (update in IntegrationTest).runOpt).flatten

    val updateClassifiersReports = List(
      (updateClassifiers in Test).runOpt,
      (updateClassifiers in IntegrationTest).runOpt).flatten

    val myDoc = (artifactPath in (Compile, packageDoc)).gimmeOpt

    val filter = if (sbtPlugin.gimme) "provided" else ""

    def jarsFor(config: Configuration) = updateReports.flatMap(_.select(
      configuration = configurationFilter(filter | config.name.toLowerCase),
      artifact = artifactFilter(extension = "jar")
    )).toSet

    def unmanagedJarsFor(config: Configuration) =
      (unmanagedJars in config).runOpt.map(_.map(_.data).toSet).getOrElse(Set())

    def jarSrcsFor(config: Configuration) = updateClassifiersReports.flatMap(_.select(
      configuration = configurationFilter(filter | config.name.toLowerCase),
      artifact = artifactFilter(classifier = "sources")
    )).toSet

    def jarDocsFor(config: Configuration) = updateClassifiersReports.flatMap(_.select(
      configuration = configurationFilter(filter | config.name.toLowerCase),
      artifact = artifactFilter(classifier = "javadoc")
    )).toSet

    val mainSources = sourcesFor(Compile) ++ sourcesFor(Provided) ++ sourcesFor(Optional)
    val testSources = sourcesFor(Test) ++ sourcesFor(IntegrationTest)
    val mainTarget = targetFor(Compile)
    val testTargets = (targetForOpt(Test) ++ targetForOpt(IntegrationTest)).toSet
    val deps = project.dependencies.map(_.project.project).toSet
    val mainJars = jarsFor(Compile) ++ unmanagedJarsFor(Compile) ++ jarsFor(Provided) ++
      jarsFor(Optional)
    val runtimeJars = jarsFor(Runtime) ++ unmanagedJarsFor(Runtime) -- mainJars
    val testJars = jarsFor(Test) ++ jarsFor(IntegrationTest) ++
      unmanagedJarsFor(Test) ++ unmanagedJarsFor(IntegrationTest) -- mainJars
    val jarSrcs = jarSrcsFor(Test) ++ jarSrcsFor(IntegrationTest)
    val jarDocs = jarDocsFor(Test) ++ jarDocsFor(IntegrationTest) ++ myDoc

    EnsimeModule(
      project.id, mainSources, testSources, mainTarget, testTargets, deps,
      mainJars, runtimeJars, testJars, jarSrcs, jarDocs)
  }

  // WORKAROUND: https://github.com/typelevel/scala/issues/75
  lazy val JdkDir: File = List(
    // manual
    sys.env.get("JDK_HOME"),
    sys.env.get("JAVA_HOME"),
    // osx
    Try("/usr/libexec/java_home".!!.trim).toOption,
    // fallback
    sys.props.get("java.home").map(new File(_).getParent),
    sys.props.get("java.home")
  ).flatten.filter { n =>
    new File(n + "/lib/tools.jar").exists
  }.headOption.map(new File(_)).getOrElse(
    throw new FileNotFoundException(
      """Could not automatically find the JDK/lib/tools.jar.
      |You must explicitly set JDK_HOME or JAVA_HOME.""".stripMargin
    )
  )
}
