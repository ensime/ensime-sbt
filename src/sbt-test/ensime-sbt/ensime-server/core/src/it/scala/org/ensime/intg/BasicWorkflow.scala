// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.intg

import org.ensime.api._
import org.ensime.core._
import org.ensime.fixture._
import org.ensime.util.EnsimeSpec
import org.ensime.util.file._

class BasicWorkflow extends EnsimeSpec
    with IsolatedEnsimeConfigFixture
    with IsolatedTestKitFixture
    with IsolatedProjectFixture
    with RefactoringHandlerTestUtils {

  val original = EnsimeConfigFixture.SimpleTestProject

  "ensime-server" should "open the simple test project" in {
    withEnsimeConfig { implicit config =>
      withTestKit { implicit testkit =>
        withProject { (project, asyncHelper) =>
          import testkit._

          val sourceRoot = scalaMain(config)
          val fooFile = sourceRoot / "org/example/Foo.scala"
          val fooFilePath = fooFile.getAbsolutePath
          val barFile = sourceRoot / "org/example/Bar.scala"

          // typeCheck module

          project ! TypecheckModule("testingSimple")
          expectMsg(VoidResponse)
          asyncHelper.expectMsgType[NewScalaNotesEvent]
          asyncHelper.expectMsgType[FullTypeCheckCompleteEvent.type]

          project ! TypeByNameReq("org.example.Bloo")
          expectMsgType[BasicTypeInfo]

          project ! UnloadModuleReq("testingSimple")
          expectMsg(VoidResponse)

          project ! TypeByNameReq("org.example.Bloo")
          expectMsg(FalseResponse)

          // trigger typeCheck
          project ! TypecheckFilesReq(List(Left(fooFile)))
          expectMsg(VoidResponse)

          asyncHelper.expectMsg(FullTypeCheckCompleteEvent)

          // Asking to typecheck mising file should report an error not kill system

          val missingFile = sourceRoot / "missing.scala"
          val missingFilePath = missingFile.getAbsolutePath
          project ! TypecheckFilesReq(List(Left(missingFile)))
          expectMsg(EnsimeServerError(s"""file(s): "$missingFilePath" do not exist"""))

          //-----------------------------------------------------------------------------------------------
          // semantic highlighting
          project ! SymbolDesignationsReq(Left(fooFile), -1, 299, SourceSymbol.allSymbols)
          val designations = expectMsgType[SymbolDesignations]
          designations.file shouldBe fooFile
          designations.syms should contain(SymbolDesignation(12, 19, PackageSymbol))
          // expected Symbols
          // ((package 12 19) (package 8 11) (trait 40 43) (valField 69 70) (class 100 103) (param 125 126) (class 128 131) (param 133 134) (class 136 142) (operator 156 157) (param 154 155) (functionCall 160 166) (param 158 159) (valField 183 186) (class 193 199) (class 201 204) (valField 214 217) (class 224 227) (functionCall 232 239) (operator 250 251) (valField 256 257) (valField 252 255) (functionCall 261 268) (functionCall 273 283) (valField 269 272)))

          //-----------------------------------------------------------------------------------------------
          // symbolAtPoint
          project ! SymbolAtPointReq(Left(fooFile), 128)
          val symbolAtPointOpt: SymbolInfo = expectMsgType[SymbolInfo]

          project ! TypeByNameReq("org.example.Foo")
          val fooClassByNameOpt = expectMsgType[TypeInfo]

          project ! TypeByNameReq("org.example.Foo$")
          val fooObjectByNameOpt = expectMsgType[TypeInfo]

          //-----------------------------------------------------------------------------------------------
          // public symbol search - java.io.File

          project ! PublicSymbolSearchReq(List("java", "io", "File"), 30)
          val javaSearchSymbol = expectMsgType[SymbolSearchResults]
          assert(javaSearchSymbol.syms.exists {
            case TypeSearchResult("java.io.File", "File", DeclaredAs.Class, Some(_)) => true
            case _ => false
          })
          //-----------------------------------------------------------------------------------------------
          // public symbol search - scala.util.Random
          project ! PublicSymbolSearchReq(List("scala", "util", "Random"), 2)
          expectMsgPF() {
            case SymbolSearchResults(List(
              TypeSearchResult("scala.util.Random", "Random", DeclaredAs.Class, Some(_)),
              TypeSearchResult("scala.util.Random$", "Random$", DeclaredAs.Class, Some(_)))) =>
            case SymbolSearchResults(List(
              TypeSearchResult("java.util.Random", "Random", DeclaredAs.Class, Some(_)),
              TypeSearchResult("scala.util.Random", "Random", DeclaredAs.Class, Some(_)))) =>
            // this is a pretty ropey test at the best of times
          }

          //-----------------------------------------------------------------------------------------------
          // documentation for type at point
          val intDocSig = DocSigPair(DocSig(DocFqn("scala", "Int"), None), DocSig(DocFqn("", "int"), None))

          // NOTE these are handled as multi-phase queries in requesthandler
          project ! DocUriAtPointReq(Left(fooFile), OffsetRange(128))
          expectMsg(Some(intDocSig))

          project ! DocUriForSymbolReq("scala.Int", None, None)
          expectMsg(Some(intDocSig))

          project ! intDocSig
          expectMsgType[StringResponse].text should endWith("/index.html#scala.Int")

          //-----------------------------------------------------------------------------------------------
          // uses of symbol at point

          log.info("------------------------------------222-")

          project ! TypecheckFilesReq(List(Left(fooFile)))
          expectMsg(VoidResponse)

          asyncHelper.expectMsg(FullTypeCheckCompleteEvent)

          project ! UsesOfSymbolAtPointReq(Left(fooFile), 119) // point on testMethod
          expectMsgType[ERangePositions].positions should contain theSameElementsAs List(
            ERangePosition(`fooFilePath`, 114, 110, 172), ERangePosition(`fooFilePath`, 273, 269, 283)
          )

          log.info("------------------------------------222-")

          // note that the line numbers appear to have been stripped from the
          // scala library classfiles, so offset/line comes out as zero unless
          // loaded by the pres compiler
          project ! SymbolAtPointReq(Left(fooFile), 276)
          expectMsgPF() {
            case SymbolInfo("testMethod", "testMethod", Some(OffsetSourcePosition(`fooFile`, 114)), ArrowTypeInfo("(i: Int, s: String)Int", BasicTypeInfo("Int", DeclaredAs.Class, "scala.Int", List(), List(), None), List(ParamSectionInfo(List((i, BasicTypeInfo("Int", DeclaredAs.Class, "scala.Int", List(), List(), None)), (s, BasicTypeInfo("String", DeclaredAs.Class, "java.lang.String", List(), List(), None))), false))), true) =>
          }

          // M-.  external symbol
          project ! SymbolAtPointReq(Left(fooFile), 190)
          expectMsgPF() {
            case SymbolInfo("Map", "Map", Some(OffsetSourcePosition(_, _)),
              BasicTypeInfo("Map$", DeclaredAs.Object, "scala.collection.immutable.Map$", List(), List(), None),
              false) =>
          }

          project ! SymbolAtPointReq(Left(fooFile), 343)
          expectMsgPF() {
            case SymbolInfo("fn", "fn", Some(OffsetSourcePosition(`fooFile`, 304)),
              BasicTypeInfo("Function1", DeclaredAs.Trait, "scala.Function1",
                List(
                  BasicTypeInfo("String", DeclaredAs.Class, "java.lang.String", List(), List(), None),
                  BasicTypeInfo("Int", DeclaredAs.Class, "scala.Int", List(), List(), None)),
                List(), None),
              false) =>
          }

          project ! SymbolAtPointReq(Left(barFile), 150)
          expectMsgPF() {
            case SymbolInfo("apply", "apply", Some(OffsetSourcePosition(`barFile`, 59)),
              ArrowTypeInfo("(bar: String, baz: Int)org.example.Bar.Foo",
                BasicTypeInfo("Foo", DeclaredAs.Class, "org.example.Bar$$Foo", List(), List(), None),
                List(ParamSectionInfo(
                  List(
                    ("bar", BasicTypeInfo("String", DeclaredAs.Class, "java.lang.String", List(), List(), None)),
                    ("baz", BasicTypeInfo("Int", DeclaredAs.Class, "scala.Int", List(), List(), None))), false))),
              true) =>
          }

          project ! SymbolAtPointReq(Left(barFile), 193)
          expectMsgPF() {
            case SymbolInfo("copy", "copy", Some(OffsetSourcePosition(`barFile`, 59)),
              ArrowTypeInfo("(bar: String, baz: Int)org.example.Bar.Foo",
                BasicTypeInfo("Foo", DeclaredAs.Class, "org.example.Bar$$Foo", List(), List(), None),
                List(ParamSectionInfo(
                  List(
                    ("bar", BasicTypeInfo("String", DeclaredAs.Class, "java.lang.String", List(), List(), None)),
                    ("baz", BasicTypeInfo("Int", DeclaredAs.Class, "scala.Int", List(), List(), None))), false))),
              true) =>
          }

          // C-c C-v p Inspect source of current package
          project ! InspectPackageByPathReq("org.example")
          expectMsgPF() {
            case PackageInfo("example", "org.example", List(
              BasicTypeInfo("Bar", DeclaredAs.Class, "org.example.Bar", List(), List(), Some(_)),
              BasicTypeInfo("Bar$", DeclaredAs.Object, "org.example.Bar$", List(), List(), Some(_)),
              BasicTypeInfo("Bloo", DeclaredAs.Class, "org.example.Bloo", List(), List(), Some(_)),
              BasicTypeInfo("Bloo$", DeclaredAs.Object, "org.example.Bloo$", List(), List(), Some(_)),
              BasicTypeInfo("Blue", DeclaredAs.Class, "org.example.Blue", List(), List(), Some(_)),
              BasicTypeInfo("Blue$", DeclaredAs.Object, "org.example.Blue$", List(), List(), Some(_)),
              BasicTypeInfo("CaseClassWithCamelCaseName", DeclaredAs.Class, "org.example.CaseClassWithCamelCaseName", List(), List(), Some(_)),
              BasicTypeInfo("CaseClassWithCamelCaseName$", DeclaredAs.Object, "org.example.CaseClassWithCamelCaseName$", List(), List(), Some(_)),
              BasicTypeInfo("Foo", DeclaredAs.Class, "org.example.Foo", List(), List(), Some(_)),
              BasicTypeInfo("Foo$", DeclaredAs.Object, "org.example.Foo$", List(), List(), Some(_)),
              BasicTypeInfo("Qux", DeclaredAs.Class, "org.example.Qux", List(), List(), Some(_)),
              BasicTypeInfo("Test1", DeclaredAs.Class, "org.example.Test1", List(), List(), None),
              BasicTypeInfo("Test1$", DeclaredAs.Object, "org.example.Test1$", List(), List(), None),
              BasicTypeInfo("Test2", DeclaredAs.Class, "org.example.Test2", List(), List(), None),
              BasicTypeInfo("Test2$", DeclaredAs.Object, "org.example.Test2$", List(), List(), None),
              BasicTypeInfo("package$", DeclaredAs.Object, "org.example.package$", List(), List(), None))) =>
          }

          // expand selection around 'val foo'
          project ! ExpandSelectionReq(fooFile, 215, 215)
          val expandRange1 = expectMsgType[FileRange]
          expandRange1 shouldBe FileRange(fooFilePath, 214, 217)

          project ! ExpandSelectionReq(fooFile, 214, 217)
          val expandRange2 = expectMsgType[FileRange]
          expandRange2 shouldBe FileRange(fooFilePath, 210, 229)

          project ! RefactorReq(1234, RenameRefactorDesc("bar", fooFile, 215, 215), false)
          expectMsgPF() {
            case RefactorDiffEffect(1234, RefactorType.Rename, diff) =>

              val relevantExpectedPart = s"""|@@ -14,5 +14,5 @@
                                             |   val map = Map[String, Int]()
                                             |-  val foo = new Foo()
                                             |-  println("Hello, " + foo.x)
                                             |-  println(foo.testMethod(7, "seven"))
                                             |+  val bar = new Foo()
                                             |+  println("Hello, " + bar.x)
                                             |+  println(bar.testMethod(7, "seven"))
                                             | \n""".stripMargin

              val diffContents = diff.canon.readString()
              val expectedContents = expectedDiffContent(fooFilePath, relevantExpectedPart)

              if (diffContents == expectedContents) true
              else fail(s"Different diff content than expected. \n Actual content: '$diffContents' \n ExpectedRelevantContent: '$relevantExpectedPart'")
          }

          val oldTree1 = """Apply[1](Select[2](Select[1](Apply[3](TypeApply[4](Select[5](Select[6](This[7](newTypeName("immutable")), scala.collection.immutable.List#MOD%M1), newTermName("apply")#METH%M1), List(TypeTree[1]())), List(Literal[8](Constant(1)), Literal[9](Constant(2)), Literal[10](Constant(3)))), newTermName("head")#METH%M1), newTermName("$plus")#METH%M1), List(Literal[9](Constant(2))))
                           |[1] TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List())
                           |[2] MethodType(List(newTermName("x")#VAL%M1), TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List()))
                           |[3] TypeRef(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.List#CLS%M1, List(TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List())))
                           |[4] MethodType(List(newTermName("xs")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.List#CLS%M1, List(TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List()))))
                           |[5] PolyType(List(newTypeName("A")#TPE%M1), MethodType(List(newTermName("xs")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.List#CLS%M1, List(TypeRef(NoPrefix, newTypeName("A")#TPE%M1, List())))))
                           |[6] SingleType(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.List#MOD%M1)
                           |[7] ThisType(scala.collection.immutable#PK%M1)
                           |[8] ConstantType(Constant(1))
                           |[9] ConstantType(Constant(2))
                           |[10] ConstantType(Constant(3))
                           |[1] compiler mirror""".stripMargin

          val tree1 = """Apply[1](Select[2](Select[1](Apply[3](TypeApply[4](Select[5](Select[6](This[7](TypeName("immutable")), scala.collection.immutable.List#MOD%M1), TermName("apply")#METH%M1), List(TypeTree[1]())), List(Literal[8](Constant(1)), Literal[9](Constant(2)), Literal[10](Constant(3)))), TermName("head")#METH%M1), TermName("$plus")#METH%M1), List(Literal[9](Constant(2))))
                        |[1] TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List())
                        |[2] MethodType(List(TermName("x")#VAL%M1), TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List()))
                        |[3] TypeRef(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.List#CLS%M1, List(TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List())))
                        |[4] MethodType(List(TermName("xs")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.List#CLS%M1, List(TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List()))))
                        |[5] PolyType(List(TypeName("A")#TPE%M1), MethodType(List(TermName("xs")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.List#CLS%M1, List(TypeRef(NoPrefix, TypeName("A")#TPE%M1, List())))))
                        |[6] SingleType(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.List#MOD%M1)
                        |[7] ThisType(scala.collection.immutable#PKC%M1)
                        |[8] ConstantType(Constant(1))
                        |[9] ConstantType(Constant(2))
                        |[10] ConstantType(Constant(3))
                        |[1] compiler mirror""".stripMargin
          project ! AstAtPointReq(SourceFileInfo(fooFile), OffsetRange(475, 496))
          expectMsgPF() {
            case AstInfo(ast) if {
              val stripped = ast.replaceAll("[\n\r]", "")
              stripped == tree1.replaceAll("[\n\r]", "") || stripped == oldTree1.replaceAll("[\n\r]", "")
            } =>
          }

          val oldTree2 = """Apply[11](TypeApply[12](Select[13](Select[14](Select[15](This[16](newTypeName("scala")), scala.Predef#MOD%M1), newTermName("Map")#GET%M1), newTermName("apply")#METH%M1), List(TypeTree[17]().setOriginal(Select[17](Select[15](This[16](newTypeName("scala")), scala.Predef#MOD%M1), newTypeName("String")#TPE%M1)), TypeTree[1]().setOriginal(Select[1](Ident[18](scala#PK%M1), scala.Int#CLS%M1)))), List())
                           |[1] TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List())
                           |[11] TypeRef(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.Map#TRT%M1, List(TypeRef(SingleType(ThisType(scala#PK%M1), scala.Predef#MOD%M1), newTypeName("String")#TPE%M1, List()), TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List())))
                           |[12] MethodType(List(newTermName("elems")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.Map#TRT%M1, List(TypeRef(SingleType(ThisType(scala#PK%M1), scala.Predef#MOD%M1), newTypeName("String")#TPE%M1, List()), TypeRef(ThisType(scala#PK%M1), scala.Int#CLS%M1, List()))))
                           |[13] PolyType(List(newTypeName("A")#TPE%M1, newTypeName("B")#TPE%M1), MethodType(List(newTermName("elems")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PK%M1), scala.collection.immutable.Map#TRT%M1, List(TypeRef(NoPrefix, newTypeName("A")#TPE%M1, List()), TypeRef(NoPrefix, newTypeName("B")#TPE%M1, List())))))
                           |[14] SingleType(SingleType(ThisType(scala#PK%M1), scala.Predef#MOD%M1), newTermName("Map")#GET%M1)
                           |[15] SingleType(ThisType(scala#PK%M1), scala.Predef#MOD%M1)
                           |[16] ThisType(scala#PK%M1)
                           |[17] TypeRef(SingleType(ThisType(scala#PK%M1), scala.Predef#MOD%M1), newTypeName("String")#TPE%M1, List())
                           |[18] SingleType(ThisType(<root>#PK%M1), scala#PK%M1)
                           |[1] compiler mirror""".stripMargin

          val tree2 = """Apply[11](TypeApply[12](Select[13](Select[14](Select[15](This[16](TypeName("scala")), scala.Predef#MOD%M1), TermName("Map")#GET%M1), TermName("apply")#METH%M1), List(TypeTree[17]().setOriginal(Select[17](Select[15](This[16](TypeName("scala")), scala.Predef#MOD%M1), TypeName("String")#TPE%M1)), TypeTree[1]().setOriginal(Select[1](Ident[18](scala#PK%M1), scala.Int#CLS%M1)))), List())
                        |[1] TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List())
                        |[11] TypeRef(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.Map#TRT%M1, List(TypeRef(SingleType(ThisType(scala#PKC%M1), scala.Predef#MOD%M1), TypeName("String")#TPE%M1, List()), TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List())))
                        |[12] MethodType(List(TermName("elems")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.Map#TRT%M1, List(TypeRef(SingleType(ThisType(scala#PKC%M1), scala.Predef#MOD%M1), TypeName("String")#TPE%M1, List()), TypeRef(ThisType(scala#PKC%M1), scala.Int#CLS%M1, List()))))
                        |[13] PolyType(List(TypeName("A")#TPE%M1, TypeName("B")#TPE%M1), MethodType(List(TermName("elems")#VAL%M1), TypeRef(ThisType(scala.collection.immutable#PKC%M1), scala.collection.immutable.Map#TRT%M1, List(TypeRef(NoPrefix, TypeName("A")#TPE%M1, List()), TypeRef(NoPrefix, TypeName("B")#TPE%M1, List())))))
                        |[14] SingleType(SingleType(ThisType(scala#PKC%M1), scala.Predef#MOD%M1), TermName("Map")#GET%M1)
                        |[15] SingleType(ThisType(scala#PKC%M1), scala.Predef#MOD%M1)
                        |[16] ThisType(scala#PKC%M1)
                        |[17] TypeRef(SingleType(ThisType(scala#PKC%M1), scala.Predef#MOD%M1), TypeName("String")#TPE%M1, List())
                        |[18] SingleType(ThisType(<root>#PKC%M1), scala#PK%M1)
                        |[1] compiler mirror""".stripMargin
          project ! AstAtPointReq(SourceFileInfo(fooFile), OffsetRange(189, 206))
          expectMsgPF() {
            case AstInfo(ast) if {
              val stripped = ast.replaceAll("[\n\r]", "")
              stripped == tree2.replaceAll("[\n\r]", "") || stripped == oldTree2.replaceAll("[\n\r]", "")
            } =>
          }
        }
      }
    }
  }
}
