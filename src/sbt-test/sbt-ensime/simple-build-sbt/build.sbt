ivyLoggingLevel := UpdateLogging.Quiet

scalaVersion in ThisBuild := "2.11.11"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")

ensimeIgnoreMissingDirectories := true
