// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime

import java.io.File
import Predef.{ any2stringadd => _, _ => _ }
import org.ensime.api._
import org.ensime.util.file._

import scala.collection.breakOut

package object config {

  implicit class RichEnsimeConfig(val c: EnsimeConfig) extends AnyVal {
    def scalaSourceFiles: Set[File] =
      c.modules.values.flatMap((m: EnsimeModule) => m.scalaSourceFiles)(breakOut)
  }

  implicit class RichEnsimeModule(val m: EnsimeModule) extends AnyVal {
    def scalaSourceFiles: Set[File] = {
      val s = for {
        root <- m.sourceRoots
        file <- root.tree
        if file.isFile && file.isScala
      } yield file

      s.toSet
    }
  }
}
