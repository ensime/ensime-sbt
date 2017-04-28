scalaVersion in ThisBuild := "2.11.8"

ensimeIgnoreMissingDirectories in ThisBuild := true

lazy val a = project

lazy val b = project.dependsOn(a)

lazy val c = project.dependsOn(a).dependsOn(b % "test->test")
