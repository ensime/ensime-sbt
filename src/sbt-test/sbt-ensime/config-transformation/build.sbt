ivyLoggingLevel := UpdateLogging.Quiet

scalaVersion in ThisBuild := "2.11.8"

import org.ensime.{EnsimeConfig, EnsimeModule}

//add a dummy module to the config
ensimeConfigTransformer := {(cfg: EnsimeConfig) => {
  val c = ensimeConfigTransformer.value(cfg)
  val dummyModule = EnsimeModule(
    name = "dummy",
    mainRoots = Set.empty,
    testRoots = Set.empty,
    targets = Set.empty,
    testTargets = Set.empty,
    dependsOnNames = Set.empty,
    compileJars = Set.empty,
    runtimeJars = Set.empty,
    testJars = Set.empty,
    sourceJars = Set.empty,
    docJars = Set.empty
  )
  c.copy(modules = c.modules ++ Map("dummy" -> dummyModule))
}}

//find the dummy module and change the name to "changed"
ensimeConfigTransformer := {(cfg: EnsimeConfig) => {
  val c = ensimeConfigTransformer.value(cfg)
  val updatedModules: Map[String, EnsimeModule] = c.modules.map{
    case ("dummy", mod) => "changed" -> mod.copy(name = "changed")
    case (name, mod) => name -> mod
  }
  c.copy(modules = updatedModules)
}}

//add a dummy java flag to the project config
ensimeConfigTransformerProject := {(cfg: EnsimeConfig) => {
  val c = ensimeConfigTransformerProject.value(cfg)
  c.copy(javaFlags = c.javaFlags ++ List("-Ddummy.flag"))
}}
