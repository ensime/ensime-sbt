// Copyright (C) 2015 Sam Halliday
// License: http://www.gnu.org/licenses/gpl.html

import difflib.DiffUtils
import sbt._
import Keys._
import collection.JavaConverters._
import scala.util.Properties
import org.ensime.CommandSupport
import org.ensime.EnsimeKeys._

object EnsimeSbtTestSupport extends AutoPlugin {
  import CommandSupport._

  override def requires = org.ensime.EnsimePlugin
  override def trigger = allRequirements

  private lazy val parser = complete.Parsers.spaceDelimited("<arg>")
  override lazy val buildSettings = Seq(
    ensimeServerVersion := "2.0.0", // our CI needs stable jars
    ensimeProjectServerVersion := "2.0.0", // our CI needs stable jars
    commands += Command.args("ensimeExpect", "<args>")(ensimeExpect)
  )

  override lazy val projectSettings = Seq(
    ivyLoggingLevel := UpdateLogging.Quiet,
    InputKey[Unit]("checkJavaOptions") := {
      val args = parser.parsed.toList
      val opts = javaOptions.value.toList.map(_.toString)
      if (args != opts) throw new MessageOnlyException(s"$opts != $args")
    }
  )

  // must be a Command to avoid recursing into aggregate projects
  def ensimeExpect: (State, Seq[String]) => State = { (state, args) =>
    val extracted = Project.extract(state)
    implicit val s = state
    implicit val pr = extracted.currentRef
    implicit val bs = extracted.structure

    val projname = org.ensime.EnsimeKeys.ensimeName.gimmeOpt.getOrElse(name.gimme)
    val propDir = file(sys.props("plugin.test.directory"))
    val origDir = propDir / "sbt-ensime" / projname

    val baseDir = baseDirectory.gimme.getCanonicalPath.replace(raw"\", "/")
    val log = state.log

    val jdkHome = javaHome.gimme.getOrElse(file(Properties.jdkHome)).getAbsolutePath

    val normalizedFilenames = args.map(filename => filename
      .replace("{sbtVersion}", sbtVersion.gimme.split("[.]").take(2).mkString("-", ".", ""))
    )

    val List(got, expect) = normalizedFilenames.map { filename =>
      log.info(s"loading $filename")
      // not windows friendly
      IO.readLines(file(filename)).map {
        line =>
          line.
            replace(raw"\\", "/").
            replace(baseDir, "BASE_DIR").
            replace(baseDir.replace("/private", ""), "BASE_DIR"). // workaround for https://github.com/ensime/ensime-sbt/issues/151
            replace(Properties.userHome + "/.ivy2", "IVY_DIR").
            replace("C:/Users/appveyor/.ivy2", "IVY_DIR").
            replace(Properties.userHome + "/.coursier", "COURSIER_DIR").
            replace("C:/Users/appveyor/.coursier", "COURSIER_DIR").
            replace("https/repository.jboss.org", "https/repo1.maven.org/maven2"). // maven central hashcode mismatches
            replaceAll("""/usr/lib/jvm/[^/"]++""", "JDK_HOME").
            replaceAll("""/opt/zulu[/]?[^/"]++""", "JDK_HOME").
            replaceAll("""/Library/Java/JavaVirtualMachines/[^/]+/Contents/Home""", "JDK_HOME").
            replaceAll("""C:/Program Files/Java/[^/"]++""", "JDK_HOME").
            replace(jdkHome, "JDK_HOME").
            replaceAll(""""-Dplugin[.]test[.]directory=[^"]++"""", "").
            replaceAll(""""-Dplugin[.]version=[^"]++"""", "").
            replaceAll(""""-Xfatal-warnings"""", ""). // ensime-server only has these in CI
            replaceAll("""/[^/]++/jars/sbt-ensime.jar"""", """/HEAD/jars/sbt-ensime.jar"""").
            replaceAll("""/[^/]++/srcs/sbt-ensime-sources.jar"""", """/HEAD/srcs/sbt-ensime-sources.jar"""").
            replaceAll(""""-Dsbt[.]global[.]base=BASE_DIR/global"""", "").
            replaceAll(raw"\s++", " ").
            replace("( ", "(").replace(" )", ")")
      }
    }.toList

    val deltas = DiffUtils.diff(expect.asJava, got.asJava).getDeltas.asScala
    if (!deltas.isEmpty) {
      IO.write(origDir / normalizedFilenames(1), got.mkString("\n"))
      throw new MessageOnlyException(s".ensime diff: ${deltas.mkString("\n")}")
    }

    state
  }

}
