import sbt._
import Keys._

object BadBuild extends Build {
  override val settings = super.settings ++ Seq(
    ivyLoggingLevel := UpdateLogging.Quiet
  )

  // settings not shared between all projects, so missing from .ensime
  val root = project settings(
    scalacOptions in Compile := Seq("-Xlog-reflective-calls"),
    scalaVersion := "2.11.11"
  )
}
