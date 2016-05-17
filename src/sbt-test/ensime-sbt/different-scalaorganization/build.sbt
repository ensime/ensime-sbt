ivyLoggingLevel := UpdateLogging.Quiet

// unless "in ThisBuild" is appended, the correct base scalac flags are not detected
scalaVersion := "2.11.7"
scalaOrganization := "org.typelevel"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")
