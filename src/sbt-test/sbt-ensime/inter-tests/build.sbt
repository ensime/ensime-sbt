lazy val core = project.in(file("core")).configs(IntegrationTest)


lazy val server = project.in(file("server")).dependsOn(
  core,
  // depend on "it" dependencies in "test" or sbt adds them to the release deps!
  // https://github.com/sbt/sbt/issues/1888
  core % "test->test",
  core % "it->it"
).configs(IntegrationTest)

