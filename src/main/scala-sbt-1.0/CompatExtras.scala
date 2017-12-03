package org.ensime

import java.util

import org.ensime.EnsimeExtrasPlugin.{fileInProject, noChanges}
import sbt.Keys._
import sbt._
import sbt.internal.inc.{CompileOutput, CompilerArguments, ReporterManager}
import xsbti._
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.CompilerCache

trait CompatExtrasKeys {
  val ensimeDebuggingArgs = settingKey[Seq[String]](
    "Java args for for debugging"
  )
}

trait CompatExtras {
  import EnsimeExtrasKeys._

  val compatSettings: Seq[Setting[_]] = Nil

  private object noopCallback extends xsbti.AnalysisCallback {
    override def startSource(source: File): Unit = {}
    override def mainClass(sourceFile: File, className: String): Unit = {}
    override def apiPhaseCompleted(): Unit = {}
    override def enabled(): Boolean = true
    override def binaryDependency(onBinaryEntry: File,
                                  onBinaryClassName: String,
                                  fromClassName: String,
                                  fromSourceFile: File,
                                  context: DependencyContext): Unit = {}
    override def generatedNonLocalClass(source: File, classFile: File,
                                        binaryClassName: String,
                                        srcClassName: String): Unit = {}
    override def problem(what: String, pos: Position, msg: String,
                         severity: Severity, reported: Boolean): Unit = {}
    override def dependencyPhaseCompleted(): Unit = {}
    override def classDependency(onClassName: String,
                                 sourceClassName: String,
                                 context: DependencyContext): Unit = {}
    override def generatedLocalClass(source: File, classFile: File): Unit = {}
    override def api(sourceFile: File, classApi: ClassLike): Unit = {}
    override def usedName(className: String, name: String,
                          useScopes: util.EnumSet[UseScope]): Unit = {}
  }

  def compileOnlyTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val args = Def.spaceDelimited().parsed
    val out = classDirectory.value
    val s = streams.value

    val (extraOpts, files) = args.partition(_.startsWith("-"))
    val opts = (scalacOptions in ensimeCompileOnly).value ++ extraOpts

    val input = files.map { arg =>
      fileInProject(arg, sourceDirectories.value.map(_.getCanonicalFile))
    }

    if (!out.exists()) IO.createDirectory(out)
    s.log.info(s"""Compiling $input with ${opts.mkString(" ")}""")

    val compArgs = new CompilerArguments(
      scalaInstance.value, classpathOptions.value
    )
    val arguments = compArgs(
      Nil, dependencyClasspath.value.map(_.data), None, opts
    )
    val basicReporterConfig = ReporterUtil.getDefaultReporterConfig
    val reporterConfig = basicReporterConfig.withMaximumErrors(maxErrors.value)
    val reporter = ReporterManager.getReporter(s.log, reporterConfig)

    compilers.value.scalac().compile(
      input.toArray,
      noChanges,
      arguments.toArray,
      CompileOutput(out),
      noopCallback,
      reporter,
      CompilerCache.getDefault,
      s.log,
      java.util.Optional.empty()
    )
  }
}
