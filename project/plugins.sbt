scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet
addSbtPlugin("com.fommil" % "sbt-sensible" % "1.1.6")

val coursierVersion = "1.0.0-M15-5"
addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursierVersion)
addSbtPlugin("io.get-coursier" % "sbt-shading" % coursierVersion)

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
