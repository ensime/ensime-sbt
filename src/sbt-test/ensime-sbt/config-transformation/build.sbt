ivyLoggingLevel := UpdateLogging.Quiet

// unless "in ThisBuild" is appended, the correct base scalac flags are not detected
scalaVersion := "2.11.7"

import org.ensime.{EnsimeConfig, EnsimeModule}

//add a dummy module to the config
EnsimeKeys.ensimeConfigTransformer := {(cfg: EnsimeConfig) => {
  val c = EnsimeKeys.ensimeConfigTransformer.value(cfg)
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
EnsimeKeys.ensimeConfigTransformer := {(cfg: EnsimeConfig) => {
  val c = EnsimeKeys.ensimeConfigTransformer.value(cfg)
  val updatedModules: Map[String, EnsimeModule] = c.modules.map{
    case ("dummy", mod) => "changed" -> mod.copy(name = "changed")
    case (name, mod) => name -> mod
  }
  c.copy(modules = updatedModules)
}}

//add a dummy java flag to the project config
EnsimeKeys.ensimeConfigTransformerProject := {(cfg: EnsimeConfig) => {
  val c = EnsimeKeys.ensimeConfigTransformerProject.value(cfg)
  c.copy(javaFlags = c.javaFlags ++ List("-Ddummy.flag"))
}}
