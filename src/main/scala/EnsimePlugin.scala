package org.ensime

import sbt._
import Keys._
import IO._
import complete.Parsers._
import collection.immutable.SortedMap
import collection.JavaConverters._
import java.lang.management.ManagementFactory
import scala.util.Properties
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

    val projects = bs.allProjectRefs(bs.root).flatMap { ref =>
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
    val javaH = (javaHome in Compile).gimme.
      orElse(sys.env.get("JAVA_HOME").map(file))
    val javaSrc = javaH.flatMap { h =>
      file(h + "/src.zip") match {
        case f if f.exists => Some(f)
        case _ => None
      }
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
      (managedSources in config).run
      (managedSourceDirectories in config).gimme.filter(_.exists).toSet ++
        (unmanagedSourceDirectories in config).gimme
    }
    def targetFor(config: Configuration) =
      (classDirectory in config).gimme

    // run these once for performance
    val updateReport = (update in Test).run
    val updateClassifiersReport = (updateClassifiers in Test).run
    val filter = if (sbtPlugin.gimme) "provided" else ""

    def jarsFor(config: Configuration) = updateReport.select(
      configuration = configurationFilter(filter | config.name.toLowerCase),
      artifact = artifactFilter(extension = "jar")
    ).toSet
    def jarSrcsFor(config: Configuration) = updateClassifiersReport.select(
      configuration = configurationFilter(filter | config.name.toLowerCase),
      artifact = artifactFilter(classifier = "sources")
    ).toSet
    def jarDocsFor(config: Configuration) = updateClassifiersReport.select(
      configuration = configurationFilter(filter | config.name.toLowerCase),
      artifact = artifactFilter(classifier = "javadoc")
    ).toSet

    val mainSources = sourcesFor(Compile)
    val testSources = sourcesFor(Test)
    val mainTarget = targetFor(Compile)
    val testTarget = targetFor(Test)
    val deps = project.dependencies.map(_.project.project).toSet
    val mainJars = jarsFor(Compile)
    val runtimeJars = jarsFor(Runtime) -- mainJars
    val testJars = jarsFor(Test) -- mainJars
    val jarSrcs = jarSrcsFor(Test)
    val jarDocs = jarDocsFor(Test)

    EnsimeModule(
      project.id, mainSources, testSources, mainTarget, testTarget, deps,
      mainJars, runtimeJars, testJars, jarSrcs, jarDocs)
  }
}
