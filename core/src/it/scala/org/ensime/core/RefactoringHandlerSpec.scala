// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core

import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import org.ensime.api._
import org.ensime.fixture._
import org.ensime.util.EnsimeSpec
import org.scalatest.Assertions

class RefactoringHandlerSpec extends EnsimeSpec
    with IsolatedAnalyzerFixture
    with RichPresentationCompilerTestUtils
    with RefactoringHandlerTestUtils {

  val encoding = "UTF-16"
  def original = EnsimeConfigFixture.EmptyTestProject.copy(
    compilerArgs = List("-encoding", encoding)
  )

  // transitionary methods
  def ContentsSourceFileInfo(file: File, contents: String) =
    SourceFileInfo(file, Some(contents))
  def ContentsInSourceFileInfo(file: File, contentsIn: File) =
    SourceFileInfo(file, contentsIn = Some(contentsIn))

  "RefactoringHandler" should "format files and preserve encoding" in {
    withAnalyzer { (config, analyzerRef) =>
      val file = srcFile(config, "abc.scala", contents(
        "package blah",
        "   class Something {",
        "def f(i:   Int) =1",
        " val x = (1\u21922)",
        "   }"
      ), write = true, encoding = encoding)

      val analyzer = analyzerRef.underlyingActor

      analyzer.handleFormatFiles(List(new File(file.path)))
      val fileContents = readSrcFile(file, encoding)

      val expectedContents = contents(
        "package blah",
        "class Something {",
        "  def f(i: Int) = 1",
        "  val x = (1 \u2192 2)",
        "}"
      )
      fileContents should ===(expectedContents)
    }
  }

  it should "format files from ContentsSourceFileInfo with handleFormatFile" in {
    withAnalyzer { (dir, analyzerRef) =>
      val content = contents(
        "package blah",
        "   class  Something   {}"
      )
      val analyzer = analyzerRef.underlyingActor

      val formatted = analyzer.handleFormatFile(ContentsSourceFileInfo(new File("abc.scala"), content))
      val expectedContents = contents(
        "package blah",
        "class Something {}",
        ""
      )
      formatted should ===(expectedContents)
    }
  }

  it should "format files from ContentsInSourceFileInfo with handleFormatFile and handle encoding" in {
    withAnalyzer { (dir, analyzerRef) =>
      val file = srcFile(dir, "tmp-contents", contents(
        "package blah",
        "   class  Something   {}"
      ), write = true, encoding = encoding)

      val analyzer = analyzerRef.underlyingActor

      val formatted = analyzer.handleFormatFile(ContentsInSourceFileInfo(new File("abc.scala"), new File(file.path)))
      val expectedContents = contents(
        "package blah",
        "class Something {}",
        ""
      )
      formatted should ===(expectedContents)
    }
  }

  it should "format files from FileSourceFileInfo with handleFormatFile and handle encoding" in {
    withAnalyzer { (dir, analyzerRef) =>
      val file = srcFile(dir, "abc.scala", contents(
        "package blah",
        "   class  Something   {}"
      ), write = true, encoding = encoding)

      val analyzer = analyzerRef.underlyingActor

      val formatted = analyzer.handleFormatFile(SourceFileInfo(new File(file.path)))
      val expectedContents = contents(
        "package blah",
        "class Something {}",
        ""
      )
      formatted === (expectedContents)
    }
  }

  it should "not format invalid files" in withAnalyzer { (config, analyzerRef) =>
    val file = srcFile(config, "abc.scala", contents(
      "package blah",
      "invalid scala syntax"
    ), write = true, encoding = encoding)

    val analyzer = analyzerRef.underlyingActor

    analyzer.handleFormatFiles(List(new File(file.path)))
    val fileContents = readSrcFile(file, encoding)

    val expectedContents = contents(
      "package blah",
      "invalid scala syntax"
    )
    fileContents should ===(expectedContents)
  }

  it should "add imports on the first line" in withAnalyzer { (dir, analyzerRef) =>
    import org.ensime.util.file._

    val file = srcFile(dir, "tmp-contents", contents(
      "import java.lang.Integer.toBinaryString",
      "import java.lang.String.valueOf",
      "",
      "trait Temp {",
      "  valueOf(5)",
      "  vo(\"5\")",
      "  toBinaryString(27)",
      "}"
    ), write = true, encoding = encoding)

    val analyzer = analyzerRef.underlyingActor

    val procId = 1
    val result = analyzer.handleRefactorRequest(
      new RefactorReq(
        procId, AddImportRefactorDesc("java.lang.Integer.{valueOf => vo}", new File(file.path)), false
      )
    )
    val diffContent = extractDiffFromResponse(result, analyzer.charset)

    val relevantExpectedPart = s"""|@@ -2,2 +2,3 @@
                                   | import java.lang.String.valueOf
                                   |+import java.lang.Integer.{valueOf => vo}
                                   | \n""".stripMargin

    val expectedContents = expectedDiffContent(file.path, relevantExpectedPart)

    diffContent should ===(expectedContents)
  }

  it should "add imports even if none exist" in {
    withAnalyzer { (dir, analyzerRef) =>

      val file = srcFile(dir, "tmp-contents", contents(
        "package org.ensime.testing",
        "",
        "trait Temp {",
        "  valueOf(5)",
        "  vo(\"5\")",
        "  toBinaryString(27)",
        "}"
      ), write = true, encoding = encoding)

      val analyzer = analyzerRef.underlyingActor

      val procId = 1
      val result = analyzer.handleRefactorRequest(
        new RefactorReq(
          procId, AddImportRefactorDesc("java.lang.Integer", new File(file.path)), false
        )
      )

      val diffContent = extractDiffFromResponse(result, analyzer.charset)

      val relevantExpectedPart = contents(
        "@@ -2,2 +2,4 @@",
        " ",
        "+import java.lang.Integer",
        "+",
        " trait Temp {",
        ""
      )

      val expectedContents = expectedDiffContent(file.path, relevantExpectedPart)

      diffContent should ===(expectedContents)
    }
  }

  it should "rename a function id with params' opening/closing parenthesis on different lines" in withAnalyzer { (dir, analyzerRef) =>

    val file = srcFile(dir, "tmp-contents", contents(
      "package org.ensime.testing",
      "trait Foo {",
      "def doIt(",
      ") = \"\"",
      "}",
      ""
    ), write = true, encoding = encoding)

    val analyzer = analyzerRef.underlyingActor

    val procId = 1
    val result = analyzer.handleRefactorRequest(
      new RefactorReq(
        procId, RenameRefactorDesc("doItNow", new File(file.path), 43, 47), false
      )
    )

    val diffContent = extractDiffFromResponse(result, analyzer.charset)

    val relevantExpectedPart = s"""|@@ -2,3 +2,3 @@
                                   | trait Foo {
                                   |-def doIt(
                                   |+def doItNow(
                                   | ) = ""
                                   |""".stripMargin
    val expectedContents = expectedDiffContent(file.path, relevantExpectedPart)

    diffContent should ===(expectedContents)
  }

  it should "organize imports when 3 imports exist" in {
    withAnalyzer { (dir, analyzerRef) =>
      //when 3 imports exist
      // "produce a diff file in the unified output format"

      val file = srcFile(dir, "tmp-contents", contents(
        "import java.lang.Integer.{valueOf => vo}",
        "import java.lang.Integer.toBinaryString",
        "import java.lang.String.valueOf",
        " ",
        "trait Temp {",
        "  valueOf(5)",
        "  vo(\"5\")",
        "  toBinaryString(27)",
        "}"
      ), write = true, encoding = encoding)

      val analyzer = analyzerRef.underlyingActor

      val procId = 1
      val result = analyzer.handleRefactorRequest(
        new RefactorReq(
          procId, OrganiseImportsRefactorDesc(new File(file.path)), false
        )
      )

      val diffContent = extractDiffFromResponse(result, analyzer.charset)

      val relevantExpectedPart = s"""|@@ -1,3 +1,2 @@
                                     |-import java.lang.Integer.{valueOf => vo}
                                     |-import java.lang.Integer.toBinaryString
                                     |+import java.lang.Integer.{valueOf => vo, toBinaryString}
                                     | import java.lang.String.valueOf
                                     |""".stripMargin
      val expectedContents = expectedDiffContent(file.path, relevantExpectedPart)

      diffContent should ===(expectedContents)
    }
  }

  it should "organize and group imports" in {
    withAnalyzer { (dir, analyzerRef) =>

      val file = srcFile(dir, "tmp-contents", contents(
        "import scala._",
        "import java.lang.Integer",
        "import scala.Int",
        "import java._",
        " ",
        "trait Temp {",
        "  def i(): Int",
        "  def j(): Integer",
        "}",
        ""
      ), write = true, encoding = encoding)

      val analyzer = analyzerRef.underlyingActor

      val procId = 1
      val result = analyzer.handleRefactorRequest(
        new RefactorReq(
          procId, OrganiseImportsRefactorDesc(new File(file.path)), false
        )
      )

      val diffContent = extractDiffFromResponse(result, analyzer.charset)

      val relevantExpectedPart = s"""|@@ -1,5 +1,5 @@
                                     |-import scala._
                                     |-import java.lang.Integer
                                     |-import scala.Int
                                     | import java._
                                     |+import java.lang.Integer
                                     |+
                                     |+import scala._
                                     |  \n""".stripMargin
      val expectedContents = expectedDiffContent(file.path, relevantExpectedPart)

      diffContent should ===(expectedContents)
    }
  }
}

trait RefactoringHandlerTestUtils extends Assertions {

  import org.ensime.util.file._

  def expectedDiffContent(filepath: String, expectedContent: String) = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss Z")
    val t = sdf.format(new Date((new File(filepath)).lastModified()))
    val filepathPart = s"""|--- ${filepath}	${t}
                           |+++ ${filepath}	${t}
                           |""".stripMargin

    filepathPart + expectedContent
  }

  def extractDiffFromResponse(response: RpcResponse, charset: Charset) = response match {
    case RefactorDiffEffect(_, _, f) =>
      val diffFile = f.canon
      val diffContent = diffFile.readString()(charset)

      diffFile.delete()
      diffContent

    case default =>
      fail("Not expected type of RpcResponse.")
  }
}
