package runEnsimeMain

import scala.collection.JavaConversions._
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import java.io._


object printArgs extends App {

  def writeArgsToFile(filename: String): Unit = {
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean

    val jvmArgPrefixes = Seq("-Dtesting", "-Xm", "-agentlib", "-framework")
    val jvmArgs = runtimeMxBean.getInputArguments.toList.filter(
      arg => jvmArgPrefixes.exists(prefix => arg.startsWith(prefix))).sorted

    val envArgs = sys.env.toSeq.filter(_._1 startsWith "testing").sorted

    val properties = sys.props.toSeq.filter(_._1 startsWith "testing").sorted

    val output = new PrintWriter(new File(filename))
    try {
      output.write(properties.map(t => t._1 + "=" + t._2).mkString(" "))
      output.write("\n")
      output.write(jvmArgs.mkString(" "))
      output.write("\n")
      output.write(envArgs.map(t => t._1 + "=" + t._2).mkString(" "))
      if (args != null) {
        output.write("\n")
        output.write(args.mkString(" "))
      }
    } finally output.close()
  }

  writeArgsToFile(args(0))
}


