// Copyright (C) 2014 - 2016 ENSIME Contributors
// Licence: Apache-2.0
package org.ensime

import sbt._
import scalaz._
import scalaz.concurrent.Task

/**
 * Ideally we'd put these in the EnsimeCoursierPlugin file, but we
 * have to keep that API clean so that the shading plugin doesn't
 * break compilation.
 *
 * WORKAROUND: https://github.com/coursier/coursier/issues/454
 */
private[ensime] object CoursierHelper {
  // methods that don't expose coursier APIs
  def resolveScalaJars(org: String, version: String, repos: Seq[String]): Seq[File] =
    resolveScalaJarsImpl(org, version)(resolvers(repos))
  def resolveEnsimeJars(org: String, scala: String, ensime: String, repos: Seq[String]): Seq[File] =
    resolveEnsimeJarsImpl(org, scala, ensime)(resolvers(repos))
  def resolveSingleJar(module: ModuleID, scalaVersion: String, repos: Seq[String]): File =
    resolveSingleJarImpl(module, scalaVersion)(resolvers(repos))

  private[this] def resolvers(urls: Seq[String]) = urls.map(coursier.MavenRepository(_))

  private[this] def resolveScalaJarsImpl(org: String, version: String)(implicit repos: Seq[coursier.Repository]): Seq[File] = resolve(
    org % "scalap" % version intransitive,
    org % "scala-compiler" % version intransitive,
    org % "scala-reflect" % version intransitive,
    org % "scala-library" % version intransitive
  )

  private[this] def resolveEnsimeJarsImpl(org: String, scala: String, ensime: String)(implicit repos: Seq[coursier.Repository]): Seq[File] = {
    val Some((major, minor)) = CrossVersion.partialVersion(scala)
    resolve(
      "org.ensime" % s"server_$major.$minor" % ensime,
      org % "scalap" % scala intransitive
    )
  }

  private[this] def resolveSingleJarImpl(module: ModuleID, scalaVersion: String)(implicit repos: Seq[coursier.Repository]): File = {
    val mod = CrossVersion(
      scalaVersion,
      CrossVersion.binaryScalaVersion(scalaVersion)
    )(module.intransitive)
    resolve(mod).head.getCanonicalFile
  }

  private[this] def resolve(modules: ModuleID*)(implicit repos: Seq[coursier.Repository]): Seq[File] = {
    val resolution = coursier.Resolution(
      modules.map { module =>
      coursier.Dependency(
        coursier.Module(
          module.organization,
          module.name
        ),
        module.revision,
        configuration = module.configurations.getOrElse(""),
        transitive = module.isTransitive
      )
    }.toSet
    )

    val fetch = coursier.Fetch.from(repos, coursier.Cache.fetch())
    val resolved = resolution.process.run(fetch).unsafePerformSync
    resolved.errors.foreach { err =>
      throw new RuntimeException(s"failed to resolve $err")
    }

    Task.gatherUnordered(
      resolved.artifacts.map(coursier.Cache.file(_).run)
    ).unsafePerformSync.flatMap {
        case -\/(err)                                    => throw new RuntimeException(err.message)
        case \/-(file) if !file.getName.endsWith(".jar") => None
        case \/-(file)                                   => Some(file)
      }
  }
}
