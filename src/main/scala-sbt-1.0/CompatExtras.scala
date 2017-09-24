package org.ensime

import sbt.Setting

trait CompatExtras {
  val compatSettings: Seq[Setting[_]] = Nil
}
