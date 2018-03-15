libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.25"
excludeDependencies += ExclusionRule("org.slf4j", "slf4j-simple")
ivyLoggingLevel := UpdateLogging.Quiet

scalacOptions ++= Seq("-unchecked", "-deprecation")

addSbtPlugin("com.fommil" % "sbt-sensible" % "2.3.1")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.2")

