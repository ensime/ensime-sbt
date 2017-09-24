// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import scala.util.Properties.{versionNumberString => sbtScalaVersion}

import CoursierHelper._
import EnsimeCoursierKeys._
import EnsimeKeys._
import sbt._
import sbt.Keys._

object EnsimeCoursierKeys {
  // can't include Coursier keys in our public API because it is shaded
  val ensimeRepositoryUrls = settingKey[Seq[String]](
    "The maven repositories to download the scala compiler, ensime-server and ensime-plugins jars"
  )

  def addEnsimeScalaPlugin(module: ModuleID, args: String = ""): Seq[Setting[_]] = {
    ensimeScalacOptions += {
      val jar = resolveSingleJar(module, ensimeScalaVersion.value, ensimeRepositoryUrls.value)
      s"-Xplugin:${jar.getCanonicalFile}${args}"
    }
  }

}

/**
 * Defines the tasks that resolve all the jars needed to start the
 * ensime-server.
 *
 * Intentionally separated from EnsimePlugin to allow corporate users
 * to avoid a dependency on coursier and provide hard coded jar paths.
 */
object EnsimeCoursierPlugin extends AutoPlugin {
  override def requires = EnsimePlugin
  override def trigger = allRequirements
  val autoImport = EnsimeCoursierKeys

  import EnsimeKeys._

  override lazy val buildSettings = Seq(
    ensimeRepositoryUrls := Seq(
      // intentionally not using the ivy cache because it's very unreliable
      "https://repo1.maven.org/maven2/"
    ),

    ensimeScalaJars := resolveScalaJars(scalaOrganization.value, ensimeScalaVersion.value, ensimeRepositoryUrls.value),
    ensimeScalaProjectJars := resolveScalaJars("org.scala-lang", sbtScalaVersion, ensimeRepositoryUrls.value),
    ensimeServerJars := resolveEnsimeJars(scalaOrganization.value, ensimeScalaVersion.value, ensimeServerVersion.value, ensimeRepositoryUrls.value),
    ensimeServerProjectJars := resolveEnsimeJars("org.scala-lang", sbtScalaVersion, ensimeProjectServerVersion.value, ensimeRepositoryUrls.value)
  )

}
