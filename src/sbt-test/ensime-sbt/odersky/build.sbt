ivyLoggingLevel := UpdateLogging.Quiet

val `scala-library` = project.settings(
    organization := "org.scala-lang",
    version := "2.10.10",
    crossPaths := false,
    autoScalaLibrary := false,
    managedScalaInstance := false
)
