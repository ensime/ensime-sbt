package runEnsimeMain

import java.io.{File, PrintWriter}

object printHello extends App {
  val output = new PrintWriter(new File(args(0)))
  try output.write("Hello!")
  finally output.close()
}
