scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.2.0")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC9")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-RC9")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
