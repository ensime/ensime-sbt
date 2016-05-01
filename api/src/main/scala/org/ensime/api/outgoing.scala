// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.api

import java.io.File

/**
 * There should be exactly one `RpcResponseEnvelope` in response to an
 * `RpcRequestEnvelope`. If the `callId` is empty, the response is
 * an asynchronous event.
 */
final case class RpcResponseEnvelope(
  callId: Option[Int],
  payload: EnsimeServerMessage
)

sealed trait EnsimeServerMessage

/**
 * A message that the server can send to the client at any time.
 */
sealed trait EnsimeEvent extends EnsimeServerMessage

//////////////////////////////////////////////////////////////////////
// Contents of the payload

sealed trait RpcResponse extends EnsimeServerMessage
final case class EnsimeServerError(description: String) extends RpcResponse

case object DebuggerShutdownEvent

sealed trait DebugVmStatus extends RpcResponse

// must have redundant status: String to match legacy API
final case class DebugVmSuccess(
  status: String = "success"
) extends DebugVmStatus
final case class DebugVmError(
  errorCode: Int,
  details: String,
  status: String = "error"
) extends DebugVmStatus

sealed trait GeneralSwankEvent extends EnsimeEvent
sealed trait DebugEvent extends EnsimeEvent

/**
 * Generic background notification.
 *
 * NOTE: codes will be deprecated, preferring sealed families.
 */
final case class SendBackgroundMessageEvent(
  detail: String,
  code: Int = 105
) extends GeneralSwankEvent

/** The presentation compiler is ready to accept requests. */
case object AnalyzerReadyEvent extends GeneralSwankEvent

/** The presentation compiler has finished analysing the entire project. */
case object FullTypeCheckCompleteEvent extends GeneralSwankEvent

/** The search engine has finished indexing the classpath. */
case object IndexerReadyEvent extends GeneralSwankEvent

/** The presentation compiler was restarted. Existing `:type-id`s are invalid. */
case object CompilerRestartedEvent extends GeneralSwankEvent

/** The presentation compiler has invalidated all existing notes.  */
case object ClearAllScalaNotesEvent extends GeneralSwankEvent

/** The presentation compiler has invalidated all existing notes.  */
case object ClearAllJavaNotesEvent extends GeneralSwankEvent

final case class Note(
  file: String,
  msg: String,
  severity: NoteSeverity,
  beg: Int,
  end: Int,
  line: Int,
  col: Int
) extends RpcResponse

/** The presentation compiler is providing notes: e.g. errors, warnings. */
final case class NewScalaNotesEvent(
  isFull: Boolean,
  notes: List[Note]
) extends GeneralSwankEvent

/** The presentation compiler is providing notes: e.g. errors, warnings. */
final case class NewJavaNotesEvent(
  isFull: Boolean,
  notes: List[Note]
) extends GeneralSwankEvent

/** The debugged VM has stepped to a new location and is now paused awaiting control. */
final case class DebugStepEvent(
  threadId: DebugThreadId,
  threadName: String,
  file: File,
  line: Int
) extends DebugEvent

/** The debugged VM has stopped at a breakpoint. */
final case class DebugBreakEvent(
  threadId: DebugThreadId,
  threadName: String,
  file: File,
  line: Int
) extends DebugEvent

/** The debugged VM has started. */
case object DebugVMStartEvent extends DebugEvent

/** The debugger has disconnected from the debugged VM. */
case object DebugVMDisconnectEvent extends DebugEvent

/** The debugged VM has thrown an exception and is now paused waiting for control. */
final case class DebugExceptionEvent(
  exception: Long,
  threadId: DebugThreadId,
  threadName: String,
  file: Option[File],
  line: Option[Int]
) extends DebugEvent

/** A new thread has started. */
final case class DebugThreadStartEvent(threadId: DebugThreadId) extends DebugEvent

/** A thread has died. */
final case class DebugThreadDeathEvent(threadId: DebugThreadId) extends DebugEvent

/** Communicates stdout/stderr of debugged VM to client. */
final case class DebugOutputEvent(body: String) extends DebugEvent

case object ReloadExistingFilesEvent
case object AskReTypecheck

case object VoidResponse extends RpcResponse

final case class RefactorFailure(
  procedureId: Int,
  reason: String,
  status: scala.Symbol = 'failure // redundant field
) extends RpcResponse

trait RefactorProcedure {
  def procedureId: Int
  def refactorType: RefactorType
}

final case class RefactorDiffEffect(
  procedureId: Int,
  refactorType: RefactorType,
  diff: File
) extends RpcResponse with RefactorProcedure

sealed abstract class RefactorDesc(val refactorType: RefactorType)

final case class InlineLocalRefactorDesc(file: File, start: Int, end: Int) extends RefactorDesc(RefactorType.InlineLocal)

final case class RenameRefactorDesc(newName: String, file: File, start: Int, end: Int) extends RefactorDesc(RefactorType.Rename)

final case class ExtractMethodRefactorDesc(methodName: String, file: File, start: Int, end: Int)
  extends RefactorDesc(RefactorType.ExtractMethod)

final case class ExtractLocalRefactorDesc(name: String, file: File, start: Int, end: Int)
  extends RefactorDesc(RefactorType.ExtractLocal)

final case class OrganiseImportsRefactorDesc(file: File) extends RefactorDesc(RefactorType.OrganizeImports)

final case class AddImportRefactorDesc(qualifiedName: String, file: File)
  extends RefactorDesc(RefactorType.AddImport)

sealed trait PatchOp {
  def start: Int
}

final case class PatchInsert(
  start: Int,
  text: String
) extends PatchOp

final case class PatchDelete(
  start: Int,
  end: Int
) extends PatchOp

final case class PatchReplace(
  start: Int,
  end: Int,
  text: String
) extends PatchOp

sealed trait EntityInfo extends RpcResponse {
  def name: String
  def members: Iterable[EntityInfo]
}

object SourceSymbol {
  val allSymbols: List[SourceSymbol] = List(
    ObjectSymbol, ClassSymbol, TraitSymbol, PackageSymbol, ConstructorSymbol, ImportedNameSymbol, TypeParamSymbol,
    ParamSymbol, VarFieldSymbol, ValFieldSymbol, OperatorFieldSymbol, VarSymbol, ValSymbol, FunctionCallSymbol,
    ImplicitConversionSymbol, ImplicitParamsSymbol, DeprecatedSymbol
  )
}

sealed trait SourceSymbol

case object ObjectSymbol extends SourceSymbol
case object ClassSymbol extends SourceSymbol
case object TraitSymbol extends SourceSymbol
case object PackageSymbol extends SourceSymbol
case object ConstructorSymbol extends SourceSymbol
case object ImportedNameSymbol extends SourceSymbol
case object TypeParamSymbol extends SourceSymbol
case object ParamSymbol extends SourceSymbol
case object VarFieldSymbol extends SourceSymbol
case object ValFieldSymbol extends SourceSymbol
case object OperatorFieldSymbol extends SourceSymbol
case object VarSymbol extends SourceSymbol
case object ValSymbol extends SourceSymbol
case object FunctionCallSymbol extends SourceSymbol
case object ImplicitConversionSymbol extends SourceSymbol
case object ImplicitParamsSymbol extends SourceSymbol
case object DeprecatedSymbol extends SourceSymbol

sealed trait PosNeeded
case object PosNeededNo extends PosNeeded
case object PosNeededAvail extends PosNeeded
case object PosNeededYes extends PosNeeded

sealed trait SourcePosition extends RpcResponse
final case class EmptySourcePosition() extends SourcePosition
final case class OffsetSourcePosition(file: File, offset: Int) extends SourcePosition
final case class LineSourcePosition(file: File, line: Int) extends SourcePosition

final case class PackageInfo(
    name: String,
    fullName: String,
    // n.b. members should be sorted by name for consistency
    members: Seq[EntityInfo]
) extends EntityInfo {
  require(members == members.sortBy(_.name), "members should be sorted by name")
}

sealed trait SymbolSearchResult extends RpcResponse {
  def name: String
  def localName: String
  def declAs: DeclaredAs
  def pos: Option[SourcePosition]
}

final case class TypeSearchResult(
  name: String,
  localName: String,
  declAs: DeclaredAs,
  pos: Option[SourcePosition]
) extends SymbolSearchResult

final case class MethodSearchResult(
  name: String,
  localName: String,
  declAs: DeclaredAs,
  pos: Option[SourcePosition],
  ownerName: String
) extends SymbolSearchResult

// what is the point of these types?
final case class ImportSuggestions(symLists: List[List[SymbolSearchResult]]) extends RpcResponse
final case class SymbolSearchResults(syms: List[SymbolSearchResult]) extends RpcResponse

final case class SymbolDesignations(
  file: File,
  syms: List[SymbolDesignation]
) extends RpcResponse

final case class SymbolDesignation(
  start: Int,
  end: Int,
  symType: SourceSymbol
)

final case class SymbolInfo(
    name: String,
    localName: String,
    declPos: Option[SourcePosition],
    `type`: TypeInfo,
    isCallable: Boolean
) extends RpcResponse {
  def tpe = `type`
}

final case class Op(
  op: String,
  description: String
)

final case class MethodBytecode(
  className: String,
  methodName: String,
  methodSignature: Option[String],
  byteCode: List[Op],
  startLine: Int,
  endLine: Int
)

final case class CompletionSignature(
  sections: List[List[(String, String)]],
  result: String,
  hasImplicit: Boolean
)

final case class CompletionInfo(
  name: String,
  typeSig: CompletionSignature,
  isCallable: Boolean,
  relevance: Int,
  toInsert: Option[String]
) extends RpcResponse

final case class CompletionInfoList(
  prefix: String,
  completions: List[CompletionInfo]
) extends RpcResponse

final case class Breakpoint(file: File, line: Int) extends RpcResponse
final case class BreakpointList(active: List[Breakpoint], pending: List[Breakpoint]) extends RpcResponse

final case class OffsetRange(from: Int, to: Int)

object OffsetRange extends ((Int, Int) => OffsetRange) {
  def apply(fromTo: Int): OffsetRange = new OffsetRange(fromTo, fromTo)
}

/**
 * A debugger thread id.
 */
final case class DebugThreadId(id: Long)

object DebugThreadId {
  /**
   * Create a ThreadId from a String representation
   * @param s A Long encoded as a string
   * @return A ThreadId
   */
  def apply(s: String): DebugThreadId = {
    new DebugThreadId(s.toLong)
  }
}

final case class DebugObjectId(id: Long)

object DebugObjectId {
  /**
   * Create a DebugObjectId from a String representation
   * @param s A Long encoded as a string
   * @return A DebugObjectId
   */
  def apply(s: String): DebugObjectId = {
    new DebugObjectId(s.toLong)
  }
}

sealed trait DebugLocation extends RpcResponse

final case class DebugObjectReference(objectId: DebugObjectId) extends DebugLocation

object DebugObjectReference {
  def apply(objId: Long): DebugObjectReference = new DebugObjectReference(DebugObjectId(objId))
}

final case class DebugStackSlot(threadId: DebugThreadId, frame: Int, offset: Int) extends DebugLocation

final case class DebugArrayElement(objectId: DebugObjectId, index: Int) extends DebugLocation

final case class DebugObjectField(objectId: DebugObjectId, field: String) extends DebugLocation

sealed trait DebugValue extends RpcResponse {
  def typeName: String
}

final case class DebugNullValue(
  typeName: String
) extends DebugValue

final case class DebugPrimitiveValue(
  summary: String,
  typeName: String
) extends DebugValue

final case class DebugObjectInstance(
  summary: String,
  fields: List[DebugClassField],
  typeName: String,
  objectId: DebugObjectId
) extends DebugValue

final case class DebugStringInstance(
  summary: String,
  fields: List[DebugClassField],
  typeName: String,
  objectId: DebugObjectId
) extends DebugValue

final case class DebugArrayInstance(
  length: Int,
  typeName: String,
  elementTypeName: String,
  objectId: DebugObjectId
) extends DebugValue

final case class DebugClassField(
  index: Int,
  name: String,
  typeName: String,
  summary: String
) extends RpcResponse

final case class DebugStackLocal(
  index: Int,
  name: String,
  summary: String,
  typeName: String
) extends RpcResponse

final case class DebugStackFrame(
  index: Int,
  locals: List[DebugStackLocal],
  numArgs: Int,
  className: String,
  methodName: String,
  pcLocation: LineSourcePosition,
  thisObjectId: DebugObjectId
) extends RpcResponse

final case class DebugBacktrace(
  frames: List[DebugStackFrame],
  threadId: DebugThreadId,
  threadName: String
) extends RpcResponse

final case class NamedTypeMemberInfo(
    name: String,
    `type`: TypeInfo,
    pos: Option[SourcePosition],
    signatureString: Option[String],
    declAs: DeclaredAs
) extends EntityInfo {
  override def members = List.empty
  def tpe = `type`
}

sealed trait TypeInfo extends EntityInfo {
  def name: String
  def declAs: DeclaredAs
  def fullName: String
  def typeArgs: Iterable[TypeInfo]
  def members: Iterable[EntityInfo]
  def pos: Option[SourcePosition]

  final def declaredAs = declAs
  final def args = typeArgs
}

final case class BasicTypeInfo(
  name: String,
  declAs: DeclaredAs,
  fullName: String,
  typeArgs: Iterable[TypeInfo],
  members: Iterable[EntityInfo],
  pos: Option[SourcePosition]
) extends TypeInfo

final case class ArrowTypeInfo(
    name: String,
    resultType: TypeInfo,
    paramSections: Iterable[ParamSectionInfo]
) extends TypeInfo {
  def declAs = DeclaredAs.Nil
  def fullName = name
  def typeArgs = List.empty
  def members = List.empty
  def pos = None
}

final case class ParamSectionInfo(
  params: Iterable[(String, TypeInfo)],
  isImplicit: Boolean
)

final case class InterfaceInfo(
    `type`: TypeInfo,
    viaView: Option[String]
) extends RpcResponse {
  def tpe = `type`
}

final case class TypeInspectInfo(
    `type`: TypeInfo,
    interfaces: Iterable[InterfaceInfo],
    infoType: scala.Symbol = 'typeInspect // redundant field in protocol
) extends RpcResponse {
  def supers = interfaces
}

/** ERangePosition is a mirror of scala compiler internal RangePosition as a case class to */
final case class ERangePosition(file: String, offset: Int, start: Int, end: Int)
final case class ERangePositions(positions: List[ERangePosition]) extends RpcResponse

final case class FileRange(file: String, start: Int, end: Int) extends RpcResponse

final case class EnsimeImplementation(
  name: String
)
final case class ConnectionInfo(
  pid: Option[Int] = None,
  implementation: EnsimeImplementation = EnsimeImplementation("ENSIME"),
  version: String = "0.8.20"
) extends RpcResponse

sealed trait ImplicitInfo

final case class ImplicitConversionInfo(
  start: Int,
  end: Int,
  fun: SymbolInfo
) extends ImplicitInfo

final case class ImplicitParamInfo(
  start: Int,
  end: Int,
  fun: SymbolInfo,
  params: List[SymbolInfo],
  funIsImplicit: Boolean
) extends ImplicitInfo

final case class ImplicitInfos(infos: List[ImplicitInfo]) extends RpcResponse

sealed trait LegacyRawResponse extends RpcResponse
case object FalseResponse extends LegacyRawResponse
case object TrueResponse extends LegacyRawResponse
final case class StringResponse(text: String) extends LegacyRawResponse

final case class StructureView(view: List[StructureViewMember]) extends RpcResponse

final case class StructureViewMember(
  keyword: String,
  name: String,
  position: SourcePosition,
  members: List[StructureViewMember]
)

/**
 * @param ast raw representation of the tree at point
 */
final case class AstInfo(ast: String) extends RpcResponse
