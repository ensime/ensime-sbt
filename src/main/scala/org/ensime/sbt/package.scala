package org.ensime

import scala.collection.immutable.ListMap
import org.ensime.sbt.util._

package object sbt {
  type KeyMap = ListMap[KeywordAtom, SExp]
  val KeyMap = (ListMap[KeywordAtom, SExp] _)

  implicit def tuples2TupleKeyMapOps(tuples: List[(KeywordAtom, SExp)]): TupleKeyMapOps = new TupleKeyMapOps(tuples)
  class TupleKeyMapOps(tuples: List[(KeywordAtom, SExp)]) {
    def toKeyMap: KeyMap = KeyMap() ++ tuples
  }

  implicit def keyMap2KeyMapOps(keyMap: KeyMap): KeyMapOps = new KeyMapOps(keyMap)
  class KeyMapOps(m1: KeyMap) {
    def simpleMerge(m2:KeyMap):KeyMap = {
      val keys = m1.keys.toList.diff(m2.keys.toList) ++ m2.keys
      val merged: KeyMap = keys.map{ key =>
        (m1.get(key), m2.get(key)) match{
          case (Some(s1),None) => (key, s1)
          case (None,Some(s2)) => (key, s2)
          case (Some(SExpList(items1)),
            Some(SExpList(items2))) => (key, SExpList(items1 ++ items2))
          case (Some(s1:SExp),Some(s2:SExp)) => (key, s2)
          case _ => (key, NilAtom())
        }
      }.toKeyMap
      merged
    }
  }

  type SymMap = ListMap[scala.Symbol, Any]
  val SymMap = (ListMap[scala.Symbol, Any] _)
}
