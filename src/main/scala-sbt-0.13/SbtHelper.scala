// for using some private[sbt] functionality
package sbt

import sbt.ConcurrentRestrictions.Tag
import Tests.{Output => TestOutput, _}
import sbt.testing.Runner

object SbtHelper {
  def constructForkTests(runners: Map[TestFramework, Runner], tests: List[TestDefinition], config: Execution, classpath: Seq[File], fork: ForkOptions, log: Logger, tag: Tag): Task[TestOutput] = {
    ForkTests(runners, tests, config, classpath, fork, log, tag)
  }

  def reportError(error: Option[String]): Unit = error.foreach(sys.error)

  def showShow[A](s: Show[A], a: A): String = s.apply(a)
}
