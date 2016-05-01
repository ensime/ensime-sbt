// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core.javac

import com.sun.source.tree._
import com.sun.source.tree.Tree.Kind

import com.sun.source.util.TreePath
import scala.collection.JavaConversions._

object ScopeFor {

  def apply(compilation: Compilation, position: Int): Option[Scope] = {

    PathFor(compilation, position) map { path =>

      val sourcePositions = compilation.trees.getSourcePositions

      val root = path.getCompilationUnit

      val statements: List[_ <: StatementTree] = path.getLeaf.getKind match {
        case Kind.BLOCK => path.getLeaf.asInstanceOf[BlockTree].getStatements.toList
        case Kind.FOR_LOOP => path.getLeaf.asInstanceOf[ForLoopTree].getInitializer.toList
        case Kind.ENHANCED_FOR_LOOP => List(path.getLeaf.asInstanceOf[EnhancedForLoopTree].getStatement)
        case Kind.CASE => path.getLeaf.asInstanceOf[CaseTree].getStatements.toList
        case Kind.METHOD => path.getLeaf.asInstanceOf[MethodTree].getParameters.toList
        case otherwise => Nil
      }

      val isAtPosition: StatementTree => Boolean = {
        sourcePositions.getStartPosition(root, _) <= position
      }

      val treePathOption = for {
        tree <- statements.reverse.find(isAtPosition)
      } yield new TreePath(path, tree)

      compilation.trees.getScope(treePathOption.getOrElse(path))
    }
  }
}
