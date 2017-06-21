scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.2.0" exclude("io.get-coursier", "sbt-coursier"))
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

// hold coursier at RC2 https://github.com/coursier/coursier/issues/590
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC2")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-RC2")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
