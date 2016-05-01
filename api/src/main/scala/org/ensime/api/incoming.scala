// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// Licence: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.api

import java.io.File

final case class RpcRequestEnvelope(req: RpcRequest, callId: Int)

/**
 * All messages into the ENSIME server from the client are part of
 * this family.
 *
 * NOTE: we intend to simplify these messages
 * https://github.com/ensime/ensime-server/issues/845
 */
sealed trait RpcRequest

// queries related to connection startup
sealed trait RpcStartupRequest extends RpcRequest

/**
 * Responds with a `ConnectionInfo`.
 */
case object ConnectionInfoReq extends RpcStartupRequest

// related to managing the state of the analyser
sealed trait RpcAnalyserRequest extends RpcRequest

/**
 * Request details about implicit conversions applied inside the given
 * range.
 *
 * Responds with `ImplicitInfos`.
 *
 * @param file source.
 * @param range in the file to inspect.
 */
final case class ImplicitInfoReq(
  file: Either[File, SourceFileInfo],
  range: OffsetRange
) extends RpcAnalyserRequest

/**
 * Responds with a `VoidResponse`.
 */
final case class RemoveFileReq(file: File) extends RpcAnalyserRequest

/**
 * Responds with a `VoidResponse`.
 */
final case class TypecheckFileReq(fileInfo: SourceFileInfo) extends RpcAnalyserRequest

/**
 * Response with a `VoidResponse`.
 */
final case class UnloadModuleReq(module: String) extends RpcAnalyserRequest

/**
 * Response with a `VoidResponse`.
 */
final case class TypecheckModule(module: String) extends RpcAnalyserRequest

/**
 * Responds with a `VoidResponse`.
 */
case object UnloadAllReq extends RpcAnalyserRequest

/**
 * Responds with a `VoidResponse`.
 */
case object TypecheckAllReq extends RpcAnalyserRequest

/**
 * Responds with a `VoidResponse`.
 */
final case class TypecheckFilesReq(files: List[Either[File, SourceFileInfo]]) extends RpcAnalyserRequest

/**
 * Responds with `VoidResponse`.
 */
final case class FormatSourceReq(files: List[File]) extends RpcAnalyserRequest

/**
 * Responds with the formatted file as a `StringResponse`.
 */
final case class FormatOneSourceReq(file: SourceFileInfo) extends RpcAnalyserRequest

// related to searching the indexer
sealed trait RpcSearchRequest extends RpcRequest

/**
 * Responds with `SymbolSearchResults`.
 */
final case class PublicSymbolSearchReq(
  keywords: List[String],
  maxResults: Int
) extends RpcSearchRequest

/**
 * Responds with [ImportSuggestions].
 */
final case class ImportSuggestionsReq(
  file: Either[File, SourceFileInfo],
  point: Int,
  names: List[String],
  maxResults: Int
) extends RpcSearchRequest

/**
 * Responds with `ERangePositions`.
 */
final case class UsesOfSymbolAtPointReq(
  file: Either[File, SourceFileInfo],
  point: Int
) extends RpcAnalyserRequest // will probably become a search request

/**
 * Responds with a `StringResponse` for the URL of the documentation if valid,
 * or `FalseResponse`.
 */
final case class DocUriAtPointReq(
  file: Either[File, SourceFileInfo],
  point: OffsetRange
) extends RpcAnalyserRequest

/**
 * Responds with a `StringResponse` for the URL of the documentation if valid,
 * or `FalseResponse`.
 */
final case class DocUriForSymbolReq(
  typeFullName: String,
  memberName: Option[String],
  signatureString: Option[String]
) extends RpcAnalyserRequest

/**
 * Responds with a `CompletionInfoList`.
 */
final case class CompletionsReq(
  fileInfo: SourceFileInfo,
  point: Int,
  maxResults: Int,
  caseSens: Boolean,
  reload: Boolean
) extends RpcAnalyserRequest

/**
 * Responds with a `List[CompletionInfo]`.
 */
final case class PackageMemberCompletionReq(
  path: String,
  prefix: String
) extends RpcAnalyserRequest

/**
 * Responds with `TypeInfo` if valid, or `FalseResponse`.
 */
final case class TypeByNameReq(name: String) extends RpcAnalyserRequest

/**
 * Responds with `TypeInfo` if valid, or `FalseResponse`.
 */
final case class TypeByNameAtPointReq(
  name: String, file: Either[File, SourceFileInfo], range: OffsetRange
) extends RpcAnalyserRequest

/**
 * Responds with `TypeInfo` if valid, or `FalseResponse`.
 */
final case class TypeAtPointReq(
  file: Either[File, SourceFileInfo], range: OffsetRange
) extends RpcAnalyserRequest

/**
 * Request detailed type information about the item at the given file
 * position.
 *
 * Responds with a `TypeInspectInfo` if the range is a valid type or
 * `FalseResponse`.
 *
 * @param file source.
 * @param range in the file to inspect.
 */
final case class InspectTypeAtPointReq(file: Either[File, SourceFileInfo], range: OffsetRange) extends RpcAnalyserRequest

/**
 * Request detailed type description by fully qualified class name.
 *
 * Responds with a `TypeInspectInfo` if the FQN is valid, or
 * `FalseResponse`.
 *
 * @param name fully qualified type name to inspect
 */
final case class InspectTypeByNameReq(name: String) extends RpcAnalyserRequest

/**
 * Responds with a `SymbolInfo` if valid, or `FalseResponse`.
 */
final case class SymbolAtPointReq(file: Either[File, SourceFileInfo], point: Int) extends RpcAnalyserRequest

/**
 * Request detailed symbol description by fully qualified symbol name.
 *
 * Responds with a `SymbolInfo` if valid, or `FalseResponse`.
 *
 * @param typeFullName fully qualified name of a type, object or package.
 * @param memberName short name of a member symbol of the qualified symbol.
 * @param signatureString to disambiguate overloaded methods.
 */
final case class SymbolByNameReq(
  typeFullName: String,
  memberName: Option[String],
  signatureString: Option[String]
) extends RpcAnalyserRequest

/**
 * Responds with `PackageInfo`.
 */
final case class InspectPackageByPathReq(path: String) extends RpcAnalyserRequest

/**
 * Responds with a `RefactorFailure` or a `RefactorDiffEffect`.
 */
final case class RefactorReq(
  procId: Int,
  params: RefactorDesc,
  interactive: Boolean
) extends RpcAnalyserRequest

/**
 * Request the semantic classes of symbols in the given range.
 * Intended for semantic highlighting.
 *
 * Responds with a `SymbolDesignations`.
 *
 * @param file source.
 * @param start of character offset of the input range.
 * @param end of character offset of the input range.
 * @param requestedTypes semantic classes in which we are interested.
 */
final case class SymbolDesignationsReq(
  file: Either[File, SourceFileInfo],
  start: Int,
  end: Int,
  requestedTypes: List[SourceSymbol]
) extends RpcAnalyserRequest

/**
 * Responds with a `FileRange`.
 */
final case class ExpandSelectionReq(file: File, start: Int, end: Int) extends RpcAnalyserRequest

/**
 * Responds with a `StructureView`.
 */
final case class StructureViewReq(fileInfo: SourceFileInfo) extends RpcAnalyserRequest

/**
 * Responds with `ASTInfo`
 *
 * @param file source
 * @param offset in file to inspect
 */
final case class AstAtPointReq(
  file: SourceFileInfo,
  offset: OffsetRange
) extends RpcAnalyserRequest

sealed trait RpcDebuggerRequest extends RpcRequest

/**
 * Query whether we are in an active debug session
 * Responds with a `TrueResponse` or a `FalseResponse`.
 */
case object DebugActiveVmReq extends RpcDebuggerRequest

/**
 * Responds with `DebugVmStatus`.
 */
final case class DebugAttachReq(hostname: String, port: String) extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
case object DebugStopReq extends RpcDebuggerRequest

/**
 * Responds with a `VoidResponse`.
 */
final case class DebugSetBreakReq(file: File, line: Int) extends RpcDebuggerRequest

/**
 * Responds with a `VoidResponse`.
 */
final case class DebugClearBreakReq(file: File, line: Int) extends RpcDebuggerRequest

/**
 * Responds with a `VoidResponse`.
 */
case object DebugClearAllBreaksReq extends RpcDebuggerRequest

/**
 * Responds with a `BreakpointList`.
 */
case object DebugListBreakpointsReq extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
case object DebugRunReq extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
final case class DebugContinueReq(threadId: DebugThreadId) extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
final case class DebugStepReq(threadId: DebugThreadId) extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
final case class DebugNextReq(threadId: DebugThreadId) extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
final case class DebugStepOutReq(threadId: DebugThreadId) extends RpcDebuggerRequest

/**
 * Responds with a `DebugLocation` if successful, or `FalseResponse`.
 */
final case class DebugLocateNameReq(threadId: DebugThreadId, name: String) extends RpcDebuggerRequest

/**
 * Responds with a `DebugValue` if successful, or `FalseResponse`.
 */
final case class DebugValueReq(loc: DebugLocation) extends RpcDebuggerRequest

/**
 * Responds with a `StringResponse` if successful, or `FalseResponse`.
 */
final case class DebugToStringReq(threadId: DebugThreadId, loc: DebugLocation) extends RpcDebuggerRequest

/**
 * Responds with a `FalseResponse` or a `TrueResponse`.
 */
final case class DebugSetValueReq(loc: DebugLocation, newValue: String) extends RpcDebuggerRequest

/**
 * Responds with a `DebugBacktrace`.
 */
final case class DebugBacktraceReq(threadId: DebugThreadId, index: Int, count: Int) extends RpcDebuggerRequest
