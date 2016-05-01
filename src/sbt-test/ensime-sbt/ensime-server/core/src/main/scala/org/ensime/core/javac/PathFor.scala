// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core.javac

import com.sun.source.tree._
import com.sun.source.util.SourcePositions
import com.sun.source.util.TreePath
import com.sun.source.util.TreePathScanner
import scala.util.Try
import scala.util.Failure
import scala.util.Success

case class Hit(path: TreePath) extends Exception

class PathFor(private val sourcePositions: SourcePositions, private val position: Int) extends TreePathScanner[Unit, Unit] {

  private def isAtPosition(tree: Tree): Boolean = {
    val startPosition = sourcePositions.getStartPosition(getCurrentPath().getCompilationUnit(), tree)
    val endPosition = sourcePositions.getEndPosition(getCurrentPath().getCompilationUnit(), tree)
    startPosition <= position && endPosition >= position
  }

  override def scan(tree: Tree, p: Unit): Unit = {

    Option(tree) match {

      case Some(t) if isAtPosition(t) && t.getKind == Tree.Kind.ERRONEOUS =>
        t.accept(this, p)
        throw Hit(getCurrentPath)

      case Some(t) if isAtPosition(t) =>
        super.scan(t, p)
        throw Hit(new TreePath(getCurrentPath(), tree))

      case _ =>
    }
  }
}

object PathFor {

  def apply(c: Compilation, position: Int): Option[TreePath] = {

    val sourcePositions = c.trees.getSourcePositions
    val tree = new TreePath(c.compilationUnit)

    Try(new PathFor(sourcePositions, position).scan(tree, ())) match {
      case Success(x) => None
      case Failure(Hit(path)) => Some(path)
      case Failure(e) => None
    }
  }
}
