val a = project
val b = project.settings(
  scalacOptions in (Test, EnsimeKeys.compileOnly) += "-Xprint-types"
)
