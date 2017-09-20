scalaVersion in ThisBuild := "2.12.2"

ensimeIgnoreMissingDirectories in ThisBuild := true

lazy val a = project

lazy val b = project.dependsOn(a)

lazy val c = project.dependsOn(a).dependsOn(b % "test->test")
