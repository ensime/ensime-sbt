// Copyright (C) 2015 Sam Halliday
// License: http://www.gnu.org/licenses/gpl.html

import difflib.DiffUtils
import sbt._
import Keys._
import collection.JavaConverters._
import util.Properties
import org.ensime.CommandSupport

object EnsimeSbtTestSupport extends AutoPlugin {
  import CommandSupport._

  {
    // horrible hack to enable side effects in EnsimePlugin
    System.setProperty("ensime.sbt.debug", "true")
  }

  override def trigger = allRequirements

  private lazy val parser = complete.Parsers.spaceDelimited("<arg>")
  override lazy val buildSettings = Seq(
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

    val baseDir = baseDirectory.gimme.getCanonicalPath.replace(raw"\", "/")
    val log = state.log

    val jdkHome = javaHome.gimme.getOrElse(file(Properties.jdkHome)).getAbsolutePath

    val List(got, expect) = args.map { filename =>
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
            replaceAll("""/usr/lib/jvm/[^/"]++""", "JDK_HOME").
            replaceAll("""/opt/zulu[/]?[^/"]++""", "JDK_HOME").
            replaceAll("""/Library/Java/JavaVirtualMachines/[^/]+/Contents/Home""", "JDK_HOME").
            replaceAll("""C:/Program Files/Java/[^/"]++""", "JDK_HOME").
            replace(jdkHome, "JDK_HOME").
            replaceAll(raw""""-Dplugin[.]version=[.\d]++(-SNAPSHOT)?"""", "").
            replaceAll(""""-Xfatal-warnings"""", ""). // ensime-server only has these in CI
            replaceAll(raw"""/[.\d]++(-SNAPSHOT)?/jars/sbt-ensime.jar"""", """/HEAD/jars/sbt-ensime.jar"""").
            replaceAll(raw"""/[.\d]++(-SNAPSHOT)?/srcs/sbt-ensime-sources.jar"""", """/HEAD/srcs/sbt-ensime-sources.jar"""").
            replaceAll(raw""""-Dsbt[.]global[.]base=BASE_DIR/global"""", "").
            replaceAll(raw"\s++", " ").
            replace("0.13.11", "0.13.12"). // upgraded sbt
            replace("( ", "(").replace(" )", ")")
      }
    }.toList

    val deltas = DiffUtils.diff(expect.asJava, got.asJava).getDeltas.asScala
    if (!deltas.isEmpty) {
      // for local debugging
      IO.write(file(Properties.userHome + "/ensime-got"), got.mkString("\n"))
      throw new MessageOnlyException(s".ensime diff: ${deltas.mkString("\n")}")
    }

    state
  }

}
