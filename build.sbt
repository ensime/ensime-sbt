import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import SonatypeSupport._

organization := "org.ensime"
version := "1.11.4-SNAPSHOT"
name := "sbt-ensime"

sbtPlugin := true

Sensible.settings

sonatype("ensime", "ensime-sbt", Apache2)

// intentionally old version of scalariform: do not force an upgrade upon users
libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.4"
// scalap needed for :scala-compiler-jars in project/.ensime
libraryDependencies += "org.scala-lang" % "scalap" % scalaVersion.value

ScalariformKeys.preferences := FormattingPreferences().setPreference(AlignSingleLineCaseStatements, true)

scriptedSettings
scriptedBufferLog := false

scriptedLaunchOpts := Seq(
  "-Dplugin.version=" + version.value,
  // .jvmopts is ignored, simulate here
  "-XX:MaxPermSize=256m", "-Xmx2g", "-Xss2m"
)
