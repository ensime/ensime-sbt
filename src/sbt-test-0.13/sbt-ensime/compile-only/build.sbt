val a = project
val b = project.settings(
  scalacOptions in (Test, ensimeCompileOnly) ++= Seq("-Xshow-phases")
)
