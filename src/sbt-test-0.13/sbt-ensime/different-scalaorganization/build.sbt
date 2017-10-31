ivyLoggingLevel := UpdateLogging.Quiet

scalaVersion in ThisBuild := "2.12.2-bin-typelevel-4"
scalaOrganization in ThisBuild := "org.typelevel"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")

addEnsimeScalaPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

// and not only a custom scalaOrganization, but a different version of
// scala for ensime (uses the same org as the build)
ensimeScalaVersion in ThisBuild := "2.12.4-bin-typelevel-4"
ensimeIgnoreScalaMismatch := true

// WORKAROUND https://github.com/ensime/ensime-sbt/issues/239
// (apparently fixed in sbt 0.13.15)
//ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
