ivyLoggingLevel := UpdateLogging.Quiet

scalaVersion in ThisBuild := "2.11.7"
scalaOrganization := "org.typelevel"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")

// and not only a custom scalaOrganization, but a different version of
// scala for ensime (uses the same org as the build)
ensimeScalaVersion in ThisBuild := "2.11.8"
