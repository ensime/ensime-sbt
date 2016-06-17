val a = project
val b = project.settings(
  scalacOptions in (Test, EnsimeKeys.ensimeCompileOnly) ++= Seq("-Xshow-phases")
)
