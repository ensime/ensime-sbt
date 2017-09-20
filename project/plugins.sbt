scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet
scalaVersion := "2.10.6"

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.2.2")

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC11")
//addSbtPlugin("io.get-coursier" % "sbt-shading" % "1.0.0-RC11")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
