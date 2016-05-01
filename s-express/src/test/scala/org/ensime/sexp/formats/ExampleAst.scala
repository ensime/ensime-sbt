// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.sexp.formats

/**
 * An example Abstract Syntax Tree / family.
 */
object ExampleAst {
  sealed trait Token {
    def text: String
  }

  sealed trait RawToken extends Token
  final case class Split(text: String) extends RawToken
  final case class And(text: String) extends RawToken
  final case class Or(text: String) extends RawToken

  sealed trait ContextualMarker extends RawToken
  final case class Like(text: String) extends ContextualMarker
  final case class Prefer(text: String) extends ContextualMarker
  final case class Negate(text: String) extends ContextualMarker

  sealed trait TokenTree extends Token
  sealed trait ContextualToken extends TokenTree
  sealed trait CompressedToken extends TokenTree
  final case class Unparsed(text: String) extends TokenTree
  final case class AndCondition(left: TokenTree, right: TokenTree, text: String) extends TokenTree
  final case class OrCondition(left: TokenTree, right: TokenTree, text: String) extends TokenTree

  final case class Ignored(text: String = "") extends TokenTree
  final case class Unclear(text: String = "") extends TokenTree

  object SpecialToken extends TokenTree {
    // to test case object serialisation
    def text = ""
  }

  sealed trait Term extends TokenTree {
    def field: DatabaseField
  }

  final case class DatabaseField(column: String)
  final case class FieldTerm(text: String, field: DatabaseField, value: String) extends Term
  final case class BoundedTerm(
    text: String,
    field: DatabaseField,
    low: Option[String] = None,
    high: Option[String] = None,
    inclusive: Boolean = false
  ) extends Term
  final case class LikeTerm(term: FieldTerm, like: Option[Like]) extends Term {
    val text = like.map(_.text).getOrElse("")
    val field = term.field
  }
  final case class PreferToken(tree: TokenTree, before: Option[Prefer], after: Option[Prefer]) extends TokenTree {
    val text = before.getOrElse("") + tree.text + after.getOrElse("")
  }
  final case class InTerm(field: DatabaseField, value: List[String], text: String = "") extends CompressedToken

  final case class QualifierToken(text: String, field: DatabaseField) extends ContextualToken with Term
}
