scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet
addSbtPlugin("com.fommil" % "sbt-sensible" % "1.1.11")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M15-5")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-M15-5")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
