scalaVersion in ThisBuild := "2.12.2"

ensimeIgnoreMissingDirectories in ThisBuild := true

ensimeScalacOptions +=  "-wibble"

lazy val a = project.settings(
  ensimeScalacOptions +=  "-wobble"
)

ensimeScalacOptions in a in Test +=  "-wriggle"
