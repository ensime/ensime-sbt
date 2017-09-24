package org.ensime

import sbt._

trait CompatExtrasKeys {
  val ensimeDebuggingArgs = settingKey[Seq[String]](
    "Java args for for debugging"
  )
}

trait CompatExtras {
  val compatSettings: Seq[Setting[_]] = Nil
}
