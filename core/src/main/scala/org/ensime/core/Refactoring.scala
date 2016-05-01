// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.core

import java.io.File
import java.nio.charset.Charset
import org.ensime.api._
import org.ensime.util.FileUtils._
import org.ensime.util._
import scala.tools.nsc.io.AbstractFile
import scala.tools.refactoring._
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.{ Change, CompilerAccess }
import scala.tools.refactoring.implementations._
import scala.util.{ Success, Try }
import scalariform.astselect.AstSelector
import scalariform.formatter.ScalaFormatter
import scalariform.utils.Range

abstract class RefactoringEnvironment(file: String, start: Int, end: Int) {

  val refactoring: MultiStageRefactoring with CompilerAccess

  def performRefactoring(
    procId: Int,
    tpe: RefactorType,
    parameters: refactoring.RefactoringParameters
  )(implicit charset: Charset): Either[RefactorFailure, RefactorDiffEffect] = {

    def transformToDiff(modifications: List[Change]): Either[RefactorFailure, RefactorDiffEffect] = {
      val edits = modifications.map(FileEditHelper.fromChange).sorted

      writeDiffChanges(edits) match {
        case Right(diff) => Right(new RefactorDiffEffect(procId, tpe, diff))
        case Left(err) => Left(RefactorFailure(procId, err.toString))
      }
    }

    val af = AbstractFile.getFile(file)

    refactoring.compilationUnitOfFile(af) match {
      case Some(cu) =>
        val selection = new refactoring.FileSelection(af, cu.body, start, end)
        refactoring.prepare(selection) match {
          case Right(prepare) =>
            refactoring.perform(selection, prepare, parameters) match {
              case Right(modifications) => transformToDiff(modifications)
              case Left(error) => Left(RefactorFailure(procId, error.cause))
            }
          case Left(error) => Left(RefactorFailure(procId, error.cause))
        }
      case None =>
        Left(RefactorFailure(procId, "Compilation unit not found: " + af))
    }

  }
}

trait RefactoringHandler { self: Analyzer =>

  implicit def cs: Charset = charset

  def handleRefactorRequest(req: RefactorReq): RpcResponse =
    scalaCompiler.askDoRefactor(req.procId, req.params) match {
      case Right(success) => success
      case Left(failure) => failure
    }

  def handleExpandselection(file: File, start: Int, stop: Int): FileRange = {
    readFile(file) match {
      case Right(contents) =>
        val selectionRange = Range(start, stop - start)
        AstSelector.expandSelection(contents, selectionRange) match {
          case Some(range) => FileRange(file.getPath, range.offset, range.offset + range.length)
          case _ =>
            FileRange(file.getPath, start, stop)
        }
      case Left(e) => throw e
    }
  }

  def handleFormatFiles(files: List[File]): Unit = {
    val changeList = files.map { f =>
      readFile(f) match {
        case Right(contents) =>
          Try(ScalaFormatter.format(contents, config.formattingPrefs)).map((f, contents, _))
        case Left(e) => throw e
      }
    }.collect {
      case Success((f, contents, formatted)) =>
        TextEdit(f, 0, contents.length, formatted)
    }
    writeChanges(changeList)
  }

  def handleFormatFile(fileInfo: SourceFileInfo): String = {
    val sourceFile = createSourceFile(fileInfo)
    val contents = sourceFile.content.mkString
    ScalaFormatter.format(contents, config.formattingPrefs)
  }

}

trait RefactoringControl { self: RichCompilerControl with RefactoringImpl =>

  def askDoRefactor(
    procId: Int,
    refactor: RefactorDesc
  ): Either[RefactorFailure, RefactorDiffEffect] = {
    askOption(doRefactor(procId, refactor)).getOrElse(Left(RefactorFailure(procId, "Refactor call failed")))
  }

}

trait RefactoringImpl { self: RichPresentationCompiler =>

  import org.ensime.util.FileUtils._

  implicit def cs: Charset = charset

  protected def doRename(procId: Int, tpe: RefactorType, name: String, file: File, start: Int, end: Int) =
    new RefactoringEnvironment(file.getPath, start, end) {
      val refactoring = new Rename with GlobalIndexes {
        val global = RefactoringImpl.this
        val invalidSet = toBeRemoved.synchronized { toBeRemoved.toSet }
        val cuIndexes = this.global.activeUnits().map { u => CompilationUnitIndex(u.body) }
        val index = GlobalIndex(cuIndexes.toList)
      }
      val result = performRefactoring(procId, tpe, name)
    }.result

  protected def doExtractMethod(procId: Int, tpe: RefactorType,
    name: String, file: File, start: Int, end: Int) =
    new RefactoringEnvironment(file.getPath, start, end) {
      val refactoring = new ExtractMethod with GlobalIndexes {
        val global = RefactoringImpl.this
        val cuIndexes = this.global.activeUnits().map { u => CompilationUnitIndex(u.body) }
        val index = GlobalIndex(cuIndexes.toList)
      }
      val result = performRefactoring(procId, tpe, name)
    }.result

  protected def doExtractLocal(procId: Int, tpe: RefactorType,
    name: String, file: File, start: Int, end: Int) =
    new RefactoringEnvironment(file.getPath, start, end) {
      val refactoring = new ExtractLocal with GlobalIndexes {
        val global = RefactoringImpl.this
        val cuIndexes = this.global.activeUnits().map { u => CompilationUnitIndex(u.body) }
        val index = GlobalIndex(cuIndexes.toList)
      }
      val result = performRefactoring(procId, tpe, name)
    }.result

  protected def doInlineLocal(procId: Int, tpe: RefactorType, file: File,
    start: Int, end: Int) =
    new RefactoringEnvironment(file.getPath, start, end) {
      val refactoring = new InlineLocal with GlobalIndexes {
        val global = RefactoringImpl.this
        val cuIndexes = this.global.activeUnits().map { u => CompilationUnitIndex(u.body) }
        val index = GlobalIndex(cuIndexes.toList)
      }
      val result = performRefactoring(procId, tpe, new refactoring.RefactoringParameters())
    }.result

  protected def doOrganizeImports(procId: Int, tpe: RefactorType, file: File) =
    new RefactoringEnvironment(file.getPath, 0, 0) {
      val refactoring = new OrganizeImports {
        val global = RefactoringImpl.this
      }

      val result = performRefactoring(procId, tpe, new refactoring.RefactoringParameters(
        options = List(
          refactoring.SortImports,
          refactoring.SortImportSelectors,
          refactoring.CollapseImports,
          refactoring.SimplifyWildcards,
          refactoring.RemoveDuplicates,
          refactoring.GroupImports(List("java", "scala"))
        )
      ))
    }.result

  protected def doAddImport(procId: Int, tpe: RefactorType, qualName: String, file: File) = {
    val refactoring = new AddImportStatement {
      val global = RefactoringImpl.this
    }

    val af = AbstractFile.getFile(file.getPath)
    val modifications = refactoring.addImport(af, qualName)
    val edits = modifications.map(FileEditHelper.fromChange)

    writeDiffChanges(edits)(charset) match {
      case Right(diff) => Right(new RefactorDiffEffect(procId, tpe, diff))
      case Left(err) => Left(RefactorFailure(procId, err.toString))
    }
  }

  protected def reloadAndType(f: File) = reloadAndTypeFiles(List(this.createSourceFile(f.getPath)))

  protected def doRefactor(procId: Int, refactor: RefactorDesc): Either[RefactorFailure, RefactorDiffEffect] = {

    val tpe = refactor.refactorType

    try {
      refactor match {
        case InlineLocalRefactorDesc(file, start, end) =>
          reloadAndType(file)
          doInlineLocal(procId, tpe, file, start, end)
        case RenameRefactorDesc(newName, file, start, end) =>
          reloadAndType(file)
          doRename(procId, tpe, newName, file, start, end)
        case ExtractMethodRefactorDesc(methodName, file, start, end) =>
          reloadAndType(file)
          doExtractMethod(procId, tpe, methodName, file, start, end)
        case ExtractLocalRefactorDesc(name, file, start, end) =>
          reloadAndType(file)
          doExtractLocal(procId, tpe, name, file, start, end)
        case OrganiseImportsRefactorDesc(file) =>
          reloadAndType(file)
          doOrganizeImports(procId, tpe, file)
        case AddImportRefactorDesc(qualifiedName, file) =>
          reloadAndType(file)
          doAddImport(procId, tpe, qualifiedName, file)
      }
    } catch {
      case e: Throwable =>
        logger.error("Error during refactor request: " + refactor, e)
        Left(RefactorFailure(procId, e.toString))
    }
  }

}
