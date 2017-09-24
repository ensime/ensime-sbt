libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25"
excludeDependencies += SbtExclusionRule("org.slf4j", "slf4j-simple")
ivyLoggingLevel := UpdateLogging.Quiet

scalacOptions ++= Seq("-unchecked", "-deprecation")

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.2.3")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC12")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-RC12")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
