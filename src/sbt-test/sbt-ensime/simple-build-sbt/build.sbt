ivyLoggingLevel := UpdateLogging.Quiet

scalaVersion in ThisBuild := "2.12.2"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")

ensimeIgnoreMissingDirectories := true
