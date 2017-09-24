package runEnsimeMain

import utest._

object printArgsTest extends TestSuite {
  val tests = this{
    'test1{
      printArgs.writeArgsToFile("output_test")
    }
  }
}