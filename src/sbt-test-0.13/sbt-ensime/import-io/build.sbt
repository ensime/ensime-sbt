import io.gatling.sbt.GatlingKeys._
import io.gatling.sbt.GatlingPlugin


lazy val root = Project("root", file("."))
  .enablePlugins(GatlingPlugin)
