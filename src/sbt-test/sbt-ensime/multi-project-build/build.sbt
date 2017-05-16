scalaVersion in ThisBuild := "2.11.11"

ensimeIgnoreMissingDirectories in ThisBuild := true

lazy val a = project

lazy val b = project.dependsOn(a)

lazy val c = project.dependsOn(a).dependsOn(b % "test->test")
