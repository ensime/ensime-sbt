// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.api

import java.io.File
import scalariform.formatter.preferences.FormattingPreferences

// there is quite a lot of code in this file, when we clean up the
// config file format so that a lot of these hacks are no longer
// needed, we should move the functionality into a higher layer
// RichConfig to keep the API clean.

final case class EnsimeConfig(
    rootDir: File,
    cacheDir: File,
    javaHome: File,
    name: String,
    scalaVersion: String,
    compilerArgs: List[String],
    referenceSourceRoots: List[File],
    subprojects: List[EnsimeModule],
    formattingPrefs: FormattingPreferences,
    sourceMode: Boolean,
    javaLibs: List[File],
    // WORKAROUND: https://github.com/ensime/ensime-server/issues/1042
    disableSourceMonitoring: Boolean = false,
    disableClassMonitoring: Boolean = false
) {
  (rootDir :: cacheDir :: javaHome :: referenceSourceRoots ::: javaLibs).foreach { f =>
    require(f.exists, "" + f + " is required but does not exist")
  }

  /* Proposed alternatives to the legacy wire format field names */
  def root = rootDir
  val referenceSourceJars =
    (referenceSourceRoots ++ subprojects.flatMap(_.referenceSourceRoots)).toSet

  // some marshalling libs (e.g. spray-json) might not like extra vals
  val modules = subprojects.map { module => (module.name, module) }.toMap

  def compileClasspath: Set[File] = modules.values.toSet.flatMap {
    m: EnsimeModule => m.compileDeps ++ m.testDeps
  } ++ (if (sourceMode) List.empty else targetClasspath)

  def targetClasspath: Set[File] = modules.values.toSet.flatMap {
    m: EnsimeModule => m.targets ++ m.testTargets
  }

  def allJars: Set[File] = {
    modules.values.flatMap { m =>
      m.compileDeps ::: m.testDeps
    }.toSet
  } ++ javaLibs

  val allTargets: Set[File] =
    subprojects.flatMap(sm => sm.targets ::: sm.testTargets).toSet

  def allDocJars: Set[File] = modules.values.flatMap(_.docJars).toSet

  def scalaLibrary: Option[File] = allJars.find(_.getName.startsWith("scala-library"))
}

final case class EnsimeModule(
    name: String,
    targets: List[File],
    testTargets: List[File],
    dependsOnModules: List[String],
    compileDeps: List[File],
    testDeps: List[File],
    sourceRoots: List[File],
    docJars: List[File],
    referenceSourceRoots: List[File]
) {
  // only check the files, not the directories, see below
  (compileDeps ::: testDeps ::: referenceSourceRoots).foreach { f =>
    require(f.exists, "" + f + " is required but does not exist")
  }

  /*
   Proposed alternatives to the legacy wire format field names:
   */
  def compileJars = compileDeps
  def testJars = testDeps
  def referenceSourceJars = referenceSourceRoots

  def dependencies(implicit config: EnsimeConfig): List[EnsimeModule] =
    dependsOnModules.map(config.modules)

}
