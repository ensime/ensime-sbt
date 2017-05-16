ivyLoggingLevel := UpdateLogging.Quiet

scalaVersion in ThisBuild := "2.11.11"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")

libraryDependencies += "commons-io" % "commons-io" % "2.5"
libraryDependencies += "com.google.guava" % "guava" % "19.0" % "provided"
