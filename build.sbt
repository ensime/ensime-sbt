sbtPlugin := true

name := "ensime-sbt-cmd"

organization := "org.ensime"

version := "0.1.1"

//pgpSecretRing := file("/Users/aemon/.gnupg/secring.gpg")

crossScalaVersions := Seq("2.9.0","2.9.1","2.9.1-1","2.9.2", "2.10.0-M6")

scalacOptions := Seq("-deprecation", "-unchecked")

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishArtifact in (Compile, packageBin) := true

publishArtifact in (Test, packageBin) := false

publishArtifact in (Compile, packageDoc) := true

publishArtifact in (Compile, packageSrc) := true

publishMavenStyle := true

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>http://github.com/aemoncannon/ensime-sbt-cmd</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>http://github.com/aemoncannon/ensime-sbt-cmd</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:aemoncannon/ensime-sbt-cmd.git</url>
    <connection>scm:git:git@github.com:aemoncannon/ensime-sbt-cmd.git</connection>
  </scm>
  <developers>
    <developer>
      <id>aemoncannon</id>
      <name>Aemon Cannon</name>
      <url>http://github.com/aemoncannon</url>
    </developer>
  </developers>)