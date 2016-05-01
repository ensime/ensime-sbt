package org.ensime.core.javac

import com.sun.source.util.Trees;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

class Compilation(val javacTask: JavacTask, val trees: Trees, val compilationUnit: CompilationUnitTree) {

  def types = javacTask.getTypes

  def elements = javacTask.getElements
}

object Compilation {

  def apply(javacTask: JavacTask, compilationUnit: CompilationUnitTree): Compilation = {
    new Compilation(javacTask, Trees.instance(javacTask), compilationUnit)
  }
}
