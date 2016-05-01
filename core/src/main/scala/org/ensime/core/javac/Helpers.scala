// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core.javac

import akka.event.slf4j.SLF4JLogging
import com.sun.source.tree.{ Tree, IdentifierTree }
import com.sun.source.util.TreePath
import javax.lang.model.`type`.{ DeclaredType, PrimitiveType, TypeKind, TypeMirror }
import javax.lang.model.element.ElementKind
import javax.lang.model.element.{ Element, TypeElement }
import org.ensime.core.{ DocFqn, DocSig }

final case class JavaFqn(pack: Option[String], typename: Option[String], fieldOrMethod: Option[String]) {
  def toDocSig = DocSig(DocFqn(pack.getOrElse(""), typename.getOrElse("")), fieldOrMethod)
  def toFqnString = Array(pack, typename, fieldOrMethod).flatten.mkString(".")
  def toQueryString = Array(pack, typename.map(_.replace(".", "$")), fieldOrMethod).flatten.mkString(".")
}

object JavaFqn {
  def apply(pack: String, tpe: String, fieldOrMethod: Option[String]): JavaFqn = {
    JavaFqn(
      if (pack.isEmpty) None else Some(pack),
      if (tpe.isEmpty) None else Some(tpe),
      fieldOrMethod
    )
  }
}

trait Helpers extends UnsafeHelpers with SLF4JLogging {

  def typeMirror(c: Compilation, t: Tree): Option[TypeMirror] = {
    Option(c.trees.getTypeMirror(c.trees.getPath(c.compilationUnit, t)))
  }

  def typeElement(c: Compilation, t: Tree): Option[Element] = {
    typeMirror(c, t).map(c.types.asElement)
  }

  def element(c: Compilation, path: TreePath): Option[Element] = {
    Option(c.trees.getElement(path)).orElse(unsafeGetElement(path.getLeaf)).orElse {
      Option(c.trees.getTypeMirror(path)).flatMap { t => Option(c.types.asElement(t)) }
    }
  }

  private def parseFqnAsClass(s: String): Option[JavaFqn] = {
    val (front, back) = s.split("\\.").partition { s => s.forall(Character.isLowerCase) }
    Some(JavaFqn(front.mkString("."), back.mkString("."), None))
  }

  def fqn(c: Compilation, el: Element): Option[JavaFqn] = {
    el.getKind match {

      case ElementKind.LOCAL_VARIABLE | ElementKind.PARAMETER =>
        Some(JavaFqn(None, None, Some(el.getSimpleName.toString)))

      case ElementKind.CONSTRUCTOR | ElementKind.ENUM_CONSTANT
        | ElementKind.METHOD | ElementKind.FIELD =>

        Option(el.getEnclosingElement).flatMap(fqn(c, _)).map(_.copy(fieldOrMethod = Some(el.toString)))

      case k => parseFqnAsClass(el.toString)
    }
  }

  def fqn(c: Compilation, p: TreePath): Option[JavaFqn] = {
    element(c, p).flatMap(fqn(c, _)).orElse({
      p.getLeaf match {
        case t: IdentifierTree => Some(JavaFqn(None, None, Some(t.getName.toString)))
        case t => None
      }
    }).orElse(fqn(c, c.trees.getTypeMirror(p)))
  }

  def fqn(c: Compilation, t: Tree): Option[JavaFqn] = {
    Option(c.trees.getPath(c.compilationUnit, t)).flatMap { p => fqn(c, p) }
  }

  def fqn(c: Compilation, tm: TypeMirror): Option[JavaFqn] = {
    // "Using instanceof is not necessarily a reliable idiom for
    // determining the effective class of an object in this modeling
    // hierarchy since an implementation may choose to have a single
    // object implement multiple TypeMirror subinterfaces." --
    // TypeMirror docs
    tm match {
      case tm: DeclaredType if tm.getKind == TypeKind.DECLARED => {
        tm.asElement match {
          case te: TypeElement => parseFqnAsClass(te.getQualifiedName.toString)
          case _ => {
            None
          }
        }
      }
      case tm: PrimitiveType if tm.getKind.isPrimitive => Some(JavaFqn(None, Some(tm.toString), None))
      case _ => None
    }
  }
}
