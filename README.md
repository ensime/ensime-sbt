# ENSIME SBT

This [sbt](http://github.com/sbt/sbt) plugin generates a `.ensime` file and provides various convenience commands for interacting with [ENSIME](http://github.com/ensime/ensime-server).

## Install

Add these lines to `~/.sbt/0.13/plugins/plugins.sbt` as opposed to `project/plugins.sbt` (the decision to use ENSIME is per-user, rather than per-project):

```scala
addSbtPlugin("org.ensime" % "ensime-sbt" % "0.1.7")
```

**Check that again**, if you incorrectly used `~/.sbt/0.13/plugins.sbt` you'll get an sbt resolution error, it really has to be in the `plugins` folder.

## Commands

* `gen-ensime` --- Generate a `.ensime` for the project.
* `gen-ensime-meta` --- Generate a `project/.ensime` for the meta-project.
* `debugging` --- Add debugging flags to all forked JVM processes.
* `debugging-off` --- Remove debugging flags from all forked JVM processes.

Note that downloading and resolving the sources and javadocs can take some time on first use.

(Copied from [EnsimePlugin.scala](https://github.com/ensime/ensime-sbt/blob/master/src/main/scala/EnsimePlugin.scala#L59))

## Customise

Customising [EnsimeKeys](https://github.com/ensime/ensime-sbt/blob/master/src/main/scala/EnsimePlugin.scala#L21) is done via the usual sbt mechanism, e.g. insert the following into `~/.sbt/0.13/ensime.sbt`

```scala
import org.ensime.Imports.EnsimeKeys

EnsimeKeys.debugPort := 1337
```
