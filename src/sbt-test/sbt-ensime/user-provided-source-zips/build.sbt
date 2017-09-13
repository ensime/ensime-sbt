
lazy val core = project.in(file("core")).settings(
  ensimeUnmanagedSourceArchives += (baseDirectory in ThisBuild).value / "openjdk-langtools/openjdk6-langtools-src.zip"
)

