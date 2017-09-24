
ivyLoggingLevel := UpdateLogging.Quiet

// settings not shared between all projects, so missing from .ensime
val root = project settings(
  scalacOptions in Compile := Seq("-Xlog-reflective-calls"),
  scalaVersion := "2.12.2"
)