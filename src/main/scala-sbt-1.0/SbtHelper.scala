// for using some private[sbt] functionality
package sbt

import sbt.ConcurrentRestrictions.Tag
import Tests.{Output => TestOutput, _}
import sbt.testing.Runner

import scala.util.Try

object SbtHelper {
  def constructForkTests(runners: Map[TestFramework, Runner], tests: List[TestDefinition], config: Execution, classpath: Seq[File], fork: ForkOptions, log: Logger, tag: Tag): Task[TestOutput] = {
    ForkTests(runners, tests.toVector, config, classpath, fork, log, tag)
  }

  def reportError(error: Try[Unit]): Unit = error.fold(
    e => sys.error(e.getLocalizedMessage),
    x => x
  )

  def showShow[A](s: Show[A], a: A): String = s.show(a)
}
