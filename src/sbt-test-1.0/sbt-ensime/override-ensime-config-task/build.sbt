ivyLoggingLevel := UpdateLogging.Quiet

libraryDependencies += "org.threeten" % "threeten-extra" % "1.3.2"

ensimeConfigTask := {
  val configOrig = ensimeConfigTask.evaluated
  configOrig.copy(
    projects = for(proj <- configOrig.projects) yield {
      proj.copy(
        libraryJars = proj.libraryJars.filterNot(_.getName startsWith "threeten-extra")
      )
    }
  )
}

scalaVersion in ThisBuild := "2.12.2"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")

ensimeIgnoreMissingDirectories := true
ensimeConfigLegacy := false
