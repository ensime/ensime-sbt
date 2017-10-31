scalaVersion in ThisBuild := "2.12.2"

ensimeIgnoreMissingDirectories in ThisBuild := true

lazy val a = project

lazy val b = project.dependsOn(a)

lazy val c = project.dependsOn(a).dependsOn(b % "test->test")

lazy val d = project.dependsOn(a).dependsOn(b % "compile->compile;test->test")

lazy val e = project.dependsOn(a).dependsOn(b % "test")

lazy val f = project.configs(IntegrationTest).dependsOn(a % "test,it")
