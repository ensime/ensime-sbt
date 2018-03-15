scalaVersion in ThisBuild := "2.12.2"

ensimeIgnoreMissingDirectories in ThisBuild := true

ensimeScalacOptions +=  "-wibble"

lazy val a = project.settings(
  ensimeScalacOptions += "-wobble",
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
  addCompilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
)

ensimeScalacOptions in a in Test +=  "-wriggle"
