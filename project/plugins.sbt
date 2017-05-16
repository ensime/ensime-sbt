scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet
addSbtPlugin("com.fommil" % "sbt-sensible" % "1.1.14")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC1")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-RC1")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
