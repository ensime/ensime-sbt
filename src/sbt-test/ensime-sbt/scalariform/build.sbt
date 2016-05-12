ivyLoggingLevel := UpdateLogging.Quiet

import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := FormattingPreferences
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 20)

scalaVersion := "2.11.7"
scalacOptions in Compile := Seq("-Xlog-reflective-calls")
