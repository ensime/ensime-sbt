// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.sexp.formats

import org.ensime.sexp._
import shapeless._
import shapeless.syntax.typeable._

trait SexpFormats {
  /**
   * Constructs an `SexpFormat` from its two parts, `SexpReader` and `SexpWriter`.
   */
  def sexpFormat[T](reader: SexpReader[T], writer: SexpWriter[T]) = new SexpFormat[T] {
    def write(obj: T) = writer.write(obj)
    def read(json: Sexp) = reader.read(json)
  }

  implicit def sexpIdentityFormat[T <: Sexp: Typeable]: SexpFormat[T] = new SexpFormat[T] {
    def write(o: T) = o
    def read(v: Sexp) = v.cast[T].getOrElse { deserializationError(v) }
  }

  // performance boilerplate
  implicit val SexpFormat_ : SexpFormat[Sexp] = cachedImplicit
  implicit val SexpConsFormat: SexpFormat[SexpCons] = cachedImplicit
  implicit val SexpAtomFormat: SexpFormat[SexpAtom] = cachedImplicit
  implicit val SexpStringFormat: SexpFormat[SexpString] = cachedImplicit
  implicit val SexpNumberFormat: SexpFormat[SexpNumber] = cachedImplicit
  implicit val SexpCharFormat: SexpFormat[SexpChar] = cachedImplicit
  implicit val SexpSymbolFormat: SexpFormat[SexpSymbol] = cachedImplicit

}
